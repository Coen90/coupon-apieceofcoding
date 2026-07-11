package com.apiece.coupon.application

import com.apiece.coupon.domain.CompensationReason
import com.apiece.coupon.infrastructure.cache.CompensationRedisRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
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

    private fun command(id: String = "operation-1") = CompensationCommand(
        couponId = 1L,
        userId = 42L,
        operationId = id,
        reason = CompensationReason.DLT_REPLAY,
    )

    @Test
    fun `처음 보는 보상이면 Redis 역연산 실행 (Lua 1 반환) + compensation_total 증가`() {
        every { writer.applyDbStep(any()) } just Runs
        every { redis.compensate(1L, 42L, "operation-1") } returns 1L // 실제 보상

        val result = service.compensate(command())

        assertTrue(result)
        verify(exactly = 1) { redis.compensate(1L, 42L, "operation-1") }
        verify(exactly = 1) { metrics.incrementCompensated() }
        verify(exactly = 0) { metrics.incrementIdempotentHit() }
    }

    @Test
    fun `진짜 중복이면 DB 멱등 hit + Lua 가 0 반환 (Redis 는 호출하되 멱등 hit)`() {
        every { writer.applyDbStep(any()) } just Runs
        every { redis.compensate(any(), any(), any()) } returns 0L // Lua 2차 멱등 키가 흡수

        val result = service.compensate(command())

        assertFalse(result)
        verify(exactly = 1) { redis.compensate(1L, 42L, "operation-1") } // skip 하지 않는다
        verify(exactly = 1) { metrics.incrementIdempotentHit() }
        verify(exactly = 0) { metrics.incrementCompensated() }
    }

    @Test
    fun `DB 만 커밋되고 Redis 가 안 됐던 부분 실패는 재시도 시 Redis 가 1 반환해 완결`() {
        // DB 는 이미 처리됨(멱등)이지만 Redis 가 아직 반영 안 됨 -> Lua NX 키가 없어 1 반환.
        every { writer.applyDbStep(any()) } just Runs
        every { redis.compensate(any(), any(), any()) } returns 1L

        val result = service.compensate(command())

        assertTrue(result) // DB 멱등이어도 Redis 가 비로소 실행됨
        verify(exactly = 1) { redis.compensate(1L, 42L, "operation-1") }
        verify(exactly = 1) { metrics.incrementCompensated() }
    }

    @Test
    fun `동시 INSERT 로 PK 충돌나도 Redis 단계로 진행 (Lua 가 0 흡수)`() {
        every { writer.applyDbStep(any()) } throws DataIntegrityViolationException("dup pk")
        every { redis.compensate(any(), any(), any()) } returns 0L

        val result = service.compensate(command())

        assertFalse(result)
        verify(exactly = 1) { redis.compensate(1L, 42L, "operation-1") }
        verify(exactly = 1) { metrics.incrementIdempotentHit() }
    }
}
