package com.apiece.coupon.application

import com.apiece.coupon.infrastructure.cache.WaitingRoomProperties
import com.apiece.coupon.infrastructure.cache.WaitingRoomRedisRepository
import com.apiece.coupon.support.WaitingRoomNotEnteredException
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RedisWaitingRoom(
    private val waitingRoomRedisRepository: WaitingRoomRedisRepository,
    private val waitingRoomProperties: WaitingRoomProperties,
) : WaitingRoom {

    override fun enter(couponId: Long, userId: Long): Admission {
        val (admitted, position) = waitingRoomRedisRepository.enter(couponId, userId)
        return if (admitted) Admission.ADMITTED
        else Admission.waiting(position, waitingRoomProperties.admitPerSecond)
    }

    override fun status(couponId: Long, userId: Long): Admission =
        when (val position = waitingRoomRedisRepository.status(couponId, userId)) {
            null -> throw WaitingRoomNotEnteredException()
            0L -> Admission.ADMITTED
            else -> Admission.waiting(position, waitingRoomProperties.admitPerSecond)
        }

    override fun isAdmitted(couponId: Long, userId: Long): Boolean =
        waitingRoomRedisRepository.isAdmitted(couponId, userId)

    // 배출 타이머. ShedLock 으로 매초 한 대만 돈다 (안 그러면 통과 속도가 서버 수만큼 곱해진다).
    @Scheduled(fixedRate = 1000)
    @SchedulerLock(name = "waiting-room-drain", lockAtLeastFor = "PT0.95S", lockAtMostFor = "PT2S")
    fun drain() {
        waitingRoomRedisRepository.activeRooms().forEach { couponId ->
            waitingRoomRedisRepository.drain(
                couponId,
                waitingRoomProperties.admitPerSecond,
                waitingRoomProperties.passTtlMs,
            )
        }
    }
}
