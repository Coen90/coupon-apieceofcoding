package com.apiece.coupon.application

import com.apiece.coupon.domain.Coupon
import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import com.apiece.coupon.infrastructure.cache.SoldOutProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
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
    private val nextCouponId = AtomicLong(0)

    @Scheduled(fixedRateString = "\${coupon.reconcile.interval-ms}")
    fun scheduled() {
        reconcileAll()
    }

    fun reconcileAll(): ReconcileReport {
        if (!running.compareAndSet(false, true)) {
            log.info { "reconcile 이 이미 실행 중이라 이번 호출은 건너뜀" }
            return ReconcileReport(0, 0, 0, 0L, 0)
        }
        try {
            val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
            val coupons = nextCouponBatch().filter { coupon ->
                val couponId = coupon.id ?: return@filter false
                reconcileRedisRepository.hasStock(couponId) && !hasRecentIssue(couponId, cutoffMs)
            }
            val outcomes = coupons.map { coupon ->
                try {
                    reconcileCoupon(coupon.id!!)
                } catch (e: Exception) {
                    log.warn(e) { "reconcile 중 예외 coupon=${coupon.id}, skip" }
                    CouponOutcome()
                }
            }

            val report = ReconcileReport(
                checkedCoupons = coupons.size,
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

    private fun nextCouponBatch(): List<Coupon> {
        val page = PageRequest.of(0, maxOf(1, reconcileProperties.batchSize))
        val afterId = nextCouponId.get()
        var coupons = couponRepository.findByIdGreaterThanOrderByIdAsc(afterId, page)
        if (coupons.isEmpty() && afterId > 0) {
            coupons = couponRepository.findByIdGreaterThanOrderByIdAsc(0, page)
        }
        coupons.lastOrNull()?.id?.let(nextCouponId::set)
        return coupons
    }

    private fun hasRecentIssue(couponId: Long, cutoffMs: Long): Boolean {
        val lastIssuedAtMs = reconcileRedisRepository.lastIssuedAtMs(couponId) ?: return false
        return lastIssuedAtMs > cutoffMs
    }

    // Redis 측 안전한 불일치를 그 자리에서 보정하고, 무엇을 했는지 반환한다 (메트릭 집계는 reconcileAll).
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
