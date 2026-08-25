package com.coen.coupon.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CouponRepository : JpaRepository<Coupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    fun findByIdForUpdate(id: Long): Coupon?
}
