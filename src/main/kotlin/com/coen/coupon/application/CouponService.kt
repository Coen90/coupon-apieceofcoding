package com.coen.coupon.application

import com.coen.coupon.api.dto.CreateCouponRequest
import com.coen.coupon.domain.Coupon
import com.coen.coupon.domain.CouponRepository
import com.coen.coupon.domain.Issuance
import com.coen.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.coen.coupon.infrastructure.messaging.IssuanceRequested
import com.coen.coupon.support.CouponNotFoundException
import com.coen.coupon.support.NotStartedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponIssuer: CouponIssuer,
    private val issuanceRequestProducer: IssuanceRequestProducer,
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

    @Transactional
    fun issue(couponId: Long, userId: Long): Issuance {
        val coupon = couponRepository.findById(couponId)
            .orElseThrow { CouponNotFoundException() }

        val now = LocalDateTime.now()

        if (!coupon.isBookingOpen(now)) {
            throw NotStartedException()
        }

        couponIssuer.tryIssue(couponId, userId)

        val expiresAt = now.plusDays(coupon.validityDays.toLong())
        issuanceRequestProducer.publish(
            IssuanceRequested(
                couponId = couponId,
                userId = userId,
                issuedAt = now,
                expiresAt = expiresAt
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