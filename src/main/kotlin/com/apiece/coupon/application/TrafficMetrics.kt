package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class TrafficMetrics {
    private val waitingRoomEnters = AtomicLong()
    private val admitted = AtomicLong()
    private val issueArrivals = AtomicLong()

    fun incrementEnter() {
        waitingRoomEnters.incrementAndGet()
    }

    fun addAdmitted(count: Int) {
        admitted.addAndGet(count.toLong())
    }

    fun incrementIssueArrival() {
        issueArrivals.incrementAndGet()
    }

    fun snapshot(): TrafficMetricsSnapshot = TrafficMetricsSnapshot(
        waitingRoomEnters = waitingRoomEnters.get(),
        admitted = admitted.get(),
        issueArrivals = issueArrivals.get(),
    )

    fun reset() {
        waitingRoomEnters.set(0)
        admitted.set(0)
        issueArrivals.set(0)
    }
}

class TrafficMetricsSnapshot(
    val waitingRoomEnters: Long,
    val admitted: Long,
    val issueArrivals: Long,
)
