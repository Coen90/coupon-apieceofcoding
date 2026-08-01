package com.apiece.coupon.application

import com.apiece.coupon.domain.IssuanceDltLog
import com.apiece.coupon.domain.IssuanceDltLogRepository
import com.apiece.coupon.domain.IssuanceDltStatus
import com.apiece.coupon.infrastructure.messaging.IssuanceDltRecordParser
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class IssuanceDltService(
    private val issuanceDltLogRepository: IssuanceDltLogRepository,
    private val issuanceDltRecordParser: IssuanceDltRecordParser,
) {
    @Transactional
    fun record(record: ConsumerRecord<String, ByteArray>) {
        val messageKey = issuanceDltRecordParser.messageKey(record)
        if (issuanceDltLogRepository.existsByMessageKey(messageKey)) return

        val event = issuanceDltRecordParser.event(record)
        val retryCount = event
            ?.let { issuanceDltLogRepository.countByUserIdAndCouponId(it.userId, it.couponId).toInt() } ?: 0
        val exceptionType = issuanceDltRecordParser.exceptionType(record)

        val decisionReason = when {
            event == null -> "INVALID_PAYLOAD"
            retryCount >= MAX_DLT_REPLAY_COUNT -> "REPLAY_LIMIT_EXCEEDED"
            isNonRetryable(exceptionType) -> "NON_RETRYABLE_ERROR"
            else -> null
        }

        issuanceDltLogRepository.save(
            IssuanceDltLog(
                messageKey = messageKey,
                couponId = event?.couponId,
                userId = event?.userId,
                issuedAt = event?.issuedAt,
                expiresAt = event?.expiresAt,
                exceptionType = exceptionType,
                failureReason = issuanceDltRecordParser.failureReason(record),
                retryCount = retryCount,
                status = if (decisionReason == null) IssuanceDltStatus.PENDING else IssuanceDltStatus.REVIEW_REQUIRED,
                decisionReason = decisionReason,
                receivedAt = LocalDateTime.now(),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun findRecent(): List<IssuanceDltLog> = issuanceDltLogRepository.findTop100ByOrderByReceivedAtDesc()

    private fun isNonRetryable(exceptionType: String?): Boolean =
        exceptionType?.substringAfterLast('.') in NON_RETRYABLE_EXCEPTIONS

    companion object {
        private const val MAX_DLT_REPLAY_COUNT = 3
        private val NON_RETRYABLE_EXCEPTIONS = setOf(
            "DeserializationException",
            "MessageConversionException",
            "MethodArgumentResolutionException",
            "IllegalArgumentException",
        )
    }
}
