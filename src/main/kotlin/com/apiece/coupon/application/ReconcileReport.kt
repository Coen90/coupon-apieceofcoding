package com.apiece.coupon.application

// reconcile 1회 실행의 요약. /admin/reconcile/run 응답과 로그에 쓴다.
class ReconcileReport(
    val checkedCoupons: Int,
    val autoFixed: Int,
    val driftAlerts: Int,
    val falseAlarms: Int,
    val redisDbDrift: Long,
    val stockNegative: Int,
)
