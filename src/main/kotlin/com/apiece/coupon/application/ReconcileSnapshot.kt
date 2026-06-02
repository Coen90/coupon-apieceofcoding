package com.apiece.coupon.application

// 잔차 = 두 등식이 깨진 정도 (① dbResidual: DB 측, ② listResidual: 목록 측, 5단원 5.1).
class ReconcileSnapshot(
    val couponId: Long,
    val total: Int,
    val issued: Int,
    val stock: Long,
    val userCount: Long,
    val soldOut: Boolean,
) {
    val dbResidual: Long get() = total - (issued + stock)
    val listResidual: Long get() = total - (userCount + stock)
}
