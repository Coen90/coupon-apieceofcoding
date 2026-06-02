package com.apiece.coupon.application

import com.apiece.coupon.domain.CompensationLog
import com.apiece.coupon.domain.CompensationLogRepository
import com.apiece.coupon.domain.CompensationReason
import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.Issuance
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.domain.IssuanceStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class CompensationTransactionalWriterTest {

    private val issuanceRepository = mockk<IssuanceRepository>(relaxed = true)
    private val couponRepository = mockk<CouponRepository>(relaxed = true)
    private val compensationLogRepository = mockk<CompensationLogRepository>(relaxed = true)
    private val writer = CompensationTransactionalWriter(
        issuanceRepository, couponRepository, compensationLogRepository,
    )

    init {
        // 제네릭 save(S): S 는 relaxed 가 Object 를 돌려줘 캐스팅에서 터지므로 인자를 그대로 반환.
        every { compensationLogRepository.save(any()) } answers { firstArg() }
    }

    private fun command(id: String = "c1") = CompensationCommand(
        couponId = 1L,
        userId = 42L,
        compensationId = id,
        reason = CompensationReason.DLT_REPLAY,
        issuedAt = LocalDateTime.now(),
        expiresAt = LocalDateTime.now().plusDays(7),
    )

    @Test
    fun `compensation_log 에 이미 있으면 DB 손대지 않음`() {
        every { compensationLogRepository.existsById("c1") } returns true

        writer.applyDbStep(command())

        verify(exactly = 0) { issuanceRepository.save(any()) }
        verify(exactly = 0) { couponRepository.decrementIssuedQuantity(any()) }
        verify(exactly = 0) { compensationLogRepository.save(any()) }
    }

    @Test
    fun `기존 ISSUED 행 있으면 CANCELED 전이 + issued_quantity 감소`() {
        val issued = Issuance(
            userId = 42L, couponId = 1L,
            issuedAt = LocalDateTime.now(), expiresAt = LocalDateTime.now().plusDays(7),
            status = IssuanceStatus.ISSUED, id = 7L,
        )
        every { compensationLogRepository.existsById("c1") } returns false
        every { issuanceRepository.findByUserIdAndCouponId(42L, 1L) } returns issued

        writer.applyDbStep(command())

        assertEquals(IssuanceStatus.CANCELED, issued.status)
        verify(exactly = 1) { couponRepository.decrementIssuedQuantity(1L) }
        verify(exactly = 0) { issuanceRepository.save(any()) } // 기존 행은 dirty checking
        val logSlot = slot<CompensationLog>()
        verify { compensationLogRepository.save(capture(logSlot)) }
        assertEquals("c1", logSlot.captured.id)
        assertEquals(7L, logSlot.captured.issuanceId)
    }

    @Test
    fun `기존 행 없으면 CANCELED 행 사후 INSERT + issued_quantity 손대지 않음`() {
        every { compensationLogRepository.existsById("c1") } returns false
        every { issuanceRepository.findByUserIdAndCouponId(42L, 1L) } returns null
        val saveSlot = slot<Issuance>()
        every { issuanceRepository.save(capture(saveSlot)) } answers { saveSlot.captured.apply { id = 99L } }

        writer.applyDbStep(command())

        assertEquals(IssuanceStatus.CANCELED, saveSlot.captured.status)
        verify(exactly = 0) { couponRepository.decrementIssuedQuantity(any()) }
        verify { compensationLogRepository.save(any()) }
    }

    @Test
    fun `기존 행이 ISSUED 가 아니면 카운터 감소 없이 보상 기록만`() {
        val used = Issuance(
            userId = 42L, couponId = 1L,
            issuedAt = LocalDateTime.now(), expiresAt = LocalDateTime.now().plusDays(7),
            status = IssuanceStatus.USED, id = 8L,
        )
        every { compensationLogRepository.existsById("c1") } returns false
        every { issuanceRepository.findByUserIdAndCouponId(42L, 1L) } returns used

        writer.applyDbStep(command())

        assertEquals(IssuanceStatus.USED, used.status) // 전이 안 함
        verify(exactly = 0) { couponRepository.decrementIssuedQuantity(any()) }
        verify { compensationLogRepository.save(any()) }
    }
}
