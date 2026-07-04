package com.apiece.coupon.application

import com.apiece.coupon.domain.Coupon
import java.time.LocalDateTime

class CouponIssuePolicy(
    val startsAt: LocalDateTime?,
    val validityDays: Int,
) {
    fun isBookingOpen(now: LocalDateTime): Boolean = startsAt == null || !now.isBefore(startsAt)

    companion object {
        fun from(coupon: Coupon): CouponIssuePolicy = CouponIssuePolicy(
            startsAt = coupon.startsAt,
            validityDays = coupon.validityDays,
        )
    }
}
