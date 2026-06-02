package com.apiece.coupon.infrastructure.messaging

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

// 보상 처리기 전용 팩토리. 기본 팩토리는 실패를 ".DLT" 로 넘겨 "...DLT.DLT" 를 만들므로 쓰지 않는다.
@Configuration
class CompensatorKafkaConfig {

    @Bean
    fun compensatorListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Any>,
    ): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.setConsumerFactory(consumerFactory)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        // 성공할 때까지 무한 재시도. 유한이면 소진 후 offset 이 commit 돼 보상이 무음 폐기된다 (멱등 키로 재시도 안전).
        factory.setCommonErrorHandler(DefaultErrorHandler(FixedBackOff(2_000L, Long.MAX_VALUE)))
        return factory
    }
}
