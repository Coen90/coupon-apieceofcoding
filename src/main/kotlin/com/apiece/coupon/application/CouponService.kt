package com.apiece.coupon.application

import com.apiece.coupon.api.dto.CreateCouponRequest
import com.apiece.coupon.domain.Coupon
import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.Issuance
import com.apiece.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.apiece.coupon.infrastructure.messaging.IssuanceRequested
import com.apiece.coupon.support.NotStartedException
import com.apiece.coupon.support.SoldOutException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponIssuePolicyReader: CouponIssuePolicyReader,
    private val couponIssuer: CouponIssuer,
    private val issuanceRequestProducer: IssuanceRequestProducer,
    private val soldOutState: SoldOutState,
) {

    @Transactional
    fun createCoupon(request: CreateCouponRequest): Coupon {
        val coupon = couponRepository.save(
            Coupon(
                name = request.name,
                totalQuantity = request.totalQuantity,
                validityDays = request.validityDays,
                startsAt = request.startsAt,
            )
        )
        couponIssuer.initStock(coupon.id!!, coupon.totalQuantity)
        return coupon
    }

    fun issue(couponId: Long, userId: Long): Issuance {
        if (soldOutState.isSoldOut(couponId)) {
            throw SoldOutException()
        }

        val policy = couponIssuePolicyReader.get(couponId)

        val now = LocalDateTime.now()
        if (!policy.isBookingOpen(now)) {
            throw NotStartedException()
        }

        val issuanceAttemptId = UUID.randomUUID().toString()
        couponIssuer.tryIssue(couponId, userId, issuanceAttemptId)

        val expiresAt = now.plusDays(policy.validityDays.toLong())
        issuanceRequestProducer.publish(
            IssuanceRequested(
                couponId = couponId,
                userId = userId,
                issuanceAttemptId = issuanceAttemptId,
                issuedAt = now,
                expiresAt = expiresAt,
            )
        )

        return Issuance(
            userId = userId,
            couponId = couponId,
            issuedAt = now,
            expiresAt = expiresAt,
        )
    }
}
