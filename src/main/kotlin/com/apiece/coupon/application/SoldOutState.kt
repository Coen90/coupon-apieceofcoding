package com.apiece.coupon.application

import com.apiece.coupon.infrastructure.cache.SoldOutProperties
import com.apiece.coupon.infrastructure.cache.SoldOutRedisRepository
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SoldOutState(
    private val soldOutRedisRepository: SoldOutRedisRepository,
    private val cacheMetrics: CacheMetrics,
    soldOutProperties: SoldOutProperties,
) {

    private val soldOutCache: LoadingCache<Long, Boolean> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMillis(soldOutProperties.fastPathTtlMs))
        .maximumSize(MAX_TRACKED_COUPONS)
        .build { couponId ->
            cacheMetrics.incrementSoldOutRedisExists()
            soldOutRedisRepository.isFlagged(couponId)
        }

    fun isSoldOut(couponId: Long): Boolean {
        val soldOut = soldOutCache.get(couponId) ?: false
        if (soldOut) cacheMetrics.incrementSoldOutFastPathHit()
        return soldOut
    }

    private companion object {
        const val MAX_TRACKED_COUPONS = 1_000L
    }
}
