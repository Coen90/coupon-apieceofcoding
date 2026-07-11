package com.apiece.coupon.application

// 가상 대기실. 줄(FIFO)에 세우고 통과 속도만큼만 입장권으로 흘려보낸다 (발급 정확성은 안 맡고 도착 속도만).
interface WaitingRoom {
    // 최초 진입. 멱등이라 재호출해도 순번이 안 밀린다.
    fun enter(couponId: Long, userId: Long): Admission

    // 상태 조회. 대기열을 다시 수정하지 않는다.
    fun status(couponId: Long, userId: Long): Admission

    fun isAdmitted(couponId: Long, userId: Long): Boolean
}

class Admission(
    val admitted: Boolean,
    val position: Long,
    val estimatedWaitSeconds: Long,
) {
    companion object {
        val ADMITTED = Admission(true, 0, 0)

        fun waiting(position: Long, admitPerSecond: Int): Admission =
            Admission(false, position, (position + admitPerSecond - 1) / admitPerSecond)
    }
}
