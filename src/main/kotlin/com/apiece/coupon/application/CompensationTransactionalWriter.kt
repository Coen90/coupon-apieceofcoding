package com.apiece.coupon.application

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.Issuance
import com.apiece.coupon.domain.IssuanceHistory
import com.apiece.coupon.domain.IssuanceHistoryRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.domain.IssuanceStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
import java.time.LocalDateTime

// 보상의 DB 단계. issuance 최신 상태와 Redis Lua 키가 중복 실행을 막는다.
@Component
class CompensationTransactionalWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
    private val issuanceHistoryRepository: IssuanceHistoryRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun applyDbStep(command: CompensationCommand) {
        val now = LocalDateTime.now()
        val existing = issuanceRepository.findByIssuanceAttemptId(command.issuanceAttemptId)
        if (existing != null) {
            when (existing.status) {
                IssuanceStatus.ISSUED -> {
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
                IssuanceStatus.CANCELED -> return
                IssuanceStatus.USED, IssuanceStatus.EXPIRED ->
                    error("Used or expired issuance cannot be canceled: ${command.issuanceAttemptId}")
            }
            return
        } else {
            val current = issuanceRepository.findByUserIdAndCouponId(command.userId, command.couponId)
            check(current == null) { "A newer issuance already exists: ${command.issuanceAttemptId}" }

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
