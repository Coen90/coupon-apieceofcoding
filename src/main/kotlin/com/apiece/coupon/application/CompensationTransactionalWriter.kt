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

// 보상의 DB 단계 (5단원 4.3, 한 트랜잭션). catch 를 두지 않아 compensation_log PK
// 충돌(동시 같은 compensationId)이 예외로 escape 되고, Spring 이 자동 rollback 한다.
// 멱등 처리는 호출자(CompensationService)에서 (IssuanceTransactionalWriter 와 같은 패턴).
@Component
class CompensationTransactionalWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
    private val compensationLogRepository: CompensationLogRepository,
) {

    // 반환: true = 이미 처리된 보상(멱등 hit), false = 이번에 실제로 되돌림.
    @Transactional
    fun applyDbStep(command: CompensationCommand): Boolean {
        if (compensationLogRepository.existsById(command.compensationId)) {
            return true
        }

        val now = LocalDateTime.now()
        val existing = issuanceRepository.findByUserIdAndCouponId(command.userId, command.couponId)
        val issuanceId: Long = if (existing != null) {
            // 일반 보상: ISSUED 행을 CANCELED 로 전이하고 누적 카운터 -1.
            // 이미 ISSUED 가 아니면(USED/EXPIRED/CANCELED) 카운터는 건드리지 않는다.
            if (existing.status == IssuanceStatus.ISSUED) {
                existing.markCanceled()
                couponRepository.decrementIssuedQuantity(command.couponId)
            }
            existing.id!!
        } else {
            // Worker INSERT 가 실패해 행이 없는 케이스: 사후 기록(status=CANCELED).
            // issued_quantity 는 손대지 않는다 (원래 +1 된 적이 없으므로).
            val saved = issuanceRepository.save(
                Issuance(
                    userId = command.userId,
                    couponId = command.couponId,
                    issuedAt = command.issuedAt ?: now,
                    expiresAt = command.expiresAt ?: now,
                    status = IssuanceStatus.CANCELED,
                )
            )
            saved.id!!
        }

        compensationLogRepository.save(
            CompensationLog(
                id = command.compensationId,
                issuanceId = issuanceId,
                compensatedAt = now,
                reason = command.reason.name,
            )
        )
        return false
    }
}
