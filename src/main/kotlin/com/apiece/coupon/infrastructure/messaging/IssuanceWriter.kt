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
            log.debug { "UNIQUE 위반은 멱등 처리: couponId=${event.couponId}, userId=${event.userId}" }
        }
    }
}
