package com.apiece.coupon.infrastructure.messaging

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.Issuance
import com.apiece.coupon.domain.IssuanceRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IssuanceTransactionalWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
) {
    // 이미 처리된 재전달은 예외 없이 걸러낸다.
    @Transactional
    fun insertAndIncrement(event: IssuanceRequested) {
        if (issuanceRepository.existsByUserIdAndCouponId(event.userId, event.couponId)) return

        issuanceRepository.save(Issuance(
            userId = event.userId,
            couponId = event.couponId,
            issuedAt = event.issuedAt,
            expiresAt = event.expiresAt,
        ))
        couponRepository.incrementIssuedQuantity(event.couponId)
    }

    // 같은 메시지가 동시에 들어오면 둘 다 위 조회를 통과한다.
    // 그때는 uk_issuance_user_coupon 위반으로 진 쪽이 통째로 롤백되므로, 발급 수는 한 번만 증가한다.
    // 호출자는 롤백된 원인이 그 중복인지 다른 제약 위반인지 이 메서드로 가른다.
    @Transactional(readOnly = true)
    fun isAlreadyApplied(event: IssuanceRequested): Boolean =
        issuanceRepository.existsByUserIdAndCouponId(event.userId, event.couponId)
}
