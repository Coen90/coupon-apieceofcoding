package com.apiece.gateway

import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GatewayApplicationTest {

    private val keyResolver = GatewayApplication().userIdKeyResolver()

    @Test
    fun `양의 정수 사용자 ID를 Rate Limit 키로 사용한다`() {
        assertEquals("user:42", resolve("42"))
    }

    @Test
    fun `유효하지 않은 사용자 ID는 거부한다`() {
        assertNull(resolve())
        assertNull(resolve("0"))
        assertNull(resolve("not-a-number"))
        assertNull(resolve("1", "2"))
    }

    private fun resolve(vararg userIds: String): String? {
        val request = MockServerHttpRequest.get("/")
            .apply { if (userIds.isNotEmpty()) header("X-User-Id", *userIds) }
            .build()
        return keyResolver.resolve(MockServerWebExchange.from(request)).block()
    }
}
