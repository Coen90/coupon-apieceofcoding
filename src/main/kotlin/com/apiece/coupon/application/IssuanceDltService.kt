package com.apiece.coupon.application

import com.apiece.coupon.domain.IssuanceDltLog
import com.apiece.coupon.domain.IssuanceDltLogRepository
import com.apiece.coupon.domain.IssuanceDltStatus
import com.apiece.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.apiece.coupon.infrastructure.messaging.IssuanceRequested
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

@Service
class IssuanceDltService(
    private val repository: IssuanceDltLogRepository,
    private val producer: IssuanceRequestProducer,
) {
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Transactional
    fun record(record: ConsumerRecord<String, ByteArray>) {
        val messageKey = "${record.topic()}:${record.partition()}:${record.offset()}"
        if (repository.findByMessageKey(messageKey) != null) return

        val event = runCatching {
            jsonMapper.readValue(record.value(), IssuanceRequested::class.java)
        }.getOrNull()?.takeIf { it.couponId > 0 && it.userId > 0 }
        val retryCount = event?.let { repository.countByUserIdAndCouponId(it.userId, it.couponId).toInt() } ?: 0
        val exceptionType = header(record, KafkaHeaders.DLT_EXCEPTION_FQCN)?.take(MAX_EXCEPTION_TYPE_LENGTH)
        val invalidPayload = event == null
        val requiresReview = invalidPayload || retryCount >= MAX_DLT_REPLAY_COUNT || isNonRetryable(exceptionType)
        repository.save(
            IssuanceDltLog(
                messageKey = messageKey,
                dltPartition = record.partition(),
                dltOffset = record.offset(),
                couponId = event?.couponId,
                userId = event?.userId,
                issuedAt = event?.issuedAt,
                expiresAt = event?.expiresAt,
                exceptionType = exceptionType,
                failureReason = sanitizedHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                retryCount = retryCount,
                status = if (requiresReview) IssuanceDltStatus.REVIEW_REQUIRED else IssuanceDltStatus.PENDING,
                decisionReason = when {
                    invalidPayload -> "INVALID_PAYLOAD"
                    retryCount >= MAX_DLT_REPLAY_COUNT -> "REPLAY_LIMIT_EXCEEDED"
                    requiresReview -> "NON_RETRYABLE_ERROR"
                    else -> null
                },
                receivedAt = LocalDateTime.now(),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun findRecent(): List<IssuanceDltLog> = repository.findTop100ByOrderByReceivedAtDesc()

    @Transactional
    fun replay(ids: List<Long>): Int {
        val selectedIds = ids.distinct()
        require(selectedIds.isNotEmpty()) { "At least one DLT log id is required" }
        require(selectedIds.size <= MAX_REPLAY_COUNT) { "At most $MAX_REPLAY_COUNT DLT logs can be replayed" }

        val logs = repository.findAllByIdForUpdate(selectedIds).sortedBy { it.receivedAt }
        check(logs.size == selectedIds.size) { "Some DLT logs were not found" }
        val events = logs.map { log ->
            check(log.status == IssuanceDltStatus.PENDING || log.status == IssuanceDltStatus.REVIEW_REQUIRED) {
                "Only pending or review-required DLT logs can be replayed: ${log.id}"
            }
            log.toEvent()
        }
        logs.zip(events).forEach { (log, event) ->
            producer.publishAndWait(event)
            log.status = IssuanceDltStatus.REPLAYED
            log.decisionReason = "OPERATOR_REPLAY"
            log.resolvedAt = LocalDateTime.now()
        }
        return logs.size
    }

    private fun IssuanceDltLog.toEvent() = IssuanceRequested(
        couponId = requireNotNull(couponId),
        userId = requireNotNull(userId),
        issuedAt = requireNotNull(issuedAt),
        expiresAt = requireNotNull(expiresAt),
    )

    private fun header(record: ConsumerRecord<*, *>, name: String): String? =
        record.headers().lastHeader(name)?.value()?.toString(StandardCharsets.UTF_8)

    private fun sanitizedHeader(record: ConsumerRecord<*, *>, name: String): String? =
        header(record, name)?.replace(WHITESPACE, " ")?.take(MAX_FAILURE_REASON_LENGTH)

    private fun isNonRetryable(exceptionType: String?): Boolean =
        exceptionType?.substringAfterLast('.') in NON_RETRYABLE_EXCEPTIONS

    companion object {
        private const val MAX_DLT_REPLAY_COUNT = 3
        private const val MAX_REPLAY_COUNT = 100
        private const val MAX_EXCEPTION_TYPE_LENGTH = 200
        private const val MAX_FAILURE_REASON_LENGTH = 500
        private val WHITESPACE = Regex("[\\r\\n\\t]+")
        private val NON_RETRYABLE_EXCEPTIONS = setOf(
            "DeserializationException",
            "MessageConversionException",
            "MethodArgumentResolutionException",
            "IllegalArgumentException",
        )
    }
}
