package com.apiece.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "dlt_inbox",
    uniqueConstraints = [UniqueConstraint(name = "uk_dlt_inbox_message_key", columnNames = ["message_key"])],
    indexes = [Index(name = "idx_dlt_inbox_status", columnList = "status")],
)
class DltInbox(
    @Column(name = "message_key", nullable = false, length = 200)
    var messageKey: String,

    @Column(name = "dlt_partition", nullable = false)
    var dltPartition: Int,

    @Column(name = "dlt_offset", nullable = false)
    var dltOffset: Long,

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "issuance_attempt_id", nullable = false, length = 36)
    var issuanceAttemptId: String,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: LocalDateTime,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "failure_reason", length = 1000)
    var failureReason: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: DltInboxStatus = DltInboxStatus.PENDING,

    @Column(name = "decision_reason", length = 64)
    var decisionReason: String? = null,

    @Column(name = "received_at", nullable = false)
    var receivedAt: LocalDateTime,

    @Column(name = "resolved_at")
    var resolvedAt: LocalDateTime? = null,

    @Version
    var version: Long? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
