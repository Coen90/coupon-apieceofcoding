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
import java.time.LocalDateTime

@Entity
@Table(
    name = "issuance_dlt_log",
    uniqueConstraints = [UniqueConstraint(name = "uk_issuance_dlt_message_key", columnNames = ["message_key"])],
    indexes = [
        Index(name = "idx_issuance_dlt_status", columnList = "status"),
        Index(name = "idx_issuance_dlt_user_coupon", columnList = "user_id, coupon_id"),
    ],
)
class IssuanceDltLog(
    @Column(name = "message_key", nullable = false, length = 300)
    var messageKey: String,
    @Column(name = "coupon_id")
    var couponId: Long?,
    @Column(name = "user_id")
    var userId: Long?,
    @Column(name = "issued_at")
    var issuedAt: LocalDateTime?,
    @Column(name = "expires_at")
    var expiresAt: LocalDateTime?,
    @Column(name = "exception_type", length = 200)
    var exceptionType: String? = null,
    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: IssuanceDltStatus = IssuanceDltStatus.PENDING,
    @Column(name = "decision_reason", length = 64)
    var decisionReason: String? = null,
    @Column(name = "received_at", nullable = false)
    var receivedAt: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
