package com.apiece.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface IssuanceRepository : JpaRepository<Issuance, Long> {
    fun findByUserIdOrderByIssuedAtDesc(userId: Long): List<Issuance>

    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    @Query("SELECT i.userId FROM Issuance i WHERE i.couponId = :couponId")
    fun findUserIdsByCouponId(@Param("couponId") couponId: Long): List<Long>
}
