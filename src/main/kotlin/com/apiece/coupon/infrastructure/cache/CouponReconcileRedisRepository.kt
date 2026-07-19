package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class CouponReconcileRedisRepository(
    private val redis: StringRedisTemplate,
) {

    fun couponIdsIssuedBetween(fromExclusiveMs: Long, toInclusiveMs: Long): List<Long> {
        if (toInclusiveMs <= fromExclusiveMs) return emptyList()
        val members = redis.opsForZSet().rangeByScore(
            "coupon:reconcile:recent",
            (fromExclusiveMs + 1).toDouble(),
            toInclusiveMs.toDouble(),
        ) ?: emptySet()
        return members.mapNotNull { it.toLongOrNull() }
    }

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
