package com.apiece.coupon.api.dto

class ReconcileMetricsResponse(
    val redisDbDrift: Long,
    val reconcileAutoFixTotal: Long,
    val stockNegative: Long,
)
