package com.apiece.coupon.infrastructure.messaging

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class IssuanceWorker(
    private val issuanceWriter: IssuanceWriter,
) {
    @KafkaListener(
        topics = [IssuanceTopics.REQUESTED],
        groupId = IssuanceTopics.CONSUMER_GROUP,
        concurrency = "3",
    )
    fun consume(event: IssuanceRequested) {
        issuanceWriter.write(event)
    }
}
