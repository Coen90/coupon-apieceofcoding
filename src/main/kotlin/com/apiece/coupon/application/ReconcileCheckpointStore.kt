package com.apiece.coupon.application

import com.apiece.coupon.domain.ReconcileCheckpoint
import com.apiece.coupon.domain.ReconcileCheckpointRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ReconcileCheckpointStore(
    private val repository: ReconcileCheckpointRepository,
    private val properties: ReconcileProperties,
) {
    private val jobName = "coupon-reconcile"
    private val owner = UUID.randomUUID().toString()

    @Transactional
    fun acquire(): Boolean {
        ensureRow()
        val now = System.currentTimeMillis()
        return repository.acquireLease(jobName, owner, now, now + properties.leaseMs) == 1
    }

    @Transactional
    fun renew(): Boolean {
        val now = System.currentTimeMillis()
        return repository.renewLease(jobName, owner, now, now + properties.leaseMs) == 1
    }

    @Transactional(readOnly = true)
    fun lastSuccessCutoffMs(): Long =
        repository.findById(jobName).orElse(ReconcileCheckpoint(jobName)).lastSuccessCutoffMs

    @Transactional
    fun markSuccess(cutoffMs: Long) {
        check(repository.saveSuccess(jobName, owner, cutoffMs) == 1) { "reconcile lease lost" }
    }

    @Transactional
    fun release() {
        repository.releaseLease(jobName, owner)
    }

    private fun ensureRow() {
        if (repository.existsById(jobName)) return
        try {
            repository.save(ReconcileCheckpoint(jobName))
        } catch (_: DataIntegrityViolationException) {
            // 다른 인스턴스가 먼저 만들었으면 그대로 사용한다.
        }
    }
}
