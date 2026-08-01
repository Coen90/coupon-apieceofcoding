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
import kotlin.test.assertFailsWith

class IssuanceDltReplayServiceTest {
    private val issuanceDltLogRepository = mockk<IssuanceDltLogRepository>()
    private val issuanceRequestProducer = mockk<IssuanceRequestProducer>()
    private val service = IssuanceDltReplayService(issuanceDltLogRepository, issuanceRequestProducer)

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

    @Test
    fun `원본 토픽 발행 실패 시 replay 완료로 바꾸지 않음`() {
        val pending = log(1L, IssuanceDltStatus.PENDING)
        every { issuanceDltLogRepository.findAllByIdForUpdate(listOf(1L)) } returns listOf(pending)
        every { issuanceRequestProducer.publishAndWait(any()) } throws IllegalStateException("publish failed")

        assertFailsWith<IllegalStateException> { service.replay(listOf(1L)) }

        assertEquals(IssuanceDltStatus.PENDING, pending.status)
    }

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
