package com.apiece.coupon.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DltInboxRepository : JpaRepository<DltInbox, Long> {
    fun findByMessageKey(messageKey: String): DltInbox?

    fun findAllByStatusOrderByReceivedAtAsc(status: DltInboxStatus): List<DltInbox>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DltInbox d WHERE d.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): DltInbox?
}
