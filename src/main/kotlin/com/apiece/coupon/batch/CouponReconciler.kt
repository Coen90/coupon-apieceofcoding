package com.apiece.coupon.batch

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.apiece.coupon.infrastructure.cache.SoldOutProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.math.abs

private val log = KotlinLogging.logger {}

@Component
class CouponReconciler(
    private val couponRepository: CouponRepository,
    private val issuanceRepository: IssuanceRepository,
    private val reconcileRedisRepository: CouponReconcileRedisRepository,
    private val soldOutProperties: SoldOutProperties,
) {
    fun reconcile(couponId: Long): CouponReconcileOutcome {
        val snapshot = readSnapshot(couponId)
        if (snapshot == null) {
            log.warn { "Redis stock 키가 없어 대상을 확인할 수 없음 coupon=$couponId (전수 audit 대상)" }
            return CouponReconcileOutcome(confirmedDbDrift = 1)
        }

        unsafeOutcome(couponId, snapshot)?.let { return it }

        var autoFixed = reconcileSoldOut(couponId, snapshot)
        var listDriftAlert = false
        if (snapshot.listResidual > 0) {
            val redisUserIds = reconcileRedisRepository.userIds(couponId).mapNotNull { it.toLongOrNull() }.toSet()
            val missingUserIds = issuanceRepository.findUserIdsByCouponId(couponId) - redisUserIds
            if (missingUserIds.size.toLong() == snapshot.listResidual) {
                reconcileRedisRepository.addUsers(couponId, missingUserIds)
                autoFixed++
                log.info { "auto-fix: 사용자 목록 SADD ${missingUserIds.size}건 coupon=$couponId" }
            } else {
                listDriftAlert = true
                log.warn { "Redis 사용자 목록을 안전하게 복구할 수 없음 coupon=$couponId" }
            }
        } else if (snapshot.listResidual < 0) {
            listDriftAlert = true
            log.warn { "Redis 사용자 목록 초과 감지 coupon=$couponId residual=${snapshot.listResidual}" }
        }

        return CouponReconcileOutcome(autoFixed = autoFixed, listDriftAlert = listDriftAlert)
    }

    private fun unsafeOutcome(couponId: Long, snapshot: ReconcileSnapshot): CouponReconcileOutcome? {
        val stockNegative = snapshot.stock < 0
        if (stockNegative) log.warn { "재고 음수 감지 coupon=$couponId stock=${snapshot.stock}" }

        val dbResidual = snapshot.dbResidual
        if (dbResidual != 0L) {
            log.warn { "DB 측 불일치 감지 coupon=$couponId residual=$dbResidual (자동 보정 불가, 사람 확인)" }
        }
        if (!stockNegative && dbResidual == 0L) return null

        return CouponReconcileOutcome(
            confirmedDbDrift = abs(dbResidual),
            stockNegative = stockNegative,
        )
    }

    private fun reconcileSoldOut(couponId: Long, snapshot: ReconcileSnapshot): Int =
        when {
            snapshot.stock > 0 && snapshot.soldOut -> {
                reconcileRedisRepository.deleteSoldOut(couponId)
                log.info { "auto-fix: 매진 플래그 해제 coupon=$couponId" }
                1
            }
            snapshot.stock == 0L && !snapshot.soldOut -> {
                reconcileRedisRepository.setSoldOut(couponId, soldOutProperties.ttlSeconds)
                log.info { "auto-fix: 매진 플래그 설정 coupon=$couponId" }
                1
            }
            else -> 0
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
}

class CouponReconcileOutcome(
    val autoFixed: Int = 0,
    val confirmedDbDrift: Long = 0,
    val stockNegative: Boolean = false,
    val listDriftAlert: Boolean = false,
    val failed: Boolean = false,
)
