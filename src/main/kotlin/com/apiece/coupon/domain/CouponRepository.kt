package com.apiece.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponRepository : JpaRepository<Coupon, Long> {

    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1 WHERE c.id = :id")
    fun incrementIssuedQuantity(@Param("id") id: Long): Int

    // issued_quantity > 0 가드로 멱등 키가 뚫린 최악의 경우에도 음수로 내려가지 않게 막는다.
    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity - 1 WHERE c.id = :id AND c.issuedQuantity > 0")
    fun decrementIssuedQuantity(@Param("id") id: Long): Int
}
