package com.apiece.coupon.infrastructure.messaging

object IssuanceTopics {
    const val REQUESTED = "issuance.requested"
    const val REQUESTED_DLT = "issuance.requested.DLT"
    const val CONSUMER_GROUP = "issuance-worker"

    // DLT 를 소비해 보상하는 처리기의 컨슈머 그룹. 메인 Worker 와 분리한다.
    const val COMPENSATOR_GROUP = "issuance-compensator"
}
