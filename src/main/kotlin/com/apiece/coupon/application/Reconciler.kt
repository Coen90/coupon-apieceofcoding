package com.apiece.coupon.application

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import com.apiece.coupon.infrastructure.cache.SoldOutProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
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
    private val checkpointStore: ReconcileCheckpointStore,
) {
    @Scheduled(fixedRateString = "\${coupon.reconcile.interval-ms}")
    fun scheduled() {
        if (!checkpointStore.acquire()) {
            log.info { "다른 인스턴스가 reconcile lease를 가지고 있어 이번 호출은 건너뜀" }
            return
        }
        val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        try {
            // 첫 회차는 직전 cutoff가 없으니 한 칸(interval)만 거슬러 올라간다.
            val fromMs = checkpointStore.lastSuccessCutoffMs().takeIf { it > 0 }
                ?: (cutoffMs - reconcileProperties.intervalMs)
            val report = reconcileWindow(fromMs, cutoffMs, renewLease = true)
            // 실패한 쿠폰이 있으면 같은 구간을 다음 회차에 다시 본다.
            if (report.failedCoupons == 0) checkpointStore.markSuccess(cutoffMs)
        } finally {
            checkpointStore.release()
        }
    }

    // 운영/검증용 수동 트리거: 하한 0 으로 지금까지 정착된 발급을 한 번에 훑는다 (스케줄 커서는 건드리지 않음).
    fun reconcileAll(): ReconcileReport {
        val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        return reconcileWindow(0, cutoffMs)
    }

    // Redis ZSET을 잃어도 대상을 찾을 수 있도록 DB의 모든 쿠폰을 느리게 점검한다.
    fun auditAll(): ReconcileReport {
        if (!checkpointStore.acquire()) return ReconcileReport(0, 0, 0, 0L, 0)
        return try {
            val couponIds = couponRepository.findAll().mapNotNull { it.id }
            reconcileCoupons(couponIds, renewLease = true)
        } finally {
            checkpointStore.release()
        }
    }

    // 발급 시각이 (fromExclusiveMs, toInclusiveMs] 에 든 쿠폰만 검사한다.
    private fun reconcileWindow(
        fromExclusiveMs: Long,
        toInclusiveMs: Long,
        renewLease: Boolean = false,
    ): ReconcileReport {
        val couponIds = reconcileRedisRepository.couponIdsIssuedBetween(fromExclusiveMs, toInclusiveMs)
        return reconcileCoupons(couponIds, renewLease)
    }

    private fun reconcileCoupons(couponIds: List<Long>, renewLease: Boolean = false): ReconcileReport {
        var nextRenewAt = System.currentTimeMillis() + reconcileProperties.leaseMs / 3
        val outcomes = couponIds.map { couponId ->
            if (renewLease && System.currentTimeMillis() >= nextRenewAt) {
                check(checkpointStore.renew()) { "reconcile lease lost" }
                nextRenewAt = System.currentTimeMillis() + reconcileProperties.leaseMs / 3
            }
            try {
                reconcileCoupon(couponId)
            } catch (e: Exception) {
                log.warn(e) { "reconcile 중 예외 coupon=$couponId, 다음 회차에 재시도" }
                CouponOutcome(failed = true)
            }
        }
        val report = ReconcileReport(
            checkedCoupons = couponIds.size,
            autoFixed = outcomes.sumOf { it.autoFixed },
            driftAlerts = outcomes.count { it.confirmedDbDrift > 0 },
            redisDbDrift = outcomes.sumOf { it.confirmedDbDrift },
            stockNegative = outcomes.count { it.stockNegative },
            failedCoupons = outcomes.count { it.failed },
        )
        metrics.setRedisDbDrift(report.redisDbDrift)
        metrics.setStockNegative(report.stockNegative.toLong())
        metrics.addAutoFix(report.autoFixed)
        return report
    }

    // Redis 측 안전한 불일치를 그 자리에서 보정하고, 무엇을 했는지 반환한다 (메트릭 집계는 reconcileWindow).
    private fun reconcileCoupon(couponId: Long): CouponOutcome {
        val s = readSnapshot(couponId)
        if (s == null) {
            log.warn { "Redis stock 키가 없어 대상을 확인할 수 없음 coupon=$couponId (전수 audit 대상)" }
            return CouponOutcome(confirmedDbDrift = 1L)
        }
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
        val failed: Boolean = false,
    )
}
