package com.apiece.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReconcileCheckpointRepository : JpaRepository<ReconcileCheckpoint, String> {
    @Modifying
    @Query(
        "UPDATE ReconcileCheckpoint c SET c.leaseOwner = :owner, c.leaseUntilMs = :leaseUntil " +
            "WHERE c.jobName = :jobName AND " +
            "(c.leaseOwner IS NULL OR c.leaseUntilMs < :now OR c.leaseOwner = :owner)",
    )
    fun acquireLease(
        @Param("jobName") jobName: String,
        @Param("owner") owner: String,
        @Param("now") now: Long,
        @Param("leaseUntil") leaseUntil: Long,
    ): Int

    @Modifying
    @Query(
        "UPDATE ReconcileCheckpoint c SET c.lastSuccessCutoffMs = :cutoff " +
            "WHERE c.jobName = :jobName AND c.leaseOwner = :owner",
    )
    fun saveSuccess(
        @Param("jobName") jobName: String,
        @Param("owner") owner: String,
        @Param("cutoff") cutoff: Long,
    ): Int

    @Modifying
    @Query(
        "UPDATE ReconcileCheckpoint c SET c.leaseOwner = NULL, c.leaseUntilMs = NULL " +
            "WHERE c.jobName = :jobName AND c.leaseOwner = :owner",
    )
    fun releaseLease(
        @Param("jobName") jobName: String,
        @Param("owner") owner: String,
    ): Int
}
