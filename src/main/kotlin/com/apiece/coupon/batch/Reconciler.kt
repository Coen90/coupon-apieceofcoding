package com.apiece.coupon.batch

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class Reconciler(
    private val couponRepository: CouponRepository,
    private val reconcileRedisRepository: CouponReconcileRedisRepository,
    private val reconcileProperties: ReconcileProperties,
    private val reconcileMetrics: ReconcileMetrics,
    private val couponReconciler: CouponReconciler,
) {
    @Scheduled(fixedRateString = "\${coupon.reconcile.interval-ms}")
    fun scheduledRecent() {
        val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        val fromMs = cutoffMs - reconcileProperties.intervalMs * 2
        reconcileWindow(fromMs, cutoffMs)
    }

    @Scheduled(cron = "\${coupon.reconcile.audit-cron}")
    fun scheduledAudit() {
        val report = auditAll()
        log.info { "일일 전수 audit 완료 checked=${report.checkedCoupons} alerts=${report.driftAlerts}" }
    }

    fun reconcileAll(): ReconcileReport {
        val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        return reconcileWindow(0, cutoffMs)
    }

    fun auditAll(): ReconcileReport = reconcileCoupons(couponRepository.findAll().mapNotNull { it.id })

    private fun reconcileWindow(
        fromExclusiveMs: Long,
        toInclusiveMs: Long,
    ): ReconcileReport {
        val couponIds = reconcileRedisRepository.couponIdsIssuedBetween(fromExclusiveMs, toInclusiveMs)
        return reconcileCoupons(couponIds)
    }

    private fun reconcileCoupons(couponIds: List<Long>): ReconcileReport {
        val outcomes = couponIds.map { couponId ->
            try {
                couponReconciler.reconcile(couponId)
            } catch (e: Exception) {
                log.warn(e) { "reconcile 중 예외 coupon=$couponId, 다음 회차에 재시도" }
                CouponReconcileOutcome(failed = true)
            }
        }
        val report = ReconcileReport(
            checkedCoupons = couponIds.size,
            autoFixed = outcomes.sumOf { it.autoFixed },
            driftAlerts = outcomes.count { it.confirmedDbDrift > 0 || it.listDriftAlert },
            redisDbDrift = outcomes.sumOf { it.confirmedDbDrift },
            stockNegative = outcomes.count { it.stockNegative },
            failedCoupons = outcomes.count { it.failed },
        )
        reconcileMetrics.setRedisDbDrift(report.redisDbDrift)
        reconcileMetrics.setStockNegative(report.stockNegative.toLong())
        reconcileMetrics.addAutoFix(report.autoFixed)
        return report
    }
}
