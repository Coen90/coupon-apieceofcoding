package com.apiece.coupon.infrastructure.messaging

import com.apiece.coupon.application.IssuanceDltService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

// DLT 메시지를 잃지 않도록 DB에 기록만 한다. replay 시점은 운영자가 결정한다.
@Component
class IssuanceDltLogConsumer(
    private val issuanceDltService: IssuanceDltService,
) {
    @KafkaListener(
        topics = [IssuanceTopics.REQUESTED_DLT],
        groupId = "issuance-dlt-log",
        containerFactory = "dltKafkaListenerContainerFactory",
    )
    fun consume(record: ConsumerRecord<String, ByteArray>) {
        issuanceDltService.record(record)
    }
}
