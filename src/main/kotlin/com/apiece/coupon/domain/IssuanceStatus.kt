package com.apiece.coupon.domain

enum class IssuanceStatus {
    ISSUED,
    USED,
    EXPIRED,

    // 보상 트랜잭션으로 되돌린 발급. ISSUED -> CANCELED 전이만 허용 (5단원 4.1).
    // hard delete 가 아니라 soft delete 로 남겨 감사(audit)와 reconcile COUNT 정합을 지킨다.
    CANCELED,
}
