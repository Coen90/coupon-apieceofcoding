package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class CouponReconcileRedisRepository(
    private val redis: StringRedisTemplate,
) {

    // 발급 시각이 (fromExclusiveMs, toInclusiveMs] 에 든 쿠폰 id (대사 sliding window).
    // score 는 정수 ms 라 하한에 +1 해서 직전 회차가 본 cutoff 를 다시 보지 않게 한다.
    fun couponIdsIssuedBetween(fromExclusiveMs: Long, toInclusiveMs: Long): List<Long> {
        if (toInclusiveMs <= fromExclusiveMs) return emptyList()
        val members = redis.opsForZSet().rangeByScore(
            "coupon:reconcile:recent",
            (fromExclusiveMs + 1).toDouble(),
            toInclusiveMs.toDouble(),
        ) ?: emptySet()
        return members.mapNotNull { it.toLongOrNull() }
    }

    // 키가 없으면 null (추적 대상 아님 또는 휘발).
    fun stock(couponId: Long): Long? =
        redis.opsForValue().get("coupon:$couponId:stock")?.toLongOrNull()

    fun userCount(couponId: Long): Long =
        redis.opsForSet().size("coupon:$couponId:users") ?: 0L

    fun userIds(couponId: Long): Set<String> =
        redis.opsForSet().members("coupon:$couponId:users") ?: emptySet()

    fun soldOutExists(couponId: Long): Boolean =
        redis.hasKey("coupon:$couponId:sold_out")

    fun addUsers(couponId: Long, userIds: Collection<Long>) {
        if (userIds.isEmpty()) return
        redis.opsForSet().add("coupon:$couponId:users", *userIds.map { it.toString() }.toTypedArray())
    }

    fun deleteSoldOut(couponId: Long) {
        redis.delete("coupon:$couponId:sold_out")
    }

    fun setSoldOut(couponId: Long, ttlSeconds: Long) {
        redis.opsForValue().set("coupon:$couponId:sold_out", "1", ttlSeconds, TimeUnit.SECONDS)
    }
}
