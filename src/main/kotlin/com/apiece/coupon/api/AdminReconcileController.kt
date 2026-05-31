package com.apiece.coupon.api

import com.apiece.coupon.api.dto.ReconcileRunResponse
import com.apiece.coupon.application.Reconciler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// reconcile 을 즉시 1회 돌린다 (스케줄을 기다리지 않고 검증/운영에서 확정적으로 트리거).
@RestController
@RequestMapping("/admin/reconcile")
class AdminReconcileController(
    private val reconciler: Reconciler,
) {
    @PostMapping("/run")
    fun run(): ReconcileRunResponse =
        ReconcileRunResponse.from(reconciler.reconcileAll())
}
