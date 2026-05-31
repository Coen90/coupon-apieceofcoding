package com.apiece.coupon.api.dto

import com.apiece.coupon.application.ReconcileMetricsSnapshot

class ReconcileMetricsResponse(
    val redisDbDrift: Long,
    val reconcileAutoFixTotal: Long,
    val reconcileFalseAlarmTotal: Long,
    val stockNegative: Long,
) {
    companion object {
        fun from(snapshot: ReconcileMetricsSnapshot): ReconcileMetricsResponse = ReconcileMetricsResponse(
            redisDbDrift = snapshot.redisDbDrift,
            reconcileAutoFixTotal = snapshot.reconcileAutoFixTotal,
            reconcileFalseAlarmTotal = snapshot.reconcileFalseAlarmTotal,
            stockNegative = snapshot.stockNegative,
        )
    }
}
