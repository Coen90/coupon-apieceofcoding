package com.apiece.coupon.application

import com.apiece.coupon.domain.CompensationReason
import com.apiece.coupon.domain.DltInbox
import com.apiece.coupon.domain.DltInboxRepository
import com.apiece.coupon.domain.DltInboxStatus
import com.apiece.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.apiece.coupon.infrastructure.messaging.IssuanceRequested
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

@Service
class DltInboxService(
    private val repository: DltInboxRepository,
    private val producer: IssuanceRequestProducer,
    private val compensationService: CompensationService,
) {
    @Transactional
    fun receive(record: ConsumerRecord<String, IssuanceRequested>) {
        val event = record.value()
        val messageKey = "${record.topic()}:${record.partition()}:${record.offset()}"
        if (repository.findByMessageKey(messageKey) != null) return

        repository.save(
            DltInbox(
                messageKey = messageKey,
                dltPartition = record.partition(),
                dltOffset = record.offset(),
                couponId = event.couponId,
                userId = event.userId,
                issuanceAttemptId = event.issuanceAttemptId,
                issuedAt = event.issuedAt,
                expiresAt = event.expiresAt,
                failureReason = record.headers().lastHeader("kafka_dlt-exception-message")
                    ?.value()?.toString(StandardCharsets.UTF_8),
                receivedAt = LocalDateTime.now(),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun pending(): List<DltInbox> = repository.findAllByStatusOrderByReceivedAtAsc(DltInboxStatus.PENDING)

    @Transactional
    fun replay(id: Long): DltInbox {
        val inbox = pendingForUpdate(id)
        producer.publishAndWait(
            IssuanceRequested(
                couponId = inbox.couponId,
                userId = inbox.userId,
                issuanceAttemptId = inbox.issuanceAttemptId,
                issuedAt = inbox.issuedAt,
                expiresAt = inbox.expiresAt,
            ),
        )
        inbox.status = DltInboxStatus.REPLAYED
        inbox.decisionReason = "REPLAY"
        inbox.resolvedAt = LocalDateTime.now()
        return inbox
    }

    @Transactional
    fun compensate(id: Long): DltInbox {
        val inbox = pendingForUpdate(id)
        compensationService.compensate(
            CompensationCommand(
                couponId = inbox.couponId,
                userId = inbox.userId,
                issuanceAttemptId = inbox.issuanceAttemptId,
                reason = CompensationReason.OPERATOR_MANUAL,
                issuedAt = inbox.issuedAt,
                expiresAt = inbox.expiresAt,
            ),
        )
        inbox.status = DltInboxStatus.COMPENSATED
        inbox.decisionReason = "COMPENSATE"
        inbox.resolvedAt = LocalDateTime.now()
        return inbox
    }

    private fun pendingForUpdate(id: Long): DltInbox {
        val inbox = repository.findByIdForUpdate(id) ?: error("DLT message not found: $id")
        check(inbox.status == DltInboxStatus.PENDING) { "DLT message already resolved: $id" }
        return inbox
    }
}
