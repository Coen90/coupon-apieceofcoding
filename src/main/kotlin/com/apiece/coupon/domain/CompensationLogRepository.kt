package com.apiece.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository

interface CompensationLogRepository : JpaRepository<CompensationLog, String>
