package com.apiece.coupon.application

import com.apiece.coupon.domain.Coupon
import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import com.apiece.coupon.infrastructure.cache.SoldOutProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional

class ReconcilerTest {

    private val couponRepository = mockk<CouponRepository>()
    private val issuanceRepository = mockk<IssuanceRepository>(relaxed = true)
    private val redis = mockk<CouponReconcileRedisRepository>(relaxUnitFun = true)
    private val metrics = mockk<ReconcileMetrics>(relaxUnitFun = true)
    private val checkpointStore = mockk<ReconcileCheckpointStore>(relaxUnitFun = true)
    private val gracePeriodMs = 10000L
    private val reconciler = Reconciler(
        couponRepository, issuanceRepository, redis,
        SoldOutProperties(ttlSeconds = 86400, fastPathTtlMs = 1000),
        ReconcileProperties(intervalMs = 60000, gracePeriodMs = gracePeriodMs),
        metrics,
        checkpointStore,
    )

    private val couponId = 1L

    @Test
    fun `전수 audit은 하루 한 번 실행하도록 설정`() {
        val scheduled = Reconciler::class.java.getDeclaredMethod("scheduledAudit")
            .getAnnotation(org.springframework.scheduling.annotation.Scheduled::class.java)

        assert(scheduled.cron == "\${coupon.reconcile.audit-cron}")
    }

    // total/issued 만 다른 쿠폰 한 건이 발급 window 에 들어온 상황을 세팅하고, Redis 값은 인자로 stub.
    private fun setup(
        total: Int, issued: Int,
        stock: Long, users: Long, soldOut: Boolean,
    ) {
        val coupon = Coupon(name = "t", totalQuantity = total, issuedQuantity = issued, id = couponId)
        every { redis.couponIdsIssuedBetween(any(), any()) } returns listOf(couponId)
        every { couponRepository.findById(couponId) } returns Optional.of(coupon)
        every { redis.stock(couponId) } returns stock
        every { redis.userCount(couponId) } returns users
        every { redis.soldOutExists(couponId) } returns soldOut
    }

    @Test
    fun `정합이면 아무 보정도 알람도 없음`() {
        setup(total = 5000, issued = 0, stock = 5000, users = 0, soldOut = false)

        val report = reconciler.reconcileAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        verify(exactly = 0) { redis.deleteSoldOut(any()) }
        verify(exactly = 0) { redis.setSoldOut(any(), any()) }
        assert(report.autoFixed == 0 && report.driftAlerts == 0 && report.redisDbDrift == 0L)
    }

    @Test
    fun `목록 측 휘발이면 DB 의 누락 user_id 를 SADD 자동 보정`() {
        // issued 10, stock 4990 -> DB 측 정합. users 0 -> 목록 측 +10.
        setup(total = 5000, issued = 10, stock = 4990, users = 0, soldOut = false)
        every { issuanceRepository.findNonCanceledUserIds(couponId) } returns (1L..10L).toList()
        every { redis.userIds(couponId) } returns emptySet()

        val report = reconciler.reconcileAll()

        verify { redis.addUsers(couponId, match { it.size == 10 }) }
        assert(report.autoFixed == 1 && report.driftAlerts == 0 && report.redisDbDrift == 0L)
    }

    @Test
    fun `재고 양수인데 매진 플래그 살아있으면 DEL 자동 보정`() {
        setup(total = 5000, issued = 0, stock = 5000, users = 0, soldOut = true)

        val report = reconciler.reconcileAll()

        verify(exactly = 1) { redis.deleteSoldOut(couponId) }
        assert(report.autoFixed == 1)
    }

    @Test
    fun `재고 0인데 매진 플래그 없으면 SET 자동 보정`() {
        setup(total = 5000, issued = 5000, stock = 0, users = 5000, soldOut = false)

        val report = reconciler.reconcileAll()

        verify(exactly = 1) { redis.setSoldOut(couponId, 86400) }
        assert(report.autoFixed == 1)
    }

    @Test
    fun `DB 측 불일치는 알람만 내고 자동 보정하지 않음`() {
        // issued 0, stock 4990 -> DB 측 +10. users 10 -> 목록 측 정합.
        setup(total = 5000, issued = 0, stock = 4990, users = 10, soldOut = false)

        val report = reconciler.reconcileAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        assert(report.driftAlerts == 1 && report.redisDbDrift == 10L)
    }

    @Test
    fun `Redis 사용자 목록이 DB보다 많으면 삭제하지 않고 알람`() {
        setup(total = 5000, issued = 10, stock = 4990, users = 11, soldOut = false)

        val report = reconciler.reconcileAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        assert(report.autoFixed == 0 && report.driftAlerts == 1)
    }

    @Test
    fun `누락 수와 DB 사용자가 맞지 않으면 자동 보정하지 않고 알람`() {
        setup(total = 5000, issued = 10, stock = 4990, users = 9, soldOut = false)
        every { issuanceRepository.findNonCanceledUserIds(couponId) } returns (1L..10L).toList()
        every { redis.userIds(couponId) } returns (1L..8L).map { it.toString() }.toSet()

        val report = reconciler.reconcileAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        assert(report.autoFixed == 0 && report.driftAlerts == 1)
    }

    @Test
    fun `발급 window 밖 쿠폰은 검사하지 않음`() {
        every { redis.couponIdsIssuedBetween(any(), any()) } returns emptyList()

        val report = reconciler.reconcileAll()

        verify(exactly = 0) { couponRepository.findById(any()) }
        assert(report.checkedCoupons == 0 && report.driftAlerts == 0 && report.redisDbDrift == 0L)
    }

    @Test
    fun `대사 상한은 현재 시각에서 grace period 를 뺀 값`() {
        val toSlot = slot<Long>()
        every { redis.couponIdsIssuedBetween(any(), capture(toSlot)) } returns emptyList()

        val before = System.currentTimeMillis()
        reconciler.reconcileAll()
        val after = System.currentTimeMillis()

        assert(toSlot.captured in (before - gracePeriodMs)..(after - gracePeriodMs))
    }

    @Test
    fun `전수 audit 은 Redis 색인 대신 DB 의 모든 쿠폰을 검사`() {
        val coupon = Coupon(name = "t", totalQuantity = 10, issuedQuantity = 0, id = couponId)
        every { checkpointStore.acquire() } returns true
        every { checkpointStore.renew() } returns true
        every { couponRepository.findAll() } returns listOf(coupon)
        every { couponRepository.findById(couponId) } returns Optional.of(coupon)
        every { redis.stock(couponId) } returns 10
        every { redis.userCount(couponId) } returns 0
        every { redis.soldOutExists(couponId) } returns false

        val report = reconciler.auditAll()

        verify { couponRepository.findAll() }
        verify { checkpointStore.release() }
        assert(report.checkedCoupons == 1 && report.failedCoupons == 0)
    }
}
