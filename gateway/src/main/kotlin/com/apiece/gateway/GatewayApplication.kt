package com.apiece.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Mono

@SpringBootApplication
class GatewayApplication {

    // 운영에서는 앞단의 신뢰 가능한 프록시가 X-Forwarded-For 를 관리한다는 전제로 클라이언트 IP 를 구한다.
    // 로컬 검증에서는 k6 가 그 프록시 역할을 대신해 서로 다른 클라이언트 IP 를 넣는다.
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
