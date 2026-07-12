package com.apiece.coupon.api

import com.apiece.coupon.api.dto.DltReplayRequest
import com.apiece.coupon.api.dto.DltReplayResponse
import com.apiece.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.apiece.coupon.infrastructure.messaging.IssuanceRequested
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// DLT는 자동 보상하지 않고, 운영자가 확인한 뒤 원본 토픽으로 재처리한다.
@RestController
@RequestMapping("/admin/dlt")
class AdminDltController(
    private val issuanceRequestProducer: IssuanceRequestProducer,
) {
    @PostMapping("/replay")
    fun replay(@RequestBody request: DltReplayRequest): DltReplayResponse {
        issuanceRequestProducer.publish(
            IssuanceRequested(
                couponId = request.couponId,
                userId = request.userId,
                issuanceAttemptId = request.issuanceAttemptId,
                issuedAt = request.issuedAt,
                expiresAt = request.expiresAt,
            )
        )
        return DltReplayResponse(request.issuanceAttemptId)
    }
}
