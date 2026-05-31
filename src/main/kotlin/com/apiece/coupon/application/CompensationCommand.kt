package com.apiece.coupon.application

import com.apiece.coupon.domain.CompensationReason
import java.time.LocalDateTime

// 보상 한 건의 입력. compensationId 가 멱등 키다 (DLT 위치 또는 운영자 지정값).
class CompensationCommand(
    val couponId: Long,
    val userId: Long,
    val compensationId: String,
    val reason: CompensationReason,
    // 기존 행이 없어 사후 INSERT(status=CANCELED)할 때 채울 값. 없으면 now 로 채운다.
    val issuedAt: LocalDateTime? = null,
    val expiresAt: LocalDateTime? = null,
)
