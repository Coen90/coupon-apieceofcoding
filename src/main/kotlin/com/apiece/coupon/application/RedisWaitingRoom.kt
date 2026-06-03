package com.apiece.coupon.application

import com.apiece.coupon.infrastructure.cache.WaitingRoomProperties
import com.apiece.coupon.infrastructure.cache.WaitingRoomRedisRepository
import com.apiece.coupon.support.WaitingRoomUnavailableException
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

// Redis 대기실. 모든 서버가 같은 줄(Sorted Set)/통과 명단을 봐서, 서버가 몇 대든 통과 속도가 유지된다.
@Component
class RedisWaitingRoom(
    private val repository: WaitingRoomRedisRepository,
    private val properties: WaitingRoomProperties,
    private val trafficMetrics: TrafficMetrics,
) : WaitingRoom {

    override fun enter(couponId: Long, userId: Long): Admission {
        val (admitted, position) = repository.enter(couponId, userId)
        return if (admitted) Admission.ADMITTED
        else Admission.waiting(position, properties.admitPerSecond)
    }

    override fun isAdmitted(couponId: Long, userId: Long): Boolean =
        try {
            repository.isAdmitted(couponId, userId)
        } catch (e: DataAccessException) {
            // Redis 장애 시 기본은 fail-close (의심스러우면 막는다).
            if (properties.failOpen) {
                log.warn(e) { "대기실 Redis 장애, fail-open 으로 통과 (위험)" }
                true
            } else {
                throw WaitingRoomUnavailableException()
            }
        }

    // 배출 타이머. ShedLock 으로 매초 한 대만 돈다 (안 그러면 통과 속도가 서버 수만큼 곱해진다).
    @Scheduled(fixedRate = 1000)
    @SchedulerLock(name = "waiting-room-drain", lockAtLeastFor = "PT0.95S", lockAtMostFor = "PT2S")
    fun drain() {
        val passTtlSeconds = (properties.passTtlMs / 1000).coerceAtLeast(1)
        repository.activeRooms().forEach { couponId ->
            val admitted = repository.drain(couponId, properties.admitPerSecond, passTtlSeconds)
            trafficMetrics.addAdmitted(admitted)
        }
    }
}
