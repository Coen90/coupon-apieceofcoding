package com.apiece.coupon.api.dto

import java.time.LocalDateTime

class DltReplayRequest(
    val couponId: Long,
    val userId: Long,
    val operationId: String,
    val issuedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
)
