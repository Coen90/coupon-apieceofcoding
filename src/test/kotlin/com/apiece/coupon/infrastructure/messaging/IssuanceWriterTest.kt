package com.apiece.coupon.infrastructure.messaging

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

class IssuanceWriterTest {
    private val transactional = mockk<IssuanceTransactionalWriter>()
    private val writer = IssuanceWriter(transactional)
    private val event = IssuanceRequested(
        couponId = 1L,
        userId = 42L,
        issuedAt = LocalDateTime.now(),
        expiresAt = LocalDateTime.now().plusDays(7),
    )

    @Test
    fun `같은 발급 시도가 이미 저장됐으면 멱등 처리`() {
        every { transactional.insertAndIncrement(event) } throws DataIntegrityViolationException("duplicate")
        every { transactional.isAlreadyApplied(event) } returns true

        writer.write(event)
    }

    @Test
    fun `다른 DB 제약 오류는 DLT로 가도록 다시 던짐`() {
        every { transactional.insertAndIncrement(event) } throws DataIntegrityViolationException("constraint")
        every { transactional.isAlreadyApplied(event) } returns false

        assertFailsWith<DataIntegrityViolationException> { writer.write(event) }
    }
}
