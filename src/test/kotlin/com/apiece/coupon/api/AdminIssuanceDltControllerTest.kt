package com.apiece.coupon.api

import com.apiece.coupon.application.IssuanceDltReplayService
import com.apiece.coupon.application.IssuanceDltService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AdminIssuanceDltControllerTest {

    private val issuanceDltService = mockk<IssuanceDltService>()
    private val issuanceDltReplayService = mockk<IssuanceDltReplayService>()
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(AdminIssuanceDltController(issuanceDltService, issuanceDltReplayService))
        .build()

    @Test
    fun `선택한 DLT 로그 ID를 replay한다`() {
        every { issuanceDltReplayService.replay(listOf(7L, 8L)) } returns 2

        mockMvc.perform(
            post("/admin/issuance/dlt/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[7,8]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayedCount").value(2))

        verify(exactly = 1) { issuanceDltReplayService.replay(listOf(7L, 8L)) }
    }
}
