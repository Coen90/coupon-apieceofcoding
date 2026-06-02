package com.apiece.coupon.application

import com.apiece.coupon.infrastructure.cache.CompensationRedisRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

// DB 단계 먼저, Redis 역연산 다음 (역순이면 부정 발급).
@Service
class CompensationService(
    private val transactionalWriter: CompensationTransactionalWriter,
    private val compensationRedisRepository: CompensationRedisRepository,
    private val metrics: CompensationMetrics,
) {

    fun compensate(command: CompensationCommand): Boolean {
        try {
            transactionalWriter.applyDbStep(command)
        } catch (e: DataIntegrityViolationException) {
            log.debug { "compensation_log PK 충돌, Redis 단계로 진행: ${command.compensationId}" }
        }

        // DB 멱등이어도 Redis 는 항상 호출한다. 부분 실패(DB 만 커밋)를 재시도 때 완결하기 위함이며,
        // Lua 의 2차 멱등 키가 진짜 중복은 0 으로 흡수한다.
        val compensated = compensationRedisRepository.compensate(
            command.couponId, command.userId, command.compensationId,
        ) == 1L
        if (compensated) metrics.incrementCompensated() else metrics.incrementIdempotentHit()
        return compensated
    }
}
