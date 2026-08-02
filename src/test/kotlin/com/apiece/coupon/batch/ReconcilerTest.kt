package com.apiece.coupon.batch

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
    private val gracePeriodMs = 10000L
    private val reconcileProperties = ReconcileProperties(intervalMs = 60000, gracePeriodMs = gracePeriodMs)
    private val couponReconciler = CouponReconciler(
        couponRepository,
        issuanceRepository,
        redis,
        SoldOutProperties(ttlSeconds = 86400, fastPathTtlMs = 1000),
    )
    private val reconciler = Reconciler(
        couponRepository,
        redis,
        reconcileProperties,
        couponReconciler,
    )

    private val couponId = 1L

    @Test
    fun `전수 audit은 하루 한 번 실행하도록 설정`() {
        val scheduled = Reconciler::class.java.getDeclaredMethod("scheduledAudit")
            .getAnnotation(org.springframework.scheduling.annotation.Scheduled::class.java)

        assert(scheduled.cron == "\${coupon.reconcile.audit-cron}")
    }

    private fun setup(
        total: Int, issued: Int,
        stock: Long, users: Long, soldOut: Boolean,
    ) {
        val coupon = Coupon(name = "t", totalQuantity = total, issuedQuantity = issued, id = couponId)
        every { couponRepository.findAll() } returns listOf(coupon)
        every { couponRepository.findById(couponId) } returns Optional.of(coupon)
        every { redis.stock(couponId) } returns stock
        every { redis.userCount(couponId) } returns users
        every { redis.soldOutExists(couponId) } returns soldOut
    }

    @Test
    fun `정합이면 아무 보정도 알람도 없음`() {
        setup(total = 5000, issued = 0, stock = 5000, users = 0, soldOut = false)

        val report = reconciler.auditAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        verify(exactly = 0) { redis.deleteSoldOut(any()) }
        verify(exactly = 0) { redis.setSoldOut(any(), any()) }
        assert(report.autoFixed == 0 && report.driftAlerts == 0 && report.redisDbDrift == 0L)
    }

    @Test
    fun `목록 측 휘발이면 DB 의 누락 user_id 를 SADD 자동 보정`() {
        setup(total = 5000, issued = 10, stock = 4990, users = 0, soldOut = false)
        every { issuanceRepository.findUserIdsByCouponId(couponId) } returns (1L..10L).toList()
        every { redis.userIds(couponId) } returns emptySet()

        val report = reconciler.auditAll()

        verify { redis.addUsers(couponId, match { it.size == 10 }) }
        assert(report.autoFixed == 1 && report.driftAlerts == 0 && report.redisDbDrift == 0L)
    }

    @Test
    fun `재고 양수인데 매진 플래그 살아있으면 DEL 자동 보정`() {
        setup(total = 5000, issued = 0, stock = 5000, users = 0, soldOut = true)

        val report = reconciler.auditAll()

        verify(exactly = 1) { redis.deleteSoldOut(couponId) }
        assert(report.autoFixed == 1)
    }

    @Test
    fun `재고 0인데 매진 플래그 없으면 SET 자동 보정`() {
        setup(total = 5000, issued = 5000, stock = 0, users = 5000, soldOut = false)

        val report = reconciler.auditAll()

        verify(exactly = 1) { redis.setSoldOut(couponId, 86400) }
        assert(report.autoFixed == 1)
    }

    @Test
    fun `DB 측 불일치는 알람만 내고 자동 보정하지 않음`() {
        setup(total = 5000, issued = 0, stock = 4990, users = 10, soldOut = true)

        val report = reconciler.auditAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        verify(exactly = 0) { redis.deleteSoldOut(any()) }
        verify(exactly = 0) { redis.setSoldOut(any(), any()) }
        assert(report.driftAlerts == 1 && report.redisDbDrift == 10L)
    }

    @Test
    fun `DB 측 불일치면 재고가 0이어도 매진 플래그를 설정하지 않음`() {
        setup(total = 5000, issued = 4990, stock = 0, users = 5000, soldOut = false)

        val report = reconciler.auditAll()

        verify(exactly = 0) { redis.setSoldOut(any(), any()) }
        assert(report.autoFixed == 0 && report.driftAlerts == 1 && report.redisDbDrift == 10L)
    }

    @Test
    fun `재고가 음수면 DB 측 잔차가 0이어도 자동 보정하지 않음`() {
        setup(total = 10, issued = 11, stock = -1, users = 0, soldOut = false)
        every { issuanceRepository.findUserIdsByCouponId(couponId) } returns (1L..11L).toList()

        val report = reconciler.auditAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        assert(report.autoFixed == 0 && report.redisDbDrift == 0L && report.stockNegative == 1)
    }

    @Test
    fun `Redis 사용자 목록이 DB보다 많으면 삭제하지 않고 알람`() {
        setup(total = 5000, issued = 10, stock = 4990, users = 11, soldOut = false)

        val report = reconciler.auditAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        assert(report.autoFixed == 0 && report.driftAlerts == 1)
    }

    @Test
    fun `누락 수와 DB 사용자가 맞지 않으면 자동 보정하지 않고 알람`() {
        setup(total = 5000, issued = 10, stock = 4990, users = 9, soldOut = false)
        every { issuanceRepository.findUserIdsByCouponId(couponId) } returns (1L..10L).toList()
        every { redis.userIds(couponId) } returns (1L..8L).map { it.toString() }.toSet()

        val report = reconciler.auditAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        assert(report.autoFixed == 0 && report.driftAlerts == 1)
    }

    @Test
    fun `발급 window 밖 쿠폰은 검사하지 않음`() {
        every { redis.couponIdsIssuedBetween(any(), any()) } returns emptyList()

        reconciler.scheduledRecent()

        verify(exactly = 0) { couponRepository.findById(any()) }
    }

    @Test
    fun `최근 대사는 유예 시간을 두고 최근 두 구간을 겹쳐 검사`() {
        val fromSlot = slot<Long>()
        val toSlot = slot<Long>()
        every { redis.couponIdsIssuedBetween(capture(fromSlot), capture(toSlot)) } returns emptyList()

        val before = System.currentTimeMillis()
        reconciler.scheduledRecent()
        val after = System.currentTimeMillis()

        assert(toSlot.captured in (before - gracePeriodMs)..(after - gracePeriodMs))
        assert(toSlot.captured - fromSlot.captured == reconcileProperties.intervalMs * 2)
    }

    @Test
    fun `전수 audit 은 Redis 최근 발급 기록 대신 DB 의 모든 쿠폰을 검사`() {
        val coupon = Coupon(name = "t", totalQuantity = 10, issuedQuantity = 0, id = couponId)
        every { couponRepository.findAll() } returns listOf(coupon)
        every { couponRepository.findById(couponId) } returns Optional.of(coupon)
        every { redis.stock(couponId) } returns 10
        every { redis.userCount(couponId) } returns 0
        every { redis.soldOutExists(couponId) } returns false

        val report = reconciler.auditAll()

        verify { couponRepository.findAll() }
        assert(report.checkedCoupons == 1 && report.failedCoupons == 0)
    }
}
