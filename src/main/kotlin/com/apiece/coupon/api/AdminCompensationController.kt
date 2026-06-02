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

@RestController
@RequestMapping("/admin/compensate")
class AdminCompensationController(
    private val compensationService: CompensationService,
) {
    @PostMapping
    fun compensate(@RequestBody request: CompensateRequest): CompensateResponse {
        val compensated = compensationService.compensate(
            CompensationCommand(
                couponId = request.couponId,
                userId = request.userId,
                compensationId = request.compensationId,
                reason = CompensationReason.OPERATOR_MANUAL,
            )
        )
        return CompensateResponse(request.compensationId, compensated)
    }
}
