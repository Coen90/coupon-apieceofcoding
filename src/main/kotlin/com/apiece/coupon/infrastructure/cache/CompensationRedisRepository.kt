package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class CompensationRedisRepository(
    private val redis: StringRedisTemplate,
    private val properties: CompensationProperties,
) {

    private val compensateScript = longLuaScript("lua/compensate-issuance.lua")

    // 역연산(재고 +1, SREM, 매진 플래그 조건부 해제)을 한 덩어리로. 반환: 1=실제 보상, 0=중복 또는 다른 발급.
    fun compensate(couponId: Long, userId: Long, operationId: String): Long =
        redis.runForLong(
            compensateScript,
            listOf(
                "coupon:$couponId:stock",
                "coupon:$couponId:users",
                "coupon:$couponId:sold_out",
                "compensation:$operationId",
                "coupon:$couponId:operation:$userId",
            ),
            userId, properties.idempotencyTtlSeconds, operationId,
        )
}
