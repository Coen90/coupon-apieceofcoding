package com.apiece.coupon.application

class ReconcileReport(
    val checkedCoupons: Int,
    val autoFixed: Int,
    val driftAlerts: Int,
    val redisDbDrift: Long,
    val stockNegative: Int,
    val failedCoupons: Int = 0,
)
