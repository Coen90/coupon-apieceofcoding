package com.apiece.coupon.batch

import com.apiece.coupon.domain.ReconcileCheckpoint
import com.apiece.coupon.domain.ReconcileCheckpointRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ReconcileCheckpointStore(
    private val reconcileCheckpointRepository: ReconcileCheckpointRepository,
    private val reconcileProperties: ReconcileProperties,
) {
    private val jobName = "coupon-reconcile"
    private val owner = UUID.randomUUID().toString()

    @Transactional
    fun acquire(): Boolean {
        ensureRow()
        val now = System.currentTimeMillis()
        return reconcileCheckpointRepository.acquireLease(jobName, owner, now, now + reconcileProperties.leaseMs) == 1
    }

    @Transactional
    fun renew(): Boolean {
        val now = System.currentTimeMillis()
        return reconcileCheckpointRepository.renewLease(jobName, owner, now, now + reconcileProperties.leaseMs) == 1
    }

    @Transactional(readOnly = true)
    fun lastSuccessCutoffMs(): Long =
        reconcileCheckpointRepository.findById(jobName).orElse(ReconcileCheckpoint(jobName)).lastSuccessCutoffMs

    @Transactional
    fun markSuccess(cutoffMs: Long) {
        check(reconcileCheckpointRepository.saveSuccess(jobName, owner, cutoffMs) == 1) { "reconcile lease lost" }
    }

    @Transactional
    fun release() {
        reconcileCheckpointRepository.releaseLease(jobName, owner)
    }

    private fun ensureRow() {
        if (reconcileCheckpointRepository.existsById(jobName)) return
        try {
            reconcileCheckpointRepository.save(ReconcileCheckpoint(jobName))
        } catch (_: DataIntegrityViolationException) {
        }
    }
}
