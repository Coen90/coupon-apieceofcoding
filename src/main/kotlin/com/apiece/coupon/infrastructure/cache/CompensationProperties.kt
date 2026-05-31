package com.apiece.coupon.infrastructure.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "coupon.compensation")
class CompensationProperties(
    // Redis 멱등 키(compensation:{id})의 TTL. DLT 재투입/재시도 안전 창. 보통 24시간~7일.
    val idempotencyTtlSeconds: Long,
)
