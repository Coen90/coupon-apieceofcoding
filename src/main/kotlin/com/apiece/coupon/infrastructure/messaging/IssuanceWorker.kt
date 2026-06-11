package com.apiece.coupon.infrastructure.messaging

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class IssuanceWorker(
    private val writer: IssuanceWriter,
) {
    @KafkaListener(
        topics = [IssuanceTopics.REQUESTED],
        groupId = IssuanceTopics.CONSUMER_GROUP,
    )
    fun consume(event: IssuanceRequested) {
        writer.write(event)
    }
}
