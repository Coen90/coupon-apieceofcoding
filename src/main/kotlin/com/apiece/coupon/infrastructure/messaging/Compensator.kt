package com.apiece.coupon.infrastructure.messaging

import com.apiece.coupon.application.CompensationCommand
import com.apiece.coupon.application.CompensationService
import com.apiece.coupon.domain.CompensationReason
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class Compensator(
    private val compensationService: CompensationService,
) {
    @KafkaListener(
        topics = [IssuanceTopics.REQUESTED_DLT],
        groupId = IssuanceTopics.COMPENSATOR_GROUP,
        containerFactory = "compensatorListenerContainerFactory",
    )
    fun consume(record: ConsumerRecord<String, IssuanceRequested>) {
        val event = record.value() ?: run {
            log.warn { "DLT 메시지 역직렬화 실패, skip: offset=${record.offset()}" }
            return
        }

        // compensationId 는 DLT 메시지의 위치로 고정 (재투입/중복 폴링에도 같은 사건 = 같은 키).
        val compensationId = "dlt:${record.topic()}:${record.partition()}:${record.offset()}"
        compensationService.compensate(
            CompensationCommand(
                couponId = event.couponId,
                userId = event.userId,
                compensationId = compensationId,
                reason = CompensationReason.DLT_REPLAY,
                issuedAt = event.issuedAt,
                expiresAt = event.expiresAt,
            )
        )
    }
}
