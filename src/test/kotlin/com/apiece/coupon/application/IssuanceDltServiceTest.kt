package com.apiece.coupon.application

import com.apiece.coupon.domain.IssuanceDltLog
import com.apiece.coupon.domain.IssuanceDltLogRepository
import com.apiece.coupon.domain.IssuanceDltStatus
import com.apiece.coupon.infrastructure.messaging.IssuanceRequestProducer
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.KafkaHeaders
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IssuanceDltServiceTest {
    private val repository = mockk<IssuanceDltLogRepository>(relaxed = true)
    private val producer = mockk<IssuanceRequestProducer>(relaxed = true)
    private val compensationService = mockk<CompensationService>(relaxed = true)
    private val service = IssuanceDltService(repository, producer, compensationService)

    init {
        every { repository.findByMessageKey(any()) } returns null
        every { repository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `일시 장애는 운영자 replay 대기 상태로 기록`() {
        every { repository.countByIssuanceAttemptId("attempt-1") } returns 0
        val saved = slot<IssuanceDltLog>()

        service.record(record())

        verify { repository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.PENDING, saved.captured.status)
        assertEquals(0, saved.captured.retryCount)
    }

    @Test
    fun `데이터 오류는 즉시 격리`() {
        every { repository.countByIssuanceAttemptId("attempt-1") } returns 0
        val record = record().apply {
            headers().add(RecordHeader(
                KafkaHeaders.DLT_EXCEPTION_FQCN,
                "org.springframework.kafka.support.serializer.DeserializationException".toByteArray(),
            ))
        }
        val saved = slot<IssuanceDltLog>()

        service.record(record)

        verify { repository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.QUARANTINED, saved.captured.status)
        assertEquals("NON_RETRYABLE_ERROR", saved.captured.decisionReason)
    }

    @Test
    fun `본문을 읽을 수 없어도 DLT 로그에 격리`() {
        val malformed = ConsumerRecord("issuance.requested.DLT", 0, 11L, "42", "{".toByteArray())
        val saved = slot<IssuanceDltLog>()

        service.record(malformed)

        verify { repository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.QUARANTINED, saved.captured.status)
        assertEquals("INVALID_PAYLOAD", saved.captured.decisionReason)
        assertEquals(null, saved.captured.issuanceAttemptId)
    }

    @Test
    fun `replay 후 세 번 다시 실패하면 격리`() {
        every { repository.countByIssuanceAttemptId("attempt-1") } returns 3
        val saved = slot<IssuanceDltLog>()

        service.record(record())

        verify { repository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.QUARANTINED, saved.captured.status)
        assertEquals("REPLAY_LIMIT_EXCEEDED", saved.captured.decisionReason)
    }

    @Test
    fun `운영자 replay는 대기 중인 로그를 최대 백 건 재발행`() {
        val log = log(status = IssuanceDltStatus.PENDING)
        every {
            repository.findTop100ByStatusOrderByReceivedAtAsc(IssuanceDltStatus.PENDING)
        } returns listOf(log)
        every { producer.publishAndWait(any()) } just runs

        val processedCount = service.replayPending()

        assertEquals(1, processedCount)
        assertEquals(IssuanceDltStatus.REPLAYED, log.status)
        verify(exactly = 1) { producer.publishAndWait(any()) }
    }

    @Test
    fun `대기 중인 로그를 보상하면 정책 취소로 기록`() {
        val log = log(status = IssuanceDltStatus.PENDING)
        every { repository.findByIdForUpdate(1L) } returns log
        every { compensationService.compensate(any()) } returns true

        val result = service.compensate(1L)

        assertEquals(IssuanceDltStatus.COMPENSATED, result.status)
        assertEquals("POLICY_CANCELED", result.decisionReason)
    }

    @Test
    fun `발급 식별자가 없는 로그는 보상하지 않음`() {
        val log = log(status = IssuanceDltStatus.QUARANTINED).apply {
            couponId = null
            userId = null
            issuanceAttemptId = null
        }
        every { repository.findByIdForUpdate(1L) } returns log

        assertFailsWith<IllegalStateException> { service.compensate(1L) }

        verify(exactly = 0) { compensationService.compensate(any()) }
    }

    private fun record() = ConsumerRecord(
        "issuance.requested.DLT",
        0,
        10L,
        "42",
        """{"couponId":1,"userId":42,"issuanceAttemptId":"attempt-1","issuedAt":"2026-07-15T00:00:00","expiresAt":"2026-07-22T00:00:00"}"""
            .toByteArray(),
    )

    private fun log(status: IssuanceDltStatus) = IssuanceDltLog(
        messageKey = "issuance.requested.DLT:0:10",
        dltPartition = 0,
        dltOffset = 10L,
        couponId = 1L,
        userId = 42L,
        issuanceAttemptId = "attempt-1",
        issuedAt = LocalDateTime.now(),
        expiresAt = LocalDateTime.now().plusDays(7),
        status = status,
        receivedAt = LocalDateTime.now(),
        id = 1L,
    )
}
