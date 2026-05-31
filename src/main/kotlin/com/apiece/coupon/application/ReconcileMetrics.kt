package com.apiece.coupon.application

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

// reconcile 측정용 (5단원 7 핵심 지표). CacheMetrics 와 같은 AtomicLong 패턴.
// drift/negative 는 마지막 실행 기준 게이지, auto_fix/false_alarm 은 누적 카운터.
@Component
class ReconcileMetrics {
    private val redisDbDrift = AtomicLong()          // 게이지: 마지막 실행에서 확정된 DB 측 잔차 합(절댓값)
    private val stockNegative = AtomicLong()         // 게이지: 마지막 실행에서 재고 음수인 쿠폰 수 (항상 0이어야)
    private val autoFixTotal = AtomicLong()          // 누적: 자동 보정한 횟수
    private val falseAlarmTotal = AtomicLong()       // 누적: 재검사로 걸러진 false alarm 횟수

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
