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
    private val reconcileCheckpointStore: ReconcileCheckpointStore,
    private val couponReconciler: CouponReconciler,
) {
    @Scheduled(fixedRateString = "\${coupon.reconcile.interval-ms}")
    fun scheduledRecent() {
        if (!reconcileCheckpointStore.acquire()) {
            log.info { "다른 인스턴스가 reconcile lease를 가지고 있어 이번 호출은 건너뜀" }
            return
        }
        val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        try {
            val fromMs = reconcileCheckpointStore.lastSuccessCutoffMs().takeIf { it > 0 }
                ?: (cutoffMs - reconcileProperties.intervalMs)
            val report = reconcileWindow(fromMs, cutoffMs, renewLease = true)
            if (report.failedCoupons == 0) reconcileCheckpointStore.markSuccess(cutoffMs)
        } finally {
            reconcileCheckpointStore.release()
        }
    }

    @Scheduled(cron = "\${coupon.reconcile.audit-cron}")
    fun scheduledAudit() {
        val report = tryAuditAll()
        if (report == null) {
            log.info { "다른 인스턴스가 reconcile lease를 가지고 있어 일일 전수 audit을 건너뜀" }
            return
        }
        log.info { "일일 전수 audit 완료 checked=${report.checkedCoupons} alerts=${report.driftAlerts}" }
    }

    fun reconcileAll(): ReconcileReport {
        val cutoffMs = System.currentTimeMillis() - reconcileProperties.gracePeriodMs
        return reconcileWindow(0, cutoffMs)
    }

    fun auditAll(): ReconcileReport = tryAuditAll() ?: ReconcileReport()

    private fun tryAuditAll(): ReconcileReport? {
        if (!reconcileCheckpointStore.acquire()) return null
        return try {
            val couponIds = couponRepository.findAll().mapNotNull { it.id }
            reconcileCoupons(couponIds, renewLease = true)
        } finally {
            reconcileCheckpointStore.release()
        }
    }

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
                check(reconcileCheckpointStore.renew()) { "reconcile lease lost" }
                nextRenewAt = System.currentTimeMillis() + reconcileProperties.leaseMs / 3
            }
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
