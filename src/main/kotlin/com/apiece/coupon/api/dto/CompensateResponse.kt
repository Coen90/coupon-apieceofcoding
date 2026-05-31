package com.apiece.coupon.api.dto

import com.apiece.coupon.application.CompensationResult

// compensated=false 면 멱등 hit (이미 처리된 compensationId).
class CompensateResponse(
    val compensationId: String,
    val compensated: Boolean,
) {
    companion object {
        fun from(result: CompensationResult): CompensateResponse =
            CompensateResponse(result.compensationId, result.compensated)
    }
}
