package com.apiece.coupon.application

// 한 쿠폰의 4개 값을 한 시점에 같이 읽은 스냅샷 (5단원 5.1).
// 두 등식의 잔차를 따로 잰다:
//   dbResidual   = total - (issued + stock)   ... ① DB 측
//   listResidual = total - (users  + stock)   ... ② 목록 측
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
