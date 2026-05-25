package com.apiece.coupon.infrastructure.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "coupon.cache")
class CacheProperties(
    val ttlMs: Long,
<<<<<<< HEAD
    val simulatedLoadLatencyMs: Long,
=======
>>>>>>> cf9af09 (refactor(part-4-1a): Redis 호출을 CouponCacheRepository 로 분리 + ConfigurationProperties)
)
