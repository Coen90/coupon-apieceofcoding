package com.apiece.coupon.infrastructure.messaging

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

// 보상 처리기(Compensator) 전용 리스너 팩토리. 기본 팩토리는 실패 시 ".DLT" 로 다시
// 넘기는데, 이미 DLT 인 토픽을 그렇게 두면 "...DLT.DLT" 가 생긴다. 여기서는 그 recoverer
// 없이 백오프 재시도만 둔다. 재시도/재투입으로 같은 메시지가 다시 와도 멱등 키가 흡수한다.
@Configuration
class CompensatorKafkaConfig {

    @Bean
    fun compensatorListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Any>,
    ): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.setConsumerFactory(consumerFactory)
        factory.setCommonErrorHandler(DefaultErrorHandler(FixedBackOff(2_000L, 3L)))
        return factory
    }
}
