package com.coen.coupon.application

import com.coen.coupon.domain.Issuance
import com.coen.coupon.domain.IssuanceRepository
import com.coen.coupon.domain.IssuanceStatus
import com.coen.coupon.support.AlreadyUsedException
import com.coen.coupon.support.ExpiredException
import com.coen.coupon.support.IssuanceNotFoundException
import com.coen.coupon.support.NotOwnerException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class IssuanceService(
    private val issuanceRepository: IssuanceRepository,
) {

    @Transactional
    fun use(issuanceId: Long, userId: Long): Issuance {
        val issuance = issuanceRepository.findById(issuanceId)
            .orElseThrow { IssuanceNotFoundException() }

        if (issuance.userId != userId) {
            throw NotOwnerException()
        }

        when (issuance.status) {
            IssuanceStatus.USED -> throw AlreadyUsedException()
            IssuanceStatus.EXPIRED -> throw ExpiredException()
            IssuanceStatus.ISSUED -> Unit
        }

        val now = LocalDateTime.now()
        if (issuance.isExpired(now)) {
            throw ExpiredException()
        }

        issuance.markUsed(now)
        return issuance
    }

    @Transactional(readOnly = true)
    fun findByUser(userId: Long): List<Issuance> =
        issuanceRepository.findByUserIdOrderByIssuedAtDesc(userId)
}