package com.apiece.coupon.api

import com.apiece.coupon.api.dto.TrafficMetricsResponse
import com.apiece.coupon.application.TrafficMetrics
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/metrics/traffic")
class TrafficMetricsController(
    private val trafficMetrics: TrafficMetrics,
) {

    @GetMapping
    fun snapshot(): TrafficMetricsResponse =
        TrafficMetricsResponse.from(trafficMetrics.snapshot())

    @PostMapping("/reset")
    fun reset() {
        trafficMetrics.reset()
    }
}
