package com.apiece.coupon.infrastructure.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "coupon.waiting-room")
class WaitingRoomProperties(
    // 통과 속도: 1초마다 통과시키는 인원 (서버가 견딜 처리량).
    val admitPerSecond: Int,
    // 입장권 유효 시간 (통과 후 이 안에 발급).
    val passTtlMs: Long,
    // Redis 장애 시 정책. 고정 수량 도메인은 fail-close(false)가 기본 (fail-open 은 폭주 직격이라 위험).
    val failOpen: Boolean = false,
)
