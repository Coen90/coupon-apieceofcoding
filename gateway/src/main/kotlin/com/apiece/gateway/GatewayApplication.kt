package com.apiece.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Mono

@SpringBootApplication
class GatewayApplication {

    @Bean
    fun userIdKeyResolver(): KeyResolver = KeyResolver { exchange ->
        // 강의에서는 기존 API의 사용자 식별 헤더를 사용한다. 운영에서는 인증된 principal을 키로 삼는다.
        val userId = exchange.request.headers.getFirst("X-User-Id")
        if (userId.isNullOrBlank()) Mono.empty() else Mono.just("user:$userId")
    }
}

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
