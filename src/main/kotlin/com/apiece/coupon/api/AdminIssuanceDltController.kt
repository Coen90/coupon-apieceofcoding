package com.apiece.coupon.api

import com.apiece.coupon.api.dto.IssuanceDltResponse
import com.apiece.coupon.api.dto.IssuanceDltReplayResponse
import com.apiece.coupon.application.IssuanceDltService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/issuance/dlt")
class AdminIssuanceDltController(
    private val issuanceDltService: IssuanceDltService,
) {
    @GetMapping
    fun findRecent(): List<IssuanceDltResponse> =
        issuanceDltService.findRecent().map(IssuanceDltResponse::from)

    @PostMapping("/replay")
    fun replay(): IssuanceDltReplayResponse = IssuanceDltReplayResponse(issuanceDltService.replayPending())

    @PostMapping("/compensate")
    fun compensate(@RequestParam id: Long): IssuanceDltResponse =
        IssuanceDltResponse.from(issuanceDltService.compensate(id))
}
