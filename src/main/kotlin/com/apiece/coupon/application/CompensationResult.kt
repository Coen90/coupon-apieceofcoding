package com.apiece.coupon.application

// 보상 처리 결과. compensated=false 면 멱등 hit (같은 compensationId 가 이미 처리됨).
class CompensationResult(
    val compensationId: String,
    val compensated: Boolean,
)
