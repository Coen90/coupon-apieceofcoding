package com.apiece.coupon.api

import com.apiece.coupon.api.dto.DltInboxResponse
import com.apiece.coupon.application.DltInboxService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/dlt/messages")
class AdminDltInboxController(
    private val dltInboxService: DltInboxService,
) {
    @GetMapping
    fun pending(): List<DltInboxResponse> =
        dltInboxService.pending().map(DltInboxResponse::from)

    @PostMapping("/{id}/replay")
    fun replay(@PathVariable id: Long): DltInboxResponse =
        DltInboxResponse.from(dltInboxService.replay(id))

    @PostMapping("/{id}/compensate")
    fun compensate(@PathVariable id: Long): DltInboxResponse =
        DltInboxResponse.from(dltInboxService.compensate(id))
}
