package com.apiece.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

// 보상의 1차 멱등 키. PRIMARY KEY(id = compensationId)가 곧 멱등 키라, 같은 보상이
// 두 번 들어오면 두 번째 INSERT 가 PK 충돌로 거절된다 (5단원 4.3, 6.1).
@Entity
@Table(
    name = "compensation_log",
    indexes = [Index(name = "idx_compensation_issuance", columnList = "issuance_id")],
)
class CompensationLog(
    @Id
    @Column(length = 64)
    var id: String,

    @Column(name = "issuance_id", nullable = false)
    var issuanceId: Long,

    @Column(name = "compensated_at", nullable = false)
    var compensatedAt: LocalDateTime,

    @Column(nullable = false, length = 32)
    var reason: String,
)
