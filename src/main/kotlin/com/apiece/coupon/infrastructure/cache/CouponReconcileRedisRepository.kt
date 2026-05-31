package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

// reconcile 이 한 쿠폰의 Redis 값을 읽고(스냅샷), 안전한 불일치를 자동 보정(SADD/DEL/SET)한다.
@Repository
class CouponReconcileRedisRepository(
    private val redis: StringRedisTemplate,
) {

    fun hasStock(couponId: Long): Boolean =
        redis.hasKey("coupon:$couponId:stock")

    // 재고 카운터. 키가 없으면 null (추적 대상이 아니거나 휘발).
    fun stock(couponId: Long): Long? =
        redis.opsForValue().get("coupon:$couponId:stock")?.toLongOrNull()

    fun userCount(couponId: Long): Long =
        redis.opsForSet().size("coupon:$couponId:users") ?: 0L

    fun userIds(couponId: Long): Set<String> =
        redis.opsForSet().members("coupon:$couponId:users") ?: emptySet()

    fun soldOutExists(couponId: Long): Boolean =
        redis.hasKey("coupon:$couponId:sold_out")

    // --- 자동 보정 (멱등 연산) ---

    // 목록 측 불일치 보정: DB 에는 있는데 Redis 목록에서 빠진 user_id 를 되살린다.
    fun addUsers(couponId: Long, userIds: Collection<Long>) {
        if (userIds.isEmpty()) return
        redis.opsForSet().add("coupon:$couponId:users", *userIds.map { it.toString() }.toTypedArray())
    }

    // 매진 플래그 잘못 살아남음 보정: 재고가 양수인데 플래그가 있으면 푼다.
    fun deleteSoldOut(couponId: Long) {
        redis.delete("coupon:$couponId:sold_out")
    }

    // 매진인데 플래그가 빠진 경우 보정: 재고 0인데 플래그가 없으면 세운다.
    fun setSoldOut(couponId: Long, ttlSeconds: Long) {
        redis.opsForValue().set("coupon:$couponId:sold_out", "1", ttlSeconds, TimeUnit.SECONDS)
    }
}
