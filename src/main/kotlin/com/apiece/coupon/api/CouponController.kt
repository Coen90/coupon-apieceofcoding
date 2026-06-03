package com.apiece.coupon.api

import com.apiece.coupon.api.dto.CouponResponse
import com.apiece.coupon.api.dto.CreateCouponRequest
import com.apiece.coupon.api.dto.IssuanceResponse
import com.apiece.coupon.application.CouponService
import com.apiece.coupon.application.TrafficMetrics
import com.apiece.coupon.application.WaitingRoom
import com.apiece.coupon.support.NoWaitingRoomPassException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/coupons")
class CouponController(
    private val couponService: CouponService,
    private val trafficMetrics: TrafficMetrics,
    private val waitingRoom: WaitingRoom,
) {

    @PostMapping
    fun create(@RequestBody request: CreateCouponRequest): ResponseEntity<CouponResponse> {
        val coupon = couponService.createCoupon(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(CouponResponse.from(coupon))
    }

    @PostMapping("/{couponId}/issue")
    fun issue(
        @PathVariable couponId: Long,
        @RequestHeader("X-User-Id") userId: Long,
    ): IssuanceResponse {
        // 입장권 없으면 발급 차단 (발급 도메인은 대기실을 모른다).
        if (!waitingRoom.isAdmitted(couponId, userId)) throw NoWaitingRoomPassException()
        trafficMetrics.incrementIssueArrival()
        val issuance = couponService.issue(couponId, userId)
        return IssuanceResponse.from(issuance)
    }
}
