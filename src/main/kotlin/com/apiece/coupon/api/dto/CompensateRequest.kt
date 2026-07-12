package com.apiece.coupon.api.dto

class CompensateRequest(
    val couponId: Long,
    val userId: Long,
    val issuanceAttemptId: String,
)
