package com.apiece.coupon.api.dto

import com.apiece.coupon.application.ReconcileReport

class ReconcileRunResponse(
    val checkedCoupons: Int,
    val autoFixed: Int,
    val driftAlerts: Int,
    val falseAlarms: Int,
    val redisDbDrift: Long,
    val stockNegative: Int,
) {
    companion object {
        fun from(report: ReconcileReport): ReconcileRunResponse = ReconcileRunResponse(
            checkedCoupons = report.checkedCoupons,
            autoFixed = report.autoFixed,
            driftAlerts = report.driftAlerts,
            falseAlarms = report.falseAlarms,
            redisDbDrift = report.redisDbDrift,
            stockNegative = report.stockNegative,
        )
    }
}
