package com.apiece.coupon.api.dto

import com.apiece.coupon.domain.IssuanceDltLog
import java.time.LocalDateTime

class IssuanceDltResponse(
    val id: Long,
    val couponId: Long?,
    val userId: Long?,
    val exceptionType: String?,
    val failureReason: String?,
    val retryCount: Int,
    val status: String,
    val decisionReason: String?,
    val receivedAt: LocalDateTime,
) {
    companion object {
        fun from(log: IssuanceDltLog) = IssuanceDltResponse(
            id = log.id!!,
            couponId = log.couponId,
            userId = log.userId,
            exceptionType = log.exceptionType,
            failureReason = log.failureReason,
            retryCount = log.retryCount,
            status = log.status.name,
            decisionReason = log.decisionReason,
            receivedAt = log.receivedAt,
        )
    }
}
