package com.apiece.coupon.application

import com.apiece.coupon.domain.CompensationReason
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
    private val compensationService: CompensationService,
) {
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Transactional
    fun record(record: ConsumerRecord<String, ByteArray>) {
        val messageKey = "${record.topic()}:${record.partition()}:${record.offset()}"
        if (repository.findByMessageKey(messageKey) != null) return

        val event = runCatching {
            jsonMapper.readValue(record.value(), IssuanceRequested::class.java)
        }.getOrNull()?.takeIf { it.issuanceAttemptId.length <= MAX_ISSUANCE_ATTEMPT_ID_LENGTH }
        val retryCount = event?.issuanceAttemptId
            ?.let(repository::countByIssuanceAttemptId)?.toInt() ?: 0
        val exceptionType = header(record, KafkaHeaders.DLT_EXCEPTION_FQCN)?.take(MAX_EXCEPTION_TYPE_LENGTH)
        val invalidPayload = event == null
        val quarantined = invalidPayload || retryCount >= MAX_DLT_REPLAY_COUNT || isNonRetryable(exceptionType)
        repository.save(
            IssuanceDltLog(
                messageKey = messageKey,
                dltPartition = record.partition(),
                dltOffset = record.offset(),
                couponId = event?.couponId,
                userId = event?.userId,
                issuanceAttemptId = event?.issuanceAttemptId,
                issuedAt = event?.issuedAt,
                expiresAt = event?.expiresAt,
                exceptionType = exceptionType,
                failureReason = sanitizedHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                retryCount = retryCount,
                status = if (quarantined) IssuanceDltStatus.QUARANTINED else IssuanceDltStatus.PENDING,
                decisionReason = when {
                    invalidPayload -> "INVALID_PAYLOAD"
                    retryCount >= MAX_DLT_REPLAY_COUNT -> "REPLAY_LIMIT_EXCEEDED"
                    quarantined -> "NON_RETRYABLE_ERROR"
                    else -> null
                },
                receivedAt = LocalDateTime.now(),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun findRecent(): List<IssuanceDltLog> = repository.findTop100ByOrderByReceivedAtDesc()

    @Transactional
    fun replayPending(): Int {
        val logs = repository.findTop100ByStatusOrderByReceivedAtAsc(IssuanceDltStatus.PENDING)
        logs.forEach { log ->
            producer.publishAndWait(log.toEvent())
            log.status = IssuanceDltStatus.REPLAYED
            log.decisionReason = "OPERATOR_REPLAY"
            log.resolvedAt = LocalDateTime.now()
        }
        return logs.size
    }

    @Transactional
    fun compensate(id: Long): IssuanceDltLog {
        val log = repository.findByIdForUpdate(id) ?: throw IllegalArgumentException("DLT log not found: $id")
        if (log.status == IssuanceDltStatus.COMPENSATED) return log
        check(log.status == IssuanceDltStatus.PENDING || log.status == IssuanceDltStatus.QUARANTINED) {
            "Only pending or quarantined DLT logs can be compensated: $id"
        }
        check(log.couponId != null && log.userId != null && log.issuanceAttemptId != null) {
            "DLT log without issuance identifiers cannot be compensated: $id"
        }
        compensationService.compensate(
            CompensationCommand(
                couponId = log.couponId!!,
                userId = log.userId!!,
                issuanceAttemptId = log.issuanceAttemptId!!,
                reason = CompensationReason.OPERATOR_MANUAL,
                issuedAt = log.issuedAt,
                expiresAt = log.expiresAt,
            ),
        )
        if (log.status == IssuanceDltStatus.PENDING) log.decisionReason = "POLICY_CANCELED"
        log.status = IssuanceDltStatus.COMPENSATED
        log.resolvedAt = LocalDateTime.now()
        return repository.save(log)
    }

    private fun IssuanceDltLog.toEvent() = IssuanceRequested(
        couponId = requireNotNull(couponId),
        userId = requireNotNull(userId),
        issuanceAttemptId = requireNotNull(issuanceAttemptId),
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
        private const val MAX_ISSUANCE_ATTEMPT_ID_LENGTH = 36
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
