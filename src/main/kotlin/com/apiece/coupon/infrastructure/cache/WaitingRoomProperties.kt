package com.apiece.coupon.infrastructure.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "coupon.waiting-room")
class WaitingRoomProperties(
    val admitPerSecond: Int,
    val passTtlMs: Long,
    // Redis 장애 시 정책. 고정 수량 도메인은 fail-close(false)가 기본 (fail-open 은 폭주 직격이라 위험).
    val failOpen: Boolean = false,
)
