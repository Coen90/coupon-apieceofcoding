package com.apiece.coupon.api.dto

import com.apiece.coupon.domain.Coupon
import java.time.LocalDateTime

class CouponResponse(
    val id: Long,
    val name: String,
    val totalQuantity: Int,
    val validityDays: Int,
    val startsAt: LocalDateTime?,
) {
    companion object {
        fun from(coupon: Coupon): CouponResponse = CouponResponse(
            id = requireNotNull(coupon.id),
            name = coupon.name,
            totalQuantity = coupon.totalQuantity,
            validityDays = coupon.validityDays,
            startsAt = coupon.startsAt,
        )
    }
}
