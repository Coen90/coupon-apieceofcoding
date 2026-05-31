package com.apiece.coupon.infrastructure.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "coupon.reconcile")
class ReconcileProperties(
    // 배치 주기 (매 분 1회 = 60000).
    val intervalMs: Long,
    // false alarm 거르기용 재검사 지연. 1차에 불일치가 보이면 이만큼 뒤 한 번 더 본다 (5단원 5.4).
    val recheckDelayMs: Long,
)
