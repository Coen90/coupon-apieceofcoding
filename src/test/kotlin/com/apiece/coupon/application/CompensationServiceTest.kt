package com.apiece.coupon.application

import com.apiece.coupon.domain.CompensationReason
import com.apiece.coupon.infrastructure.cache.CompensationRedisRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompensationServiceTest {

    private val writer = mockk<CompensationTransactionalWriter>()
    private val redis = mockk<CompensationRedisRepository>(relaxed = true)
    private val metrics = mockk<CompensationMetrics>(relaxUnitFun = true)
    private val service = CompensationService(writer, redis, metrics)

    private fun command(id: String = "dlt:t:0:1") = CompensationCommand(
        couponId = 1L,
        userId = 42L,
        compensationId = id,
        reason = CompensationReason.DLT_REPLAY,
    )

    @Test
    fun `처음 보는 보상이면 Redis 역연산 실행 + compensation_total 증가`() {
        every { writer.applyDbStep(any()) } returns false // 멱등 hit 아님

        val result = service.compensate(command())

        assertTrue(result.compensated)
        verify(exactly = 1) { redis.compensate(1L, 42L, "dlt:t:0:1") }
        verify(exactly = 1) { metrics.incrementCompensated() }
        verify(exactly = 0) { metrics.incrementIdempotentHit() }
    }

    @Test
    fun `compensation_log 에 이미 있으면 Redis 단계 skip (멱등 hit)`() {
        every { writer.applyDbStep(any()) } returns true // 멱등 hit

        val result = service.compensate(command())

        assertFalse(result.compensated)
        verify(exactly = 0) { redis.compensate(any(), any(), any()) }
        verify(exactly = 1) { metrics.incrementIdempotentHit() }
        verify(exactly = 0) { metrics.incrementCompensated() }
    }

    @Test
    fun `동시 INSERT 로 PK 충돌나면 멱등 hit 으로 흡수 (Redis skip)`() {
        every { writer.applyDbStep(any()) } throws DataIntegrityViolationException("dup pk")

        val result = service.compensate(command())

        assertFalse(result.compensated)
        verify(exactly = 0) { redis.compensate(any(), any(), any()) }
        verify(exactly = 1) { metrics.incrementIdempotentHit() }
    }
}
