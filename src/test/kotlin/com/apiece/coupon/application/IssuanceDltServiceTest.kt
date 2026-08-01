package com.apiece.coupon.application

import com.apiece.coupon.domain.IssuanceDltLog
import com.apiece.coupon.domain.IssuanceDltLogRepository
import com.apiece.coupon.domain.IssuanceDltStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.KafkaHeaders
import kotlin.test.assertEquals

class IssuanceDltServiceTest {
    private val issuanceDltLogRepository = mockk<IssuanceDltLogRepository>(relaxed = true)
    private val service = IssuanceDltService(issuanceDltLogRepository)

    init {
        every { issuanceDltLogRepository.existsByMessageKey(any()) } returns false
        every { issuanceDltLogRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `DLT 메시지 원문과 오류 메시지를 판단 없이 기록`() {
        val record = record(VALID_PAYLOAD).apply {
            headers().add(RecordHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "MySQL connection failed".toByteArray()))
        }
        val saved = slot<IssuanceDltLog>()

        service.record(record)

        verify { issuanceDltLogRepository.save(capture(saved)) }
        assertEquals("issuance.requested.DLT:0:10", saved.captured.messageKey)
        assertEquals(VALID_PAYLOAD, saved.captured.payload)
        assertEquals("MySQL connection failed", saved.captured.errorMessage)
        assertEquals(IssuanceDltStatus.PENDING, saved.captured.status)
    }

    @Test
    fun `본문을 읽을 수 없어도 원문 그대로 기록`() {
        val saved = slot<IssuanceDltLog>()

        service.record(record("{"))

        verify { issuanceDltLogRepository.save(capture(saved)) }
        assertEquals("{", saved.captured.payload)
        assertEquals(IssuanceDltStatus.PENDING, saved.captured.status)
    }

    @Test
    fun `이미 기록한 Kafka 레코드는 다시 저장하지 않음`() {
        every { issuanceDltLogRepository.existsByMessageKey("issuance.requested.DLT:0:10") } returns true

        service.record(record(VALID_PAYLOAD))

        verify(exactly = 0) { issuanceDltLogRepository.save(any()) }
    }

    private fun record(payload: String) = ConsumerRecord(
        "issuance.requested.DLT",
        0,
        10L,
        "42",
        payload.toByteArray(),
    )

    private companion object {
        const val VALID_PAYLOAD =
            """{"couponId":1,"userId":42,"issuedAt":"2026-07-15T00:00:00","expiresAt":"2026-07-22T00:00:00"}"""
    }
}
