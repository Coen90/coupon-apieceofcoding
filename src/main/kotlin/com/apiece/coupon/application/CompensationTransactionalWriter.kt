package com.apiece.coupon.application

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.Issuance
import com.apiece.coupon.domain.IssuanceHistory
import com.apiece.coupon.domain.IssuanceHistoryRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.domain.IssuanceStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 보상의 DB 단계. issuance 최신 상태와 Redis Lua 키가 중복 실행을 막는다.
@Component
class CompensationTransactionalWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
    private val issuanceHistoryRepository: IssuanceHistoryRepository,
) {

    @Transactional
    fun applyDbStep(command: CompensationCommand) {
        val now = LocalDateTime.now()
        val existing = issuanceRepository.findByIssuanceAttemptId(command.issuanceAttemptId)
        if (existing != null) {
            if (existing.status == IssuanceStatus.ISSUED) {
                existing.markCanceled()
                couponRepository.decrementIssuedQuantity(command.couponId)
                issuanceHistoryRepository.save(IssuanceHistory(
                    issuanceAttemptId = command.issuanceAttemptId,
                    userId = command.userId,
                    couponId = command.couponId,
                    status = IssuanceStatus.CANCELED,
                    reason = command.reason.name,
                    recordedAt = now,
                ))
            }
        } else {
            val current = issuanceRepository.findByUserIdAndCouponId(command.userId, command.couponId)
            // 더 최신 발급이 이미 있으면 오래된 보상은 현재 상태를 건드리지 않는다.
            if (current != null) return

            // Worker INSERT가 실패해 행이 없는 케이스: CANCELED로 사후 기록한다.
            issuanceRepository.save(
                Issuance(
                    userId = command.userId,
                    couponId = command.couponId,
                    issuanceAttemptId = command.issuanceAttemptId,
                    issuedAt = command.issuedAt ?: now,
                    expiresAt = command.expiresAt ?: now,
                    status = IssuanceStatus.CANCELED,
                ),
            )
            issuanceHistoryRepository.save(IssuanceHistory(
                issuanceAttemptId = command.issuanceAttemptId,
                userId = command.userId,
                couponId = command.couponId,
                status = IssuanceStatus.CANCELED,
                reason = command.reason.name,
                recordedAt = now,
            ))
        }
    }
}
