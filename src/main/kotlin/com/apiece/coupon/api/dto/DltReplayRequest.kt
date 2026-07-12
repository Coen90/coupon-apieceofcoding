package com.apiece.coupon.api.dto

import java.time.LocalDateTime

class DltReplayRequest(
    val couponId: Long,
    val userId: Long,
    val issuanceAttemptId: String,
    val issuedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
)
