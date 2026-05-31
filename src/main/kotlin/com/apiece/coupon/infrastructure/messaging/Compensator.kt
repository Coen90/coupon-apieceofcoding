package com.apiece.coupon.infrastructure.messaging

import com.apiece.coupon.application.CompensationCommand
import com.apiece.coupon.application.CompensationService
import com.apiece.coupon.domain.CompensationReason
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

// DLT 토픽의 @KafkaListener. 메시지 1건이 곧 되돌릴 보상 1건이다 (5단원 4.3).
// 끝까지 성공해야 offset 이 commit 되고, 도중 실패하면 commit 되지 않아 다음 폴링에서
// 같은 메시지가 다시 온다 (멱등 키 덕분에 두 번 처리돼도 안전).
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
        val event = record.value()
        if (event == null) {
            // ErrorHandlingDeserializer 가 깨진 payload 를 null 로 넘긴 경우. 보상할 게 없다.
            log.warn { "DLT 메시지 역직렬화 실패, skip: offset=${record.offset()}" }
            return
        }

        // compensationId 는 payload 의 UUID 가 아니라 DLT 메시지의 "위치" 로 고정한다.
        // 재투입/중복 폴링에도 위치는 같아서 같은 사건을 같은 키로 식별할 수 있다 (5단원 4.3).
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
