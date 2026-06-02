package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

// 서버 도착 속도 측정. 발급 처리에 도달한 요청 수를 센다.
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
