package com.apiece.coupon.application

import com.apiece.coupon.domain.IssuanceDltLog
import com.apiece.coupon.domain.IssuanceDltLogRepository
import com.apiece.coupon.domain.IssuanceDltStatus
import com.apiece.coupon.infrastructure.messaging.IssuanceRequestProducer
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class IssuanceDltReplayServiceTest {
    private val issuanceDltLogRepository = mockk<IssuanceDltLogRepository>()
    private val issuanceRequestProducer = mockk<IssuanceRequestProducer>()
    private val service = IssuanceDltReplayService(issuanceDltLogRepository, issuanceRequestProducer)

    @Test
    fun `선택한 대기 메시지를 원본 토픽에 재발행`() {
        val first = log(1L)
        val second = log(2L)
        every { issuanceDltLogRepository.findAllByIdForUpdate(listOf(1L, 2L)) } returns listOf(first, second)
        every { issuanceRequestProducer.publishAndWait(any()) } just runs

        val replayedCount = service.replay(listOf(1L, 2L))

        assertEquals(2, replayedCount)
        assertEquals(IssuanceDltStatus.REPLAYED, first.status)
        assertEquals(IssuanceDltStatus.REPLAYED, second.status)
        verify(exactly = 2) { issuanceRequestProducer.publishAndWait(any()) }
    }

    @Test
    fun `본문을 복원할 수 없으면 재발행하지 않음`() {
        val invalid = log(1L, payload = "{")
        every { issuanceDltLogRepository.findAllByIdForUpdate(listOf(1L)) } returns listOf(invalid)

        assertFails { service.replay(listOf(1L)) }

        assertEquals(IssuanceDltStatus.PENDING, invalid.status)
        verify(exactly = 0) { issuanceRequestProducer.publishAndWait(any()) }
    }

    @Test
    fun `이미 재발행한 메시지는 다시 보내지 않음`() {
        val replayed = log(1L, status = IssuanceDltStatus.REPLAYED)
        every { issuanceDltLogRepository.findAllByIdForUpdate(listOf(1L)) } returns listOf(replayed)

        assertFailsWith<IllegalStateException> { service.replay(listOf(1L)) }

        verify(exactly = 0) { issuanceRequestProducer.publishAndWait(any()) }
    }

    @Test
    fun `원본 토픽 발행 실패 시 replay 완료로 바꾸지 않음`() {
        val pending = log(1L)
        every { issuanceDltLogRepository.findAllByIdForUpdate(listOf(1L)) } returns listOf(pending)
        every { issuanceRequestProducer.publishAndWait(any()) } throws IllegalStateException("publish failed")

        assertFailsWith<IllegalStateException> { service.replay(listOf(1L)) }

        assertEquals(IssuanceDltStatus.PENDING, pending.status)
    }

    private fun log(
        id: Long,
        status: IssuanceDltStatus = IssuanceDltStatus.PENDING,
        payload: String = VALID_PAYLOAD,
    ) = IssuanceDltLog(
        messageKey = "issuance.requested.DLT:0:$id",
        payload = payload,
        status = status,
        receivedAt = LocalDateTime.now(),
        id = id,
    )

    private companion object {
        const val VALID_PAYLOAD =
            """{"couponId":1,"userId":42,"issuedAt":"2026-07-15T00:00:00","expiresAt":"2026-07-22T00:00:00"}"""
    }
}
