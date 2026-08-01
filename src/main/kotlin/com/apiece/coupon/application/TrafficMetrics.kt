package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class TrafficMetrics {
    private val issueArrivals = AtomicLong()

    fun incrementIssueArrival() {
        issueArrivals.incrementAndGet()
    }

    fun snapshot(): TrafficMetricsSnapshot = TrafficMetricsSnapshot(
        issueArrivals = issueArrivals.get(),
    )

    fun reset() {
        issueArrivals.set(0)
    }
}

class TrafficMetricsSnapshot(
    val issueArrivals: Long,
)
