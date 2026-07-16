package com.apiece.coupon.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface IssuanceDltLogRepository : JpaRepository<IssuanceDltLog, Long> {
    fun findByMessageKey(messageKey: String): IssuanceDltLog?
    fun countByIssuanceAttemptId(issuanceAttemptId: String): Long
    fun findTop100ByOrderByReceivedAtDesc(): List<IssuanceDltLog>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT log FROM IssuanceDltLog log WHERE log.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): IssuanceDltLog?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findTop100ByStatusOrderByReceivedAtAsc(status: IssuanceDltStatus): List<IssuanceDltLog>
}
