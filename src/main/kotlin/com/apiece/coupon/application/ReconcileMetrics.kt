package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

// drift/negative 는 마지막 실행 기준 게이지(set), auto_fix/false_alarm 은 누적 카운터(increment).
@Component
class ReconcileMetrics {
    private val redisDbDrift = AtomicLong()
    private val stockNegative = AtomicLong()
    private val autoFixTotal = AtomicLong()
    private val falseAlarmTotal = AtomicLong()

    fun setRedisDbDrift(value: Long) = redisDbDrift.set(value)
    fun setStockNegative(value: Long) = stockNegative.set(value)
    fun incrementAutoFix() {
        autoFixTotal.incrementAndGet()
    }
    fun incrementFalseAlarm() {
        falseAlarmTotal.incrementAndGet()
    }

    fun snapshot(): ReconcileMetricsSnapshot = ReconcileMetricsSnapshot(
        redisDbDrift = redisDbDrift.get(),
        reconcileAutoFixTotal = autoFixTotal.get(),
        reconcileFalseAlarmTotal = falseAlarmTotal.get(),
        stockNegative = stockNegative.get(),
    )

    fun reset() {
        redisDbDrift.set(0)
        stockNegative.set(0)
        autoFixTotal.set(0)
        falseAlarmTotal.set(0)
    }
}

class ReconcileMetricsSnapshot(
    val redisDbDrift: Long,
    val reconcileAutoFixTotal: Long,
    val reconcileFalseAlarmTotal: Long,
    val stockNegative: Long,
)
