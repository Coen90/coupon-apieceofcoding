package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class IssuanceRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val soldOutProperties: SoldOutProperties,
) {

    private val issueScript = longLuaScript("lua/issue.lua")

    fun tryIssue(couponId: Long, userId: Long): Long =
        redisTemplate.runForLong(
            issueScript,
            listOf(
                "coupon:$couponId:stock",
                "coupon:$couponId:users",
                "coupon:$couponId:sold_out",
                "coupon:reconcile:recent",
            ),
            userId, soldOutProperties.ttlSeconds,
            System.currentTimeMillis(),
            couponId,
        )

    fun initStock(couponId: Long, totalQuantity: Int) {
        redisTemplate.opsForValue().set("coupon:$couponId:stock", totalQuantity.toString())
    }
}
