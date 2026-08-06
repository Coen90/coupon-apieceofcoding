package com.apiece.coupon.infrastructure.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class WaitingRoomRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    private val enterScript = listLuaScript("lua/waiting-room-enter.lua")
    private val statusScript = longLuaScript("lua/waiting-room-status.lua")
    private val drainScript = longLuaScript("lua/waiting-room-drain.lua")

    // (통과 여부, 1-based 순번). 통과면 0.
    fun enter(couponId: Long, userId: Long): Pair<Boolean, Long> {
        // 먼저 활성화해야 중간 장애가 나도 대기열이 배출 대상에서 빠지지 않는다.
        redisTemplate.opsForSet().add(ROOMS_KEY, couponId.toString())
        val result = redisTemplate.runForStrings(
            enterScript,
            listOf(queueKey(couponId), passKey(couponId, userId)),
            userId,
        )
        return (result[0] == "1") to result[1].toLong()
    }

    // 반환: 0=통과, 1-based 순번=대기, null=아직 진입하지 않음.
    fun status(couponId: Long, userId: Long): Long? {
        val position = redisTemplate.runForLong(
            statusScript,
            listOf(queueKey(couponId), passKey(couponId, userId)),
            userId,
        )
        return position.takeUnless { it < 0 }
    }

    fun isAdmitted(couponId: Long, userId: Long): Boolean =
        redisTemplate.hasKey(passKey(couponId, userId))

    fun drain(couponId: Long, admitPerSecond: Int, passTtlMs: Long): Int =
        redisTemplate.runForLong(
            drainScript,
            listOf(queueKey(couponId)),
            admitPerSecond, passTtlMs, passKeyPrefix(couponId),
        ).toInt()

    fun activeRooms(): List<Long> =
        redisTemplate.opsForSet().members(ROOMS_KEY).orEmpty().mapNotNull { it.toLongOrNull() }

    // {couponId} 해시 태그로 한 쿠폰의 키들을 같은 슬롯에 모은다 (Lua 멀티키 안전).
    private fun queueKey(couponId: Long) = "waiting:{$couponId}:queue"
    private fun passKeyPrefix(couponId: Long) = "waiting:{$couponId}:pass:"
    private fun passKey(couponId: Long, userId: Long) = passKeyPrefix(couponId) + userId

    private companion object {
        const val ROOMS_KEY = "waiting:rooms"
    }
}
