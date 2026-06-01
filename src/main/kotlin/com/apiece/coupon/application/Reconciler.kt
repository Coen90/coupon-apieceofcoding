package com.apiece.coupon.application

import com.apiece.coupon.domain.CouponRepository
import com.apiece.coupon.domain.IssuanceRepository
import com.apiece.coupon.infrastructure.cache.CouponReconcileRedisRepository
import com.apiece.coupon.infrastructure.cache.ReconcileProperties
import com.apiece.coupon.infrastructure.cache.SoldOutProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private val log = KotlinLogging.logger {}

// 사건으로 잡히지 않는 불일치(Redis 휘발, 매진 플래그 잔존)까지 주기적으로 훑어,
// 안전한 종류는 그 자리에서 자동 보정하고 위험한 종류는 알람으로 사람에게 넘긴다 (5단원 5).
@Component
class Reconciler(
    private val couponRepository: CouponRepository,
    private val issuanceRepository: IssuanceRepository,
    private val reconcileRedisRepository: CouponReconcileRedisRepository,
    private val soldOutProperties: SoldOutProperties,
    private val reconcileProperties: ReconcileProperties,
    private val metrics: ReconcileMetrics,
) {

    // 스케줄 스레드와 /admin/reconcile/run 의 HTTP 스레드가 동시에 돌면 게이지가 비결정적으로
    // 덮어써진다. 인스턴스 안에서는 한 번에 하나만 돌도록 가드 (분산 환경은 ShedLock, 5단원 6.2).
    private val running = AtomicBoolean(false)

    // 매 분 1회. 분산 환경에선 ShedLock 으로 한 인스턴스만 돈다 (5단원 6.2, 본편은 단일 인스턴스).
    @Scheduled(fixedRateString = "\${coupon.reconcile.interval-ms}")
    fun scheduled() {
        reconcileAll()
    }

    fun reconcileAll(): ReconcileReport {
        if (!running.compareAndSet(false, true)) {
            log.info { "reconcile 이 이미 실행 중이라 이번 호출은 건너뜀" }
            return ReconcileReport(
                checkedCoupons = 0, autoFixed = 0, driftAlerts = 0,
                falseAlarms = 0, redisDbDrift = 0L, stockNegative = 0,
            )
        }
        try {
            // "활성중 쿠폰": 설계 5.3 은 starts_at <= now <= ends_at + grace 로 정의하지만 본편 Coupon
            // 에는 ends_at 이 없어, Redis stock 키가 살아있는 쿠폰을 활성으로 근사한다 (단일 인스턴스 +
            // 수십 건 가정). 그래서 stock 키 자체가 휘발한 쿠폰은 검사에서 빠지는 사각지대가 있고,
            // 설계 5.4 의 진행중 warning / 종료 alert 임계 분리도 본편에선 생략했다. 대규모/시간기반은 7단원.
            val coupons = couponRepository.findAll().filter { it.id != null && reconcileRedisRepository.hasStock(it.id!!) }

            var autoFixed = 0
            var driftAlerts = 0
            var falseAlarms = 0
            var dbDriftSum = 0L
            var stockNegative = 0

            for (coupon in coupons) {
                val outcome = try {
                    reconcileCoupon(coupon.id!!)
                } catch (e: Exception) {
                    log.warn(e) { "reconcile 중 예외 coupon=${coupon.id}, skip" }
                    CouponOutcome()
                }
                autoFixed += outcome.autoFixed
                if (outcome.driftAlert) driftAlerts++
                if (outcome.falseAlarm) falseAlarms++
                dbDriftSum += outcome.confirmedDbDrift
                if (outcome.stockNegative) stockNegative++
            }

            metrics.setRedisDbDrift(dbDriftSum)
            metrics.setStockNegative(stockNegative.toLong())

            val report = ReconcileReport(
                checkedCoupons = coupons.size,
                autoFixed = autoFixed,
                driftAlerts = driftAlerts,
                falseAlarms = falseAlarms,
                redisDbDrift = dbDriftSum,
                stockNegative = stockNegative,
            )
            if (driftAlerts > 0 || stockNegative > 0) {
                // 실전이라면 Slack 등으로 사람에게. 여기서는 WARN 로그.
                log.warn { "reconcile alert: checked=${report.checkedCoupons} drift=${report.redisDbDrift} negative=${report.stockNegative}" }
            }
            return report
        } finally {
            running.set(false)
        }
    }

    private fun reconcileCoupon(couponId: Long): CouponOutcome {
        val first = readSnapshot(couponId) ?: return CouponOutcome()
        var autoFixed = 0

        // 재고 음수 (Lua 가드가 뚫린 경우): 항상 0이어야 한다. 자동 보정 대상 아님.
        val stockNegative = first.stock < 0
        if (stockNegative) log.warn { "재고 음수 감지 coupon=$couponId stock=${first.stock}" }

        // 매진 플래그 정합 (등식과 독립). 멱등 연산이라 매 분 다시 호출돼도 안전.
        if (first.stock > 0 && first.soldOut) {
            reconcileRedisRepository.deleteSoldOut(couponId)
            metrics.incrementAutoFix()
            autoFixed++
            log.info { "auto-fix: 매진 플래그 해제 coupon=$couponId (stock=${first.stock})" }
        } else if (first.stock == 0L && !first.soldOut) {
            reconcileRedisRepository.setSoldOut(couponId, soldOutProperties.ttlSeconds)
            metrics.incrementAutoFix()
            autoFixed++
            log.info { "auto-fix: 매진 플래그 설정 coupon=$couponId" }
        }

        // 목록 측만 부족 + DB 측은 정합 = Redis 사용자 목록 휘발. DB 의 ISSUED 기준으로 SADD.
        // stock 을 건드리지 않아 과발급/중복발급을 만들 수 없으므로 자동 보정이 안전하다.
        if (first.listResidual > 0 && first.dbResidual == 0L) {
            val dbUserIds = issuanceRepository.findIssuedUserIds(couponId)
            val redisUserIds = reconcileRedisRepository.userIds(couponId).mapNotNull { it.toLongOrNull() }.toSet()
            val missing = dbUserIds.filter { it !in redisUserIds }
            if (missing.isNotEmpty()) {
                reconcileRedisRepository.addUsers(couponId, missing)
                metrics.incrementAutoFix()
                autoFixed++
                log.info { "auto-fix: 사용자 목록 SADD ${missing.size}건 coupon=$couponId" }
            }
        }

        // DB 측 불일치(또는 음수): 자동 보정이 과발급/회수를 부를 수 있어 알람 대상.
        // false alarm 거르기: 재검사에서 같은 방향으로 다시 깨졌을 때만 진짜로 인정 (5단원 5.4).
        var driftAlert = false
        var falseAlarm = false
        var confirmedDbDrift = 0L
        if (first.dbResidual != 0L) {
            if (reconcileProperties.recheckDelayMs > 0) {
                Thread.sleep(reconcileProperties.recheckDelayMs)
            }
            val second = readSnapshot(couponId)
            if (second != null && second.dbResidual != 0L && sameSign(first.dbResidual, second.dbResidual)) {
                driftAlert = true
                confirmedDbDrift = abs(second.dbResidual)
                log.warn { "DB 측 불일치 확정 coupon=$couponId residual=${second.dbResidual} (자동 보정 불가, 사람 확인)" }
            } else {
                falseAlarm = true
                metrics.incrementFalseAlarm()
                log.info { "false alarm 무시 coupon=$couponId (재검사에서 사라짐)" }
            }
        }

        return CouponOutcome(autoFixed, driftAlert, falseAlarm, confirmedDbDrift, stockNegative)
    }

    private fun readSnapshot(couponId: Long): ReconcileSnapshot? {
        val coupon = couponRepository.findById(couponId).orElse(null) ?: return null
        val stock = reconcileRedisRepository.stock(couponId) ?: return null
        return ReconcileSnapshot(
            couponId = couponId,
            total = coupon.totalQuantity,
            issued = coupon.issuedQuantity,
            stock = stock,
            userCount = reconcileRedisRepository.userCount(couponId),
            soldOut = reconcileRedisRepository.soldOutExists(couponId),
        )
    }

    private fun sameSign(a: Long, b: Long): Boolean = (a > 0 && b > 0) || (a < 0 && b < 0)

    private class CouponOutcome(
        val autoFixed: Int = 0,
        val driftAlert: Boolean = false,
        val falseAlarm: Boolean = false,
        val confirmedDbDrift: Long = 0L,
        val stockNegative: Boolean = false,
    )
}
