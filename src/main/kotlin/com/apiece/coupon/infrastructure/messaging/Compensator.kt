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

        // DLT 위치가 아닌 원본 발급 operationId를 쓴다. 재투입해도 같은 발급은 같은 키다.
        compensationService.compensate(
            CompensationCommand(
                couponId = event.couponId,
                userId = event.userId,
                operationId = event.operationId,
                reason = CompensationReason.DLT_REPLAY,
                issuedAt = event.issuedAt,
                expiresAt = event.expiresAt,
            )
        )
    }
}
