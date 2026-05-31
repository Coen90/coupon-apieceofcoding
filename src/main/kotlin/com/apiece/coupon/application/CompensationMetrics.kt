package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

// 보상 측정용. CacheMetrics 와 같은 단순 AtomicLong 패턴 (단일 인스턴스 비교).
@Component
class CompensationMetrics {
    private val compensationTotal = AtomicLong()
    private val idempotentHitTotal = AtomicLong()

    fun incrementCompensated() {
        compensationTotal.incrementAndGet()
    }

    fun incrementIdempotentHit() {
        idempotentHitTotal.incrementAndGet()
    }

    fun snapshot(): CompensationMetricsSnapshot = CompensationMetricsSnapshot(
        compensationTotal = compensationTotal.get(),
        compensationIdempotentHitTotal = idempotentHitTotal.get(),
    )

    fun reset() {
        compensationTotal.set(0)
        idempotentHitTotal.set(0)
    }
}

class CompensationMetricsSnapshot(
    val compensationTotal: Long,
    val compensationIdempotentHitTotal: Long,
)
