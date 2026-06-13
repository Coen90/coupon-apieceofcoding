package com.apiece.coupon.api

import com.apiece.coupon.api.dto.ReconcileMetricsResponse
import com.apiece.coupon.application.ReconcileMetrics
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/metrics/reconcile")
class ReconcileMetricsController(
    private val reconcileMetrics: ReconcileMetrics,
) {

    @GetMapping
    fun snapshot(): ReconcileMetricsResponse = ReconcileMetricsResponse(
        redisDbDrift = reconcileMetrics.redisDbDrift,
        reconcileAutoFixTotal = reconcileMetrics.reconcileAutoFixTotal,
        stockNegative = reconcileMetrics.stockNegative,
    )

    @PostMapping("/reset")
    fun reset() {
        reconcileMetrics.reset()
    }
}
