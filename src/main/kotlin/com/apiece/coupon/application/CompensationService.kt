package com.apiece.coupon.application

import com.apiece.coupon.infrastructure.cache.CompensationRedisRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

// 보상 한 건을 두 단계로 실행한다 (5단원 4.3): ① DB 단계(한 트랜잭션), ② Redis 역연산.
// DB 가 먼저라, 반대 순서였다면 생길 부정 발급(Redis SREM 후 DB 실패 -> 재요청 통과)을 막는다.
// 한 쪽이 실패하면 다음 분 reconcile 이 안전망.
@Service
class CompensationService(
    private val transactionalWriter: CompensationTransactionalWriter,
    private val compensationRedisRepository: CompensationRedisRepository,
    private val metrics: CompensationMetrics,
) {

    fun compensate(command: CompensationCommand): CompensationResult {
        val idempotentHit = try {
            transactionalWriter.applyDbStep(command)
        } catch (e: DataIntegrityViolationException) {
            // compensation_log PK 충돌 = 동시에 같은 compensationId 가 들어옴.
            // 트랜잭션은 rollback 됐고, 우리는 멱등 hit 으로 흡수한다.
            log.debug { "compensation_log PK 충돌, 멱등 hit: ${command.compensationId}" }
            true
        }

        if (idempotentHit) {
            metrics.incrementIdempotentHit()
            return CompensationResult(command.compensationId, compensated = false)
        }

        // Redis 역연산 (Lua 안에 2차 멱등 키 + 매진 플래그 조건부 해제).
        compensationRedisRepository.compensate(command.couponId, command.userId, command.compensationId)
        metrics.incrementCompensated()
        return CompensationResult(command.compensationId, compensated = true)
    }
}
