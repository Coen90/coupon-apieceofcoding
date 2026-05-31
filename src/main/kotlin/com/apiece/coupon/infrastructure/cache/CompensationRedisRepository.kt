package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class CompensationRedisRepository(
    private val redis: StringRedisTemplate,
    private val properties: CompensationProperties,
) {

    private val compensateScript = longLuaScript("lua/compensate-issuance.lua")

    // 역연산(재고 +1, 사용자 목록 SREM, 매진 플래그 조건부 해제)을 한 덩어리로 실행.
    // 반환: 1=실제 보상, 0=멱등 hit 또는 목록에 없음.
    fun compensate(couponId: Long, userId: Long, compensationId: String): Long =
        redis.runForLong(
            compensateScript,
            listOf(
                "coupon:$couponId:stock",
                "coupon:$couponId:users",
                "coupon:$couponId:sold_out",
                "compensation:$compensationId",
            ),
            userId, properties.idempotencyTtlSeconds,
        )
}
