package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class IssuanceRedisRepository(
    private val redis: StringRedisTemplate,
    private val soldOutProperties: SoldOutProperties,
) {

    private val issueScript = longLuaScript("lua/issue.lua")

    fun tryIssue(couponId: Long, userId: Long): Long =
        redis.runForLong(
            issueScript,
            listOf("coupon:$couponId:stock", "coupon:$couponId:users", "coupon:$couponId:sold_out"),
            userId, soldOutProperties.ttlSeconds,
        )

    fun initStock(couponId: Long, totalQuantity: Int) {
        redis.opsForValue().set("coupon:$couponId:stock", totalQuantity.toString())
    }
}
