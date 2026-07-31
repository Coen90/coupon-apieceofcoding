package com.apiece.coupon.infrastructure.messaging

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.Issuance
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.domain.IssuanceHistory
import com.apiece.coupon.domain.IssuanceHistoryRepository
import com.apiece.coupon.domain.IssuanceStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IssuanceTransactionalWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
    private val issuanceHistoryRepository: IssuanceHistoryRepository,
) {
    @Transactional
    fun insertAndIncrement(event: IssuanceRequested) {
        if (issuanceRepository.findByUserIdAndCouponId(event.userId, event.couponId) != null) return

        issuanceRepository.save(Issuance(
            userId = event.userId,
            couponId = event.couponId,
            issuedAt = event.issuedAt,
            expiresAt = event.expiresAt,
        ))
        issuanceHistoryRepository.save(IssuanceHistory(
            userId = event.userId,
            couponId = event.couponId,
            status = IssuanceStatus.ISSUED,
            reason = "ISSUED",
            recordedAt = event.issuedAt,
        ))
        couponRepository.incrementIssuedQuantity(event.couponId)
    }

    @Transactional(readOnly = true)
    fun isAlreadyApplied(event: IssuanceRequested): Boolean =
        issuanceRepository.findByUserIdAndCouponId(event.userId, event.couponId) != null
}
