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
import java.time.LocalDateTime

@Entity
@Table(
    name = "issuance_history",
    indexes = [Index(name = "idx_issuance_history_user_coupon", columnList = "user_id, coupon_id")],
)
class IssuanceHistory(
    @Column(name = "issuance_attempt_id", nullable = false, length = 36)
    var issuanceAttemptId: String,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: IssuanceStatus,

    @Column(nullable = false, length = 64)
    var reason: String,

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: LocalDateTime,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
