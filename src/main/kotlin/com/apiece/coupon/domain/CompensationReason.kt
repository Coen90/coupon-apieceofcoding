package com.apiece.coupon.domain

// 보상이 일어난 이유. compensation_log.reason 으로 남겨 사후 분석에 쓴다.
enum class CompensationReason {
    // 발급 후 후속 단계(외부 API 등)가 영구 실패해 되돌림 (5단원 2.2).
    // 본편엔 후속 단계 실패 경로가 없어 아직 트리거되지 않는 예약값이다.
    DOWNSTREAM_FAILED,

    // DLT 에 쌓인 Worker INSERT 실패 메시지를 보상 처리기가 소비해 되돌림 (5단원 2.1).
    DLT_REPLAY,

    // 운영자가 /admin/compensate 로 직접 되돌림.
    OPERATOR_MANUAL,
}
