package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

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
