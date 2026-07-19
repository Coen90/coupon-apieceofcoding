package com.apiece.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "reconcile_checkpoint")
class ReconcileCheckpoint(
    @Id
    @Column(name = "job_name", length = 64)
    var jobName: String,

    @Column(name = "last_success_cutoff_ms", nullable = false)
    var lastSuccessCutoffMs: Long = 0,

    @Column(name = "lease_owner", length = 64)
    var leaseOwner: String? = null,

    @Column(name = "lease_until_ms")
    var leaseUntilMs: Long? = null,
)
