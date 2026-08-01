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
    private val issuanceDltLogRepository = mockk<IssuanceDltLogRepository>(relaxed = true)
    private val issuanceRequestProducer = mockk<IssuanceRequestProducer>(relaxed = true)
    private val service = IssuanceDltService(issuanceDltLogRepository, issuanceRequestProducer)

    init {
        every { issuanceDltLogRepository.existsByMessageKey(any()) } returns false
        every { issuanceDltLogRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `일시 장애는 운영자 replay 대기 상태로 기록`() {
        every { issuanceDltLogRepository.countByUserIdAndCouponId(42L, 1L) } returns 0
        val saved = slot<IssuanceDltLog>()

        service.record(record())

        verify { issuanceDltLogRepository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.PENDING, saved.captured.status)
        assertEquals(0, saved.captured.retryCount)
    }

    @Test
    fun `데이터 오류는 즉시 확인 필요 상태`() {
        every { issuanceDltLogRepository.countByUserIdAndCouponId(42L, 1L) } returns 0
        val record = record().apply {
            headers().add(RecordHeader(
                KafkaHeaders.DLT_EXCEPTION_FQCN,
                "org.springframework.kafka.support.serializer.DeserializationException".toByteArray(),
            ))
        }
        val saved = slot<IssuanceDltLog>()

        service.record(record)

        verify { issuanceDltLogRepository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.REVIEW_REQUIRED, saved.captured.status)
        assertEquals("NON_RETRYABLE_ERROR", saved.captured.decisionReason)
    }

    @Test
    fun `본문을 읽을 수 없어도 DLT 로그에 확인 필요 상태`() {
        val malformed = ConsumerRecord("issuance.requested.DLT", 0, 11L, "42", "{".toByteArray())
        val saved = slot<IssuanceDltLog>()

        service.record(malformed)

        verify { issuanceDltLogRepository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.REVIEW_REQUIRED, saved.captured.status)
        assertEquals("INVALID_PAYLOAD", saved.captured.decisionReason)
        assertEquals(null, saved.captured.couponId)
    }

    @Test
    fun `식별자가 빠진 본문은 확인 필요 상태로 기록`() {
        val invalid = ConsumerRecord(
            "issuance.requested.DLT",
            0,
            12L,
            "42",
            """{"couponId":1,"userId":0,"issuedAt":"2026-07-15T00:00:00","expiresAt":"2026-07-22T00:00:00"}"""
                .toByteArray(),
        )
        val saved = slot<IssuanceDltLog>()

        service.record(invalid)

        verify { issuanceDltLogRepository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.REVIEW_REQUIRED, saved.captured.status)
        assertEquals("INVALID_PAYLOAD", saved.captured.decisionReason)
    }

    @Test
    fun `replay 후 세 번 다시 실패하면 확인 필요 상태`() {
        every { issuanceDltLogRepository.countByUserIdAndCouponId(42L, 1L) } returns 3
        val saved = slot<IssuanceDltLog>()

        service.record(record())

        verify { issuanceDltLogRepository.save(capture(saved)) }
        assertEquals(IssuanceDltStatus.REVIEW_REQUIRED, saved.captured.status)
        assertEquals("REPLAY_LIMIT_EXCEEDED", saved.captured.decisionReason)
    }

    @Test
    fun `선택한 대기와 확인 필요 로그를 원본 토픽에 재발행`() {
        val pending = log(1L, IssuanceDltStatus.PENDING)
        val reviewRequired = log(2L, IssuanceDltStatus.REVIEW_REQUIRED)
        every { issuanceDltLogRepository.findAllByIdForUpdate(listOf(1L, 2L)) } returns listOf(pending, reviewRequired)
        every { issuanceRequestProducer.publishAndWait(any()) } just runs

        val replayedCount = service.replay(listOf(1L, 2L))

        assertEquals(2, replayedCount)
        assertEquals(IssuanceDltStatus.REPLAYED, pending.status)
        assertEquals(IssuanceDltStatus.REPLAYED, reviewRequired.status)
        verify(exactly = 2) { issuanceRequestProducer.publishAndWait(any()) }
    }

    @Test
    fun `발급 정보를 복원할 수 없는 확인 필요 로그는 재발행하지 않음`() {
        val invalid = log(1L, IssuanceDltStatus.REVIEW_REQUIRED).apply {
            couponId = null
            userId = null
        }
        every { issuanceDltLogRepository.findAllByIdForUpdate(listOf(1L)) } returns listOf(invalid)

        assertFailsWith<IllegalArgumentException> { service.replay(listOf(1L)) }

        verify(exactly = 0) { issuanceRequestProducer.publishAndWait(any()) }
    }

    private fun record() = ConsumerRecord(
        "issuance.requested.DLT",
        0,
        10L,
        "42",
        """{"couponId":1,"userId":42,"issuedAt":"2026-07-15T00:00:00","expiresAt":"2026-07-22T00:00:00"}"""
            .toByteArray(),
    )

    private fun log(id: Long, status: IssuanceDltStatus) = IssuanceDltLog(
        messageKey = "issuance.requested.DLT:0:$id",
        couponId = 1L,
        userId = 42L,
        issuedAt = LocalDateTime.now(),
        expiresAt = LocalDateTime.now().plusDays(7),
        status = status,
        receivedAt = LocalDateTime.now(),
        id = id,
    )
}
