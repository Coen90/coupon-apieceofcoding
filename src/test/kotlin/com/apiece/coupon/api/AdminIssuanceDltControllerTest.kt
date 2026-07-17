package com.apiece.coupon.api

import com.apiece.coupon.application.IssuanceDltService
import com.apiece.coupon.domain.IssuanceDltLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class AdminIssuanceDltControllerTest {

    private val service = mockk<IssuanceDltService>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(AdminIssuanceDltController(service)).build()

    @Test
    fun `보상 로그 ID를 요청 본문으로 받는다`() {
        val now = LocalDateTime.of(2026, 7, 17, 12, 0)
        every { service.compensate(7L) } returns IssuanceDltLog(
            messageKey = "issuance-7",
            dltPartition = 0,
            dltOffset = 10L,
            couponId = 1L,
            userId = 2L,
            issuanceAttemptId = "attempt-7",
            issuedAt = now,
            expiresAt = now.plusDays(7),
            receivedAt = now,
            id = 7L,
        )

        mockMvc.perform(
            post("/admin/issuance/dlt/compensate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":7}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))

        verify(exactly = 1) { service.compensate(7L) }
    }
}
