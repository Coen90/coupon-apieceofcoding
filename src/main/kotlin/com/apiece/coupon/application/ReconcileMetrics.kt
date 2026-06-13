package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

// drift/negative 는 마지막 실행 기준 게이지(set), auto_fix 는 누적 카운터(increment).
@Component
class ReconcileMetrics {
    private val drift = AtomicLong()
    private val negative = AtomicLong()
    private val autoFix = AtomicLong()

    fun setRedisDbDrift(value: Long) = drift.set(value)
    fun setStockNegative(value: Long) = negative.set(value)
    fun addAutoFix(count: Int) {
        autoFix.addAndGet(count.toLong())
    }

    val redisDbDrift: Long get() = drift.get()
    val stockNegative: Long get() = negative.get()
    val reconcileAutoFixTotal: Long get() = autoFix.get()

    fun reset() {
        drift.set(0)
        negative.set(0)
        autoFix.set(0)
    }
}
