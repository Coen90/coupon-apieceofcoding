package com.apiece.coupon.infrastructure.messaging

import com.apiece.coupon.application.DltInboxService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DltInboxConsumer(
    private val dltInboxService: DltInboxService,
) {
    @KafkaListener(
        topics = [IssuanceTopics.REQUESTED_DLT],
        groupId = "dlt-operator-inbox",
    )
    fun consume(record: ConsumerRecord<String, IssuanceRequested>) {
        dltInboxService.receive(record)
    }
}
