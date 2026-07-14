package com.apiece.coupon.infrastructure.messaging

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class IssuanceRequestProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    fun publish(event: IssuanceRequested) {
        kafkaTemplate.send(IssuanceTopics.REQUESTED, event.userId.toString(), event)
    }

    fun publishAndWait(event: IssuanceRequested) {
        kafkaTemplate.send(IssuanceTopics.REQUESTED, event.userId.toString(), event)
            .get(10, TimeUnit.SECONDS)
    }
}
