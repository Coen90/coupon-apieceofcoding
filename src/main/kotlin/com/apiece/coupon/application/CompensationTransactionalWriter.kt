package com.apiece.coupon.application

import com.apiece.coupon.domain.CompensationLog
import com.apiece.coupon.domain.CompensationLogRepository
import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.Issuance
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.domain.IssuanceStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 보상의 DB 단계 (한 트랜잭션). catch 를 두지 않아 compensation_log PK 충돌이 escape 되고 Spring
// 이 rollback 한다. 멱등 처리는 호출자에서 (IssuanceTransactionalWriter 패턴).
@Component
class CompensationTransactionalWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
    private val compensationLogRepository: CompensationLogRepository,
) {

    @Transactional
    fun applyDbStep(command: CompensationCommand) {
        if (compensationLogRepository.existsById(command.issuanceAttemptId)) return

        val now = LocalDateTime.now()
        val existing = issuanceRepository.findByIssuanceAttemptId(command.issuanceAttemptId)
        val issuanceId = if (existing != null) {
            if (existing.status == IssuanceStatus.ISSUED) {
                existing.markCanceled()
                couponRepository.decrementIssuedQuantity(command.couponId)
            }
            existing.id!!
        } else {
            // Worker INSERT 가 실패해 행이 없는 케이스: CANCELED 로 사후 기록 (카운터는 안 건드림).
            issuanceRepository.save(
                Issuance(
                    userId = command.userId,
                    couponId = command.couponId,
                    issuanceAttemptId = command.issuanceAttemptId,
                    issuedAt = command.issuedAt ?: now,
                    expiresAt = command.expiresAt ?: now,
                    status = IssuanceStatus.CANCELED,
                )
            ).id!!
        }

        compensationLogRepository.save(
            CompensationLog(
                id = command.issuanceAttemptId,
                issuanceId = issuanceId,
                compensatedAt = now,
                reason = command.reason.name,
            )
        )
    }
}
