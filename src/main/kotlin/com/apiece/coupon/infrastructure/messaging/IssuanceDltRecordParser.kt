package com.apiece.coupon.infrastructure.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets

@Component
class IssuanceDltRecordParser {
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    fun messageKey(record: ConsumerRecord<*, *>): String =
        "${record.topic()}:${record.partition()}:${record.offset()}"

    fun event(record: ConsumerRecord<String, ByteArray>): IssuanceRequested? =
        runCatching { jsonMapper.readValue(record.value(), IssuanceRequested::class.java) }
            .getOrNull()
            ?.takeIf { it.couponId > 0 && it.userId > 0 }

    fun exceptionType(record: ConsumerRecord<*, *>): String? =
        header(record, KafkaHeaders.DLT_EXCEPTION_FQCN)?.take(MAX_EXCEPTION_TYPE_LENGTH)

    fun failureReason(record: ConsumerRecord<*, *>): String? =
        header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE)
            ?.replace(WHITESPACE, " ")
            ?.take(MAX_FAILURE_REASON_LENGTH)

    private fun header(record: ConsumerRecord<*, *>, name: String): String? =
        record.headers().lastHeader(name)?.value()?.toString(StandardCharsets.UTF_8)

    private companion object {
        const val MAX_EXCEPTION_TYPE_LENGTH = 200
        const val MAX_FAILURE_REASON_LENGTH = 500
        val WHITESPACE = Regex("[\\r\\n\\t]+")
    }
}
