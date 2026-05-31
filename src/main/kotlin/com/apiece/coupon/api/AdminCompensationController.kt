package com.apiece.coupon.api

import com.apiece.coupon.api.dto.CompensateRequest
import com.apiece.coupon.api.dto.CompensateResponse
import com.apiece.coupon.application.CompensationCommand
import com.apiece.coupon.application.CompensationService
import com.apiece.coupon.domain.CompensationReason
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 운영자/도구가 보상 한 건을 직접 트리거. 같은 compensationId 로 두 번 호출해도
// 멱등 키 덕에 한 번만 반영된다 (5단원 7. 보상 멱등성 시나리오).
@RestController
@RequestMapping("/admin/compensate")
class AdminCompensationController(
    private val compensationService: CompensationService,
) {
    @PostMapping
    fun compensate(@RequestBody request: CompensateRequest): CompensateResponse {
        val result = compensationService.compensate(
            CompensationCommand(
                couponId = request.couponId,
                userId = request.userId,
                compensationId = request.compensationId,
                reason = CompensationReason.OPERATOR_MANUAL,
            )
        )
        return CompensateResponse.from(result)
    }
}
