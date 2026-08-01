package com.apiece.coupon.batch

class ReconcileSnapshot(
    val total: Int,
    val issued: Int,
    val stock: Long,
    val userCount: Long,
    val soldOut: Boolean,
) {
    val dbResidual: Long get() = total - (issued + stock)
    val listResidual: Long get() = total - (userCount + stock)
}
