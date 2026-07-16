package com.apiece.coupon.application

import com.apiece.coupon.infrastructure.cache.CompensationRedisRepository
import org.springframework.stereotype.Service

// DB 단계 먼저, Redis 역연산 다음 (역순이면 부정 발급).
@Service
class CompensationService(
    private val transactionalWriter: CompensationTransactionalWriter,
    private val compensationRedisRepository: CompensationRedisRepository,
    private val metrics: CompensationMetrics,
) {

    fun compensate(command: CompensationCommand): Boolean {
        // DB가 실패하면 Redis를 되돌리지 않는다. 호출자는 같은 DLT 로그로 다시 시도한다.
        transactionalWriter.applyDbStep(command)

        // DB 멱등이어도 Redis 는 항상 호출한다. 부분 실패(DB 만 커밋)를 재시도 때 완결하기 위함이며,
        // Lua 의 2차 멱등 키가 진짜 중복은 0 으로 흡수한다.
        val result = compensationRedisRepository.compensate(
            command.couponId, command.userId, command.issuanceAttemptId,
        )
        return when (result) {
            1L -> {
                metrics.incrementCompensated()
                true
            }
            0L -> {
                metrics.incrementIdempotentHit()
                false
            }
            else -> error("Current issuance does not match: ${command.issuanceAttemptId}")
        }
    }
}
