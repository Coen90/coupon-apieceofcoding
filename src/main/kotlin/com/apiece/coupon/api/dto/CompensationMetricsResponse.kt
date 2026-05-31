package com.apiece.coupon.api.dto

import com.apiece.coupon.application.CompensationMetricsSnapshot

class CompensationMetricsResponse(
    val compensationTotal: Long,
    val compensationIdempotentHitTotal: Long,
) {
    companion object {
        fun from(snapshot: CompensationMetricsSnapshot): CompensationMetricsResponse =
            CompensationMetricsResponse(
                compensationTotal = snapshot.compensationTotal,
                compensationIdempotentHitTotal = snapshot.compensationIdempotentHitTotal,
            )
    }
}
