package com.apiece.coupon.application

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import com.apiece.coupon.infrastructure.cache.SoldOutProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

private val log = KotlinLogging.logger {}

@Component
class Reconciler(
    private val couponRepository: CouponRepository,
    private val issuanceRepository: IssuanceRepository,
    private val reconcileRedisRepository: CouponReconcileRedisRepository,
    private val soldOutProperties: SoldOutProperties,
    private val reconcileProperties: ReconcileProperties,
    private val metrics: ReconcileMetrics,
) {

    // 스케줄과 /admin/reconcile/run 이 동시에 돌면 게이지가 비결정적으로 덮어써지므로 한 번에 하나만.
    private val running = AtomicBoolean(false)

    // 스케줄 대사가 마지막으로 검사한 상한(cutoff). 다음 회차의 하한이 되어 슬라이스가 이어진다 (sliding window).
    private val lastCutoffMs = AtomicLong(0)

    @Scheduled(fixedRateString = "\${coupon.reconcile.interval-ms}")
    fun scheduled() {
        val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        // 첫 회차는 직전 cutoff 가 없으니 한 칸(interval)만 거슬러 올라가 1분치 슬라이스로 시작한다.
        val fromMs = lastCutoffMs.get().takeIf { it > 0 } ?: (cutoffMs - reconcileProperties.intervalMs)
        if (reconcileWindow(fromMs, cutoffMs) != null) lastCutoffMs.set(cutoffMs)
    }

    // 운영/검증용 수동 트리거: 하한 0 으로 지금까지 정착된 발급을 한 번에 훑는다 (스케줄 커서는 건드리지 않음).
    fun reconcileAll(): ReconcileReport {
        val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        return reconcileWindow(0, cutoffMs) ?: ReconcileReport(0, 0, 0, 0L, 0)
    }

    // 발급 시각이 (fromExclusiveMs, toInclusiveMs] 에 든 쿠폰만 검사한다. 동시 실행 중이면 null.
    private fun reconcileWindow(fromExclusiveMs: Long, toInclusiveMs: Long): ReconcileReport? {
        if (!running.compareAndSet(false, true)) {
            log.info { "reconcile 이 이미 실행 중이라 이번 호출은 건너뜀" }
            return null
        }
        try {
            val couponIds = reconcileRedisRepository.couponIdsIssuedBetween(fromExclusiveMs, toInclusiveMs)
            val outcomes = couponIds.map { couponId ->
                try {
                    reconcileCoupon(couponId)
                } catch (e: Exception) {
                    log.warn(e) { "reconcile 중 예외 coupon=$couponId, skip" }
                    CouponOutcome()
                }
            }

            val report = ReconcileReport(
                checkedCoupons = couponIds.size,
                autoFixed = outcomes.sumOf { it.autoFixed },
                driftAlerts = outcomes.count { it.confirmedDbDrift > 0 },
                redisDbDrift = outcomes.sumOf { it.confirmedDbDrift },
                stockNegative = outcomes.count { it.stockNegative },
            )
            metrics.setRedisDbDrift(report.redisDbDrift)
            metrics.setStockNegative(report.stockNegative.toLong())
            metrics.addAutoFix(report.autoFixed)
            return report
        } finally {
            running.set(false)
        }
    }

    // Redis 측 안전한 불일치를 그 자리에서 보정하고, 무엇을 했는지 반환한다 (메트릭 집계는 reconcileWindow).
    private fun reconcileCoupon(couponId: Long): CouponOutcome {
        val s = readSnapshot(couponId) ?: return CouponOutcome()
        var autoFixed = 0
        val stockNegative = s.stock < 0
        if (stockNegative) log.warn { "재고 음수 감지 coupon=$couponId stock=${s.stock}" }

        if (s.stock > 0 && s.soldOut) {
            reconcileRedisRepository.deleteSoldOut(couponId)
            autoFixed++
            log.info { "auto-fix: 매진 플래그 해제 coupon=$couponId" }
        } else if (s.stock == 0L && !s.soldOut) {
            reconcileRedisRepository.setSoldOut(couponId, soldOutProperties.ttlSeconds)
            autoFixed++
            log.info { "auto-fix: 매진 플래그 설정 coupon=$couponId" }
        }

        // 목록 측만 부족(= Redis 휘발)이면 DB ISSUED 기준 SADD. stock 을 안 건드려 과발급을 못 만들어 안전.
        if (s.listResidual > 0 && s.dbResidual == 0L) {
            val missing = issuanceRepository.findIssuedUserIds(couponId) -
                reconcileRedisRepository.userIds(couponId).mapNotNull { it.toLongOrNull() }.toSet()
            if (missing.isNotEmpty()) {
                reconcileRedisRepository.addUsers(couponId, missing)
                autoFixed++
                log.info { "auto-fix: 사용자 목록 SADD ${missing.size}건 coupon=$couponId" }
            }
        }

        // DB 측 불일치는 자동 보정이 과발급/회수를 부를 수 있어 알람만.
        var confirmedDbDrift = 0L
        if (s.dbResidual != 0L) {
            confirmedDbDrift = abs(s.dbResidual)
            log.warn { "DB 측 불일치 감지 coupon=$couponId residual=${s.dbResidual} (자동 보정 불가, 사람 확인)" }
        }

        return CouponOutcome(autoFixed, confirmedDbDrift, stockNegative)
    }

    private fun readSnapshot(couponId: Long): ReconcileSnapshot? {
        val coupon = couponRepository.findById(couponId).orElse(null) ?: return null
        val stock = reconcileRedisRepository.stock(couponId) ?: return null
        return ReconcileSnapshot(
            total = coupon.totalQuantity,
            issued = coupon.issuedQuantity,
            stock = stock,
            userCount = reconcileRedisRepository.userCount(couponId),
            soldOut = reconcileRedisRepository.soldOutExists(couponId),
        )
    }

    private class CouponOutcome(
        val autoFixed: Int = 0,
        val confirmedDbDrift: Long = 0L,
        val stockNegative: Boolean = false,
    )
}
