package com.apiece.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Mono

@SpringBootApplication
class GatewayApplication {

    // 한도 키는 신뢰 가능한 클라이언트 IP. 호출자가 넣는 헤더(X-User-Id 등)는 위조로 우회/표적 DoS 가 된다.
    // maxTrustedIndex(1) = 프록시 1대만 신뢰해 X-Forwarded-For 위조를 막는다 (없으면 소켓 IP 폴백).
    private val clientAddressResolver = XForwardedRemoteAddressResolver.maxTrustedIndex(1)

    @Bean
    fun clientIpKeyResolver(): KeyResolver = KeyResolver { exchange ->
        val ip = clientAddressResolver.resolve(exchange)?.address?.hostAddress ?: "unknown"
        Mono.just("ip:$ip")
    }
}

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
