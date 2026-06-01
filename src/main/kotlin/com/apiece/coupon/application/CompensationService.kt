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
        // DB 단계: 한 트랜잭션 안에서 멱등 처리(이미 있으면 손대지 않음)하고 되돌린다.
        // compensation_log PK 충돌(동시에 같은 compensationId)은 rollback 되며, 아래 Redis
        // 단계로 그대로 진행한다 (Lua 의 2차 멱등 키가 중복을 흡수).
        try {
            transactionalWriter.applyDbStep(command)
        } catch (e: DataIntegrityViolationException) {
            log.debug { "compensation_log PK 충돌 (동시 보상), Redis 단계로 진행: ${command.compensationId}" }
        }

        // Redis 역연산은 DB 멱등 여부와 무관하게 항상 호출한다. Lua 의 2차 멱등 키(SET NX)와
        // SISMEMBER 가드가 진짜 중복은 0 으로 흡수하고, "DB 만 커밋되고 Redis 만 재시도되는"
        // 부분 실패에서는 여기서 비로소 재고 +1/SREM/매진 해제가 완결된다 (5단원 4.3).
        // 반환: 1=실제 보상, 0=이미 처리됨(멱등 hit) 또는 목록에 없음.
        val reversed = compensationRedisRepository.compensate(
            command.couponId, command.userId, command.compensationId,
        )
        return if (reversed == 1L) {
            metrics.incrementCompensated()
            CompensationResult(command.compensationId, compensated = true)
        } else {
            metrics.incrementIdempotentHit()
            CompensationResult(command.compensationId, compensated = false)
        }
    }
}
