package com.apiece.coupon.domain

enum class IssuanceStatus {
    ISSUED,
    USED,
    EXPIRED,

    // 보상으로 되돌린 발급. soft delete (행 유지, 상태만 전이) 라 감사와 reconcile COUNT 정합을 지킨다.
    CANCELED,
}
