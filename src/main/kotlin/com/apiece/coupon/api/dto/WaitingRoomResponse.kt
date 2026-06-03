package com.apiece.coupon.api.dto

import com.apiece.coupon.application.Admission

class WaitingRoomResponse(
    val admitted: Boolean,
    val position: Long,
    val estimatedWaitSeconds: Long,
) {
    companion object {
        fun from(admission: Admission): WaitingRoomResponse = WaitingRoomResponse(
            admitted = admission.admitted,
            position = admission.position,
            estimatedWaitSeconds = admission.estimatedWaitSeconds,
        )
    }
}
