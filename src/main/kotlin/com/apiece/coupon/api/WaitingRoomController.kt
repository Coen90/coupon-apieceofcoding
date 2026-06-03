package com.apiece.coupon.api

import com.apiece.coupon.api.dto.WaitingRoomResponse
import com.apiece.coupon.application.TrafficMetrics
import com.apiece.coupon.application.WaitingRoom
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/waiting-room")
class WaitingRoomController(
    private val waitingRoom: WaitingRoom,
    private val trafficMetrics: TrafficMetrics,
) {

    @PostMapping("/{couponId}")
    fun enter(
        @PathVariable couponId: Long,
        @RequestHeader("X-User-Id") userId: Long,
    ): WaitingRoomResponse {
        trafficMetrics.incrementEnter()
        return WaitingRoomResponse.from(waitingRoom.enter(couponId, userId))
    }
}
