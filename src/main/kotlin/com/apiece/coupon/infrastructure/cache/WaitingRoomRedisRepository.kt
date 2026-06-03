package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class WaitingRoomRedisRepository(
    private val redis: StringRedisTemplate,
) {
    private val enterScript = listLuaScript("lua/waiting-room-enter.lua")
    private val drainScript = longLuaScript("lua/waiting-room-drain.lua")

    // (통과 여부, 1-based 순번). 통과면 0.
    fun enter(couponId: Long, userId: Long): Pair<Boolean, Long> {
        val result = redis.runForStrings(
            enterScript,
            listOf(queueKey(couponId), passKey(couponId, userId)),
            userId,
        )
        redis.opsForSet().add(ROOMS_KEY, couponId.toString())
        return (result[0] == "1") to result[1].toLong()
    }

    fun isAdmitted(couponId: Long, userId: Long): Boolean =
        redis.hasKey(passKey(couponId, userId))

    fun drain(couponId: Long, admitPerSecond: Int, passTtlSeconds: Long): Int =
        redis.runForLong(
            drainScript,
            listOf(queueKey(couponId)),
            admitPerSecond, passTtlSeconds, passKeyPrefix(couponId),
        ).toInt()

    fun activeRooms(): List<Long> =
        redis.opsForSet().members(ROOMS_KEY).orEmpty().mapNotNull { it.toLongOrNull() }

    // {couponId} 해시 태그로 한 쿠폰의 키들을 같은 슬롯에 모은다 (Lua 멀티키 안전).
    private fun queueKey(couponId: Long) = "waiting:{$couponId}:queue"
    private fun passKeyPrefix(couponId: Long) = "waiting:{$couponId}:pass:"
    private fun passKey(couponId: Long, userId: Long) = passKeyPrefix(couponId) + userId

    private companion object {
        const val ROOMS_KEY = "waiting:rooms"
    }
}
