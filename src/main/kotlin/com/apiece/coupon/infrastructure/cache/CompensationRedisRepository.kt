package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class CompensationRedisRepository(
    private val redis: StringRedisTemplate,
    private val properties: CompensationProperties,
) {

    private val compensateScript = longLuaScript("lua/compensate-issuance.lua")

    // 반환: 1=실제 보상, 0=이미 보상, -1=다른 발급.
    fun compensate(couponId: Long, userId: Long, issuanceAttemptId: String): Long =
        redis.runForLong(
            compensateScript,
            listOf(
                "coupon:$couponId:stock",
                "coupon:$couponId:users",
                "coupon:$couponId:sold_out",
                "compensation:$issuanceAttemptId",
                "coupon:$couponId:issuance-attempt:$userId",
            ),
            userId, properties.idempotencyTtlSeconds, issuanceAttemptId,
        )
}
