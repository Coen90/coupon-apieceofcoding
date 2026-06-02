package com.apiece.coupon.infrastructure.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "coupon.compensation")
class CompensationProperties(
    val idempotencyTtlSeconds: Long,
)
