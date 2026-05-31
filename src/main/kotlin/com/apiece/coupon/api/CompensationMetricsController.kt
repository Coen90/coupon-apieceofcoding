package com.apiece.coupon.api

import com.apiece.coupon.api.dto.CompensationMetricsResponse
import com.apiece.coupon.application.CompensationMetrics
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/metrics/compensation")
class CompensationMetricsController(
    private val compensationMetrics: CompensationMetrics,
) {

    @GetMapping
    fun snapshot(): CompensationMetricsResponse =
        CompensationMetricsResponse.from(compensationMetrics.snapshot())

    @PostMapping("/reset")
    fun reset() {
        compensationMetrics.reset()
    }
}
