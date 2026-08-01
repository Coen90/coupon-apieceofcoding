package com.apiece.coupon.batch

class ReconcileReport(
    val checkedCoupons: Int = 0,
    val autoFixed: Int = 0,
    val driftAlerts: Int = 0,
    val redisDbDrift: Long = 0,
    val stockNegative: Int = 0,
    val failedCoupons: Int = 0,
)
