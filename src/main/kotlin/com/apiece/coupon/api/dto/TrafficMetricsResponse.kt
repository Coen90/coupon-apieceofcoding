package com.apiece.coupon.api.dto

import com.apiece.coupon.application.TrafficMetricsSnapshot

class TrafficMetricsResponse(
    val issueArrivals: Long,
) {
    companion object {
        fun from(snapshot: TrafficMetricsSnapshot): TrafficMetricsResponse = TrafficMetricsResponse(
            issueArrivals = snapshot.issueArrivals,
        )
    }
}
