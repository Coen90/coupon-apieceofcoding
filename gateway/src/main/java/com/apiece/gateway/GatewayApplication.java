package com.apiece.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    // 한도 키는 "클라이언트 IP". 호출자가 직접 넣는 헤더(X-User-Id 등)를 키로 쓰면
    // 값만 바꿔 우회하거나 남의 값을 위장(표적 DoS)할 수 있다.
    // 신뢰 IP 는 앞 프록시/LB 가 채운 X-Forwarded-For 의 마지막 홉에서 얻는다.
    // maxTrustedIndex(1) = 프록시 1대만 신뢰 (XFF 위조 무력화, 없으면 소켓 IP 폴백).
    private final RemoteAddressResolver clientAddressResolver = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    @Bean
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            InetSocketAddress address = clientAddressResolver.resolve(exchange);
            String ip = address != null ? address.getAddress().getHostAddress() : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
