package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

// 트래픽 지표: 대기실 진입(enters) -> 통과(admitted = 통과 속도) -> 발급 도달(issueArrivals).
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
