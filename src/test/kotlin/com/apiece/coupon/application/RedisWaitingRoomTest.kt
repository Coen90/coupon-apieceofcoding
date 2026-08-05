package com.apiece.coupon.application

import com.apiece.coupon.infrastructure.cache.WaitingRoomProperties
import com.apiece.coupon.infrastructure.cache.WaitingRoomRedisRepository
import com.apiece.coupon.support.WaitingRoomNotEnteredException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisWaitingRoomTest {

    private val repository = mockk<WaitingRoomRedisRepository>()
    private val metrics = TrafficMetrics()

    private fun room() = RedisWaitingRoom(
        repository,
        WaitingRoomProperties(admitPerSecond = 100, passTtlMs = 30_000),
        metrics,
    )

    @Test
    fun `통과한 사용자는 ADMITTED`() {
        every { repository.enter(1L, 7L) } returns (true to 0L)
        assertTrue(room().enter(1L, 7L).admitted)
    }

    @Test
    fun `대기 사용자는 순번과 예상 대기 시간을 받는다`() {
        every { repository.enter(1L, 7L) } returns (false to 250L)
        val admission = room().enter(1L, 7L)
        assertFalse(admission.admitted)
        assertEquals(250L, admission.position)
        assertEquals(3L, admission.estimatedWaitSeconds) // 250 / 100 올림
    }

    @Test
    fun `상태 조회는 대기열을 다시 등록하지 않고 기존 순번을 반환한다`() {
        every { repository.status(1L, 7L) } returns 250L
        val admission = room().status(1L, 7L)
        assertFalse(admission.admitted)
        assertEquals(250L, admission.position)
        assertEquals(3L, admission.estimatedWaitSeconds)
    }

    @Test
    fun `상태 조회에서 아직 진입하지 않은 사용자는 실패한다`() {
        every { repository.status(1L, 7L) } returns null
        assertFailsWith<WaitingRoomNotEnteredException> { room().status(1L, 7L) }
    }

    @Test
    fun `드레인은 활성 대기실마다 통과 인원만큼 메트릭을 올린다`() {
        every { repository.activeRooms() } returns listOf(1L, 2L)
        every { repository.drain(1L, 100, 30_000L) } returns 100
        every { repository.drain(2L, 100, 30_000L) } returns 40

        room().drain()

        assertEquals(140, metrics.snapshot().admitted)
    }

    @Test
    fun `통과 속도와 입장권 TTL은 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            WaitingRoomProperties(admitPerSecond = 0, passTtlMs = 30_000)
        }
        assertFailsWith<IllegalArgumentException> {
            WaitingRoomProperties(admitPerSecond = 100, passTtlMs = 0)
        }
    }
}
