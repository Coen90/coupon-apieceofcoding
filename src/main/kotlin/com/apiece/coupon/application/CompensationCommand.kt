package com.apiece.coupon.application

import com.apiece.coupon.domain.CompensationReason
import java.time.LocalDateTime

class CompensationCommand(
    val couponId: Long,
    val userId: Long,
    val compensationId: String,
    val reason: CompensationReason,
    // 기존 행이 없어 CANCELED 행을 사후 INSERT 할 때 쓰는 값 (없으면 now).
    val issuedAt: LocalDateTime? = null,
    val expiresAt: LocalDateTime? = null,
)
