package com.coen.coupon.application

import com.coen.coupon.api.dto.CreateCouponRequest
import com.coen.coupon.domain.Coupon
import com.coen.coupon.domain.CouponRepository
import com.coen.coupon.domain.Issuance
import com.coen.coupon.domain.IssuanceRepository
import com.coen.coupon.support.AlreadyIssuedException
import com.coen.coupon.support.CouponNotFoundException
import com.coen.coupon.support.NotStartedException
import com.coen.coupon.support.SoldOutException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val issuanceRepository: IssuanceRepository,
) {

    @Transactional
    fun createCoupon(request: CreateCouponRequest): Coupon {
        val coupon = Coupon(
            name = request.name,
            totalQuantity = request.totalQuantity,
            validityDays = request.validityDays,
            startsAt = request.startsAt,
        )
        return couponRepository.save(coupon)
    }

    @Transactional
    fun issue(couponId: Long, userId: Long): Issuance {
//        val coupon = couponRepository.findById(couponId)
//            .orElseThrow { CouponNotFoundException() }
        val coupon = couponRepository.findByIdForUpdate(couponId)
            ?: throw CouponNotFoundException()

        val now = LocalDateTime.now()

        if (!coupon.isBookingOpen(now)) {
            throw NotStartedException()
        }
        if (coupon.isSoldOut()) {
            throw SoldOutException()
        }
        if (issuanceRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw AlreadyIssuedException()
        }

        coupon.issuedQuantity++

        return issuanceRepository.save(
            Issuance(
                userId = userId,
                couponId = couponId,
                issuedAt = now,
                expiresAt = now.plusDays(coupon.validityDays.toLong()),
            )
        )
    }
}