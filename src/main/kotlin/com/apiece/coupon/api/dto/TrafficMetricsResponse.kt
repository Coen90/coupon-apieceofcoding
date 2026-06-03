package com.apiece.coupon.api.dto

import com.apiece.coupon.application.TrafficMetricsSnapshot

class TrafficMetricsResponse(
    val waitingRoomEnters: Long,
    val admitted: Long,
    val issueArrivals: Long,
) {
    companion object {
        fun from(snapshot: TrafficMetricsSnapshot): TrafficMetricsResponse = TrafficMetricsResponse(
            waitingRoomEnters = snapshot.waitingRoomEnters,
            admitted = snapshot.admitted,
            issueArrivals = snapshot.issueArrivals,
        )
    }
}
