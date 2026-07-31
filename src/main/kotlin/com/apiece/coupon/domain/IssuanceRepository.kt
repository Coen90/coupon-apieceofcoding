package com.apiece.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository

interface IssuanceRepository : JpaRepository<Issuance, Long> {
    fun findByUserIdOrderByIssuedAtDesc(userId: Long): List<Issuance>

    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean
}
