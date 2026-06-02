package com.apiece.coupon.application

import com.apiece.coupon.domain.Coupon
import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import com.apiece.coupon.infrastructure.cache.SoldOutProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional

class ReconcilerTest {

    private val couponRepository = mockk<CouponRepository>()
    private val issuanceRepository = mockk<IssuanceRepository>(relaxed = true)
    private val redis = mockk<CouponReconcileRedisRepository>(relaxUnitFun = true)
    private val metrics = mockk<ReconcileMetrics>(relaxUnitFun = true)
    private val reconciler = Reconciler(
        couponRepository, issuanceRepository, redis,
        SoldOutProperties(ttlSeconds = 86400, fastPathTtlMs = 1000),
        ReconcileProperties(intervalMs = 60000, recheckDelayMs = 0),
        metrics,
    )

    private val couponId = 1L

    // total/issued 만 다른 쿠폰 한 건을 활성중으로 세팅하고, Redis 값은 인자로 stub.
    private fun setup(
        total: Int, issued: Int,
        stock: Long, users: Long, soldOut: Boolean,
        stockSecond: Long? = null,
    ) {
        val coupon = Coupon(name = "t", totalQuantity = total, issuedQuantity = issued, id = couponId)
        every { couponRepository.findAll() } returns listOf(coupon)
        every { couponRepository.findById(couponId) } returns Optional.of(coupon)
        every { redis.hasStock(couponId) } returns true
        if (stockSecond == null) {
            every { redis.stock(couponId) } returns stock
        } else {
            every { redis.stock(couponId) } returnsMany listOf(stock, stockSecond)
        }
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
        every { issuanceRepository.findIssuedUserIds(couponId) } returns (1L..10L).toList()
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
    fun `DB 측 불일치가 재검사에서도 같으면 알람 (자동 보정 안 함)`() {
        // issued 0, stock 4990 -> DB 측 +10. users 10 -> 목록 측 정합. 재검사도 동일.
        setup(total = 5000, issued = 0, stock = 4990, users = 10, soldOut = false)

        val report = reconciler.reconcileAll()

        verify(exactly = 0) { redis.addUsers(any(), any()) }
        assert(report.driftAlerts == 1 && report.redisDbDrift == 10L && report.falseAlarms == 0)
    }

    @Test
    fun `DB 측 불일치가 재검사에서 사라지면 false alarm`() {
        // 1차 stock 4990 (DB 측 +10), 재검사 stock 5000 (정합) -> false alarm.
        setup(total = 5000, issued = 0, stock = 4990, users = 10, soldOut = false, stockSecond = 5000)

        val report = reconciler.reconcileAll()

        assert(report.driftAlerts == 0 && report.falseAlarms == 1 && report.redisDbDrift == 0L)
    }
}
