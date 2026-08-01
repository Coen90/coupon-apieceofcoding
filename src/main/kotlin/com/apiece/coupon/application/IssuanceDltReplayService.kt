package com.apiece.coupon.application

import com.apiece.coupon.domain.IssuanceDltLog
import com.apiece.coupon.domain.IssuanceDltLogRepository
import com.apiece.coupon.domain.IssuanceDltStatus
import com.apiece.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.apiece.coupon.infrastructure.messaging.IssuanceRequested
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IssuanceDltReplayService(
    private val issuanceDltLogRepository: IssuanceDltLogRepository,
    private val issuanceRequestProducer: IssuanceRequestProducer,
) {
    @Transactional
    fun replay(ids: List<Long>): Int {
        val selectedIds = ids.distinct()
        require(selectedIds.isNotEmpty()) { "At least one DLT log id is required" }
        require(selectedIds.size <= MAX_REPLAY_COUNT) { "At most $MAX_REPLAY_COUNT DLT logs can be replayed" }

        val logs = issuanceDltLogRepository.findAllByIdForUpdate(selectedIds).sortedBy { it.receivedAt }
        check(logs.size == selectedIds.size) { "Some DLT logs were not found" }

        val events = logs.map { log ->
            check(log.status == IssuanceDltStatus.PENDING || log.status == IssuanceDltStatus.REVIEW_REQUIRED) {
                "Only pending or review-required DLT logs can be replayed: ${log.id}"
            }
            log.toEvent()
        }
        logs.zip(events).forEach { (log, event) ->
            issuanceRequestProducer.publishAndWait(event)
            log.status = IssuanceDltStatus.REPLAYED
            log.decisionReason = "OPERATOR_REPLAY"
        }
        return logs.size
    }

    private fun IssuanceDltLog.toEvent() = IssuanceRequested(
        couponId = requireNotNull(couponId),
        userId = requireNotNull(userId),
        issuedAt = requireNotNull(issuedAt),
        expiresAt = requireNotNull(expiresAt),
    )

    private companion object {
        const val MAX_REPLAY_COUNT = 100
    }
}
