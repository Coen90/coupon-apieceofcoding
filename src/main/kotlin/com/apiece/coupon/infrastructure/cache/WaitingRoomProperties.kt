package com.apiece.coupon.infrastructure.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "coupon.waiting-room")
class WaitingRoomProperties(
    val admitPerSecond: Int,
    val passTtlMs: Long,
)
