package com.apiece.coupon.api.dto

import com.apiece.coupon.domain.DltInbox
import java.time.LocalDateTime

class DltInboxResponse(
    val id: Long,
    val couponId: Long,
    val userId: Long,
    val issuanceAttemptId: String,
    val failureReason: String?,
    val status: String,
    val receivedAt: LocalDateTime,
) {
    companion object {
        fun from(inbox: DltInbox) = DltInboxResponse(
            id = inbox.id!!,
            couponId = inbox.couponId,
            userId = inbox.userId,
            issuanceAttemptId = inbox.issuanceAttemptId,
            failureReason = inbox.failureReason,
            status = inbox.status.name,
            receivedAt = inbox.receivedAt,
        )
    }
}
