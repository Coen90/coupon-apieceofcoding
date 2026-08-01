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
    private val reconcileMetrics: ReconcileMetrics,
    private val reconcileCheckpointStore: ReconcileCheckpointStore,
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

    fun auditAll(): ReconcileReport =
        tryAuditAll() ?: ReconcileReport(0, 0, 0, 0L, 0)

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
                reconcileCoupon(couponId)
            } catch (e: Exception) {
                log.warn(e) { "reconcile 중 예외 coupon=$couponId, 다음 회차에 재시도" }
                CouponOutcome(failed = true)
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

        var listDriftAlert = false
        if (s.listResidual > 0 && s.dbResidual == 0L) {
            val missing = issuanceRepository.findNonCanceledUserIds(couponId) -
                reconcileRedisRepository.userIds(couponId).mapNotNull { it.toLongOrNull() }.toSet()
            if (missing.size.toLong() == s.listResidual) {
                reconcileRedisRepository.addUsers(couponId, missing)
                autoFixed++
                log.info { "auto-fix: 사용자 목록 SADD ${missing.size}건 coupon=$couponId" }
            } else {
                listDriftAlert = true
                log.warn { "Redis 사용자 목록을 안전하게 복구할 수 없음 coupon=$couponId" }
            }
        } else if (s.listResidual < 0) {
            listDriftAlert = true
            log.warn { "Redis 사용자 목록 초과 감지 coupon=$couponId residual=${s.listResidual}" }
        }

        var confirmedDbDrift = 0L
        if (s.dbResidual != 0L) {
            confirmedDbDrift = abs(s.dbResidual)
            log.warn { "DB 측 불일치 감지 coupon=$couponId residual=${s.dbResidual} (자동 보정 불가, 사람 확인)" }
        }

        return CouponOutcome(autoFixed, confirmedDbDrift, stockNegative, listDriftAlert)
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
        val listDriftAlert: Boolean = false,
        val failed: Boolean = false,
    )
}
