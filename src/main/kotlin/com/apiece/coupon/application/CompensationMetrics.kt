package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class CompensationMetrics {
    private val compensated = AtomicLong()
    private val idempotentHit = AtomicLong()

    fun incrementCompensated() {
        compensated.incrementAndGet()
    }

    fun incrementIdempotentHit() {
        idempotentHit.incrementAndGet()
    }

    val compensationTotal: Long get() = compensated.get()
    val compensationIdempotentHitTotal: Long get() = idempotentHit.get()

    fun reset() {
        compensated.set(0)
        idempotentHit.set(0)
    }
}
