package com.apiece.coupon.infrastructure.messaging

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

// 보상 처리기(Compensator) 전용 리스너 팩토리. 기본 팩토리는 실패 시 ".DLT" 로 다시 넘기는데,
// 이미 DLT 인 토픽을 그렇게 두면 "...DLT.DLT" 가 생긴다. 여기서는 그 recoverer 없이 백오프
// 재시도만 둔다.
@Configuration
class CompensatorKafkaConfig {

    @Bean
    fun compensatorListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Any>,
    ): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.setConsumerFactory(consumerFactory)
        // 한 건 단위 commit. 직접 만든 팩토리는 yaml 의 ack-mode 를 자동 반영하지 않아 명시한다.
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        // 성공할 때까지 2초 간격으로 무한 재시도한다. 유한 횟수면 소진 후 recoverer 가 offset 을
        // commit 해 보상 메시지를 무음 폐기하는데, 그러면 "끝까지 성공해야 commit" (Compensator)
        // 의도와 어긋나고 reconcile 도 DB 측 drift 를 자동 복구하지 않아 보상이 영구 누락된다.
        // 멱등 키 덕분에 무한 재시도/재투입도 안전하고, 깨진 payload 는 null 로 와서 그냥 skip 된다.
        factory.setCommonErrorHandler(DefaultErrorHandler(FixedBackOff(2_000L, Long.MAX_VALUE)))
        return factory
    }
}
