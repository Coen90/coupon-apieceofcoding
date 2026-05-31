package com.apiece.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository

interface IssuanceRepository : JpaRepository<Issuance, Long> {
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    fun findByUserIdOrderByIssuedAtDesc(userId: Long): List<Issuance>

    // 보상 시 되돌릴 기존 발급 행을 찾는다. uk_issuance_user_coupon 으로 (user, coupon)
    // 당 최대 1행이라 단건이다.
    fun findByUserIdAndCouponId(userId: Long, couponId: Long): Issuance?
}
