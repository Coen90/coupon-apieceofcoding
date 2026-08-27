package com.coen.coupon.infrastructure.messaging

import com.coen.coupon.domain.CouponRepository
import com.coen.coupon.domain.Issuance
import com.coen.coupon.domain.IssuanceRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IssuanceTransactionWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
) {

    @Transactional
    fun insertAndIncrement(event: IssuanceRequested) {
        issuanceRepository.save(
            Issuance(
                userId = event.userId,
                couponId = event.couponId,
                issuedAt = event.issuedAt,
                expiresAt = event.expiresAt,
            )
        )
        couponRepository.incrementIssuedQuantity(event.couponId)
    }
}
