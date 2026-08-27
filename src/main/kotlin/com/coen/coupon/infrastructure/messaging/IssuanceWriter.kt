package com.coen.coupon.infrastructure.messaging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class IssuanceWriter(
    private val transaction: IssuanceTransactionWriter
) {
    fun write(event: IssuanceRequested) {
        try {
            transaction.insertAndIncrement(event)
        } catch (_: DataIntegrityViolationException) {
            log.debug {"Unique 위반은 멱등 처리: couponId=${event.couponId}, userId=${event.userId}"}
        }
    }
}