package com.apiece.coupon.api.dto

import com.apiece.coupon.application.ReconcileReport

class ReconcileRunResponse(
    val checkedCoupons: Int,
    val autoFixed: Int,
    val driftAlerts: Int,
    val redisDbDrift: Long,
    val stockNegative: Int,
    val failedCoupons: Int,
) {
    companion object {
        fun from(report: ReconcileReport): ReconcileRunResponse = ReconcileRunResponse(
            checkedCoupons = report.checkedCoupons,
            autoFixed = report.autoFixed,
            driftAlerts = report.driftAlerts,
            redisDbDrift = report.redisDbDrift,
            stockNegative = report.stockNegative,
            failedCoupons = report.failedCoupons,
        )
    }
}
