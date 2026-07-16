package com.apiece.coupon.infrastructure.messaging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class IssuanceWriter(
    private val transactional: IssuanceTransactionalWriter,
) {
    fun write(event: IssuanceRequested) {
        try {
            transactional.insertAndIncrement(event)
        } catch (e: DataIntegrityViolationException) {
            if (!transactional.isAlreadyApplied(event)) throw e
            log.debug { "이미 저장된 발급 시도는 멱등 처리: issuanceAttemptId=${event.issuanceAttemptId}" }
        }
    }
}
