package com.apiece.coupon.api.dto

// 운영자 수동 보상 요청. compensationId 는 호출자가 만든 멱등 키 (같은 값으로 두 번
// 호출해도 한 번만 반영된다).
class CompensateRequest(
    val couponId: Long,
    val userId: Long,
    val compensationId: String,
)
