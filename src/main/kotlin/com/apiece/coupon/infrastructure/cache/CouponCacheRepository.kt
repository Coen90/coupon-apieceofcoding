package com.apiece.coupon.infrastructure.cache

import com.apiece.coupon.api.dto.CouponResponse
import com.apiece.coupon.application.CacheMetrics
import com.apiece.coupon.application.CouponIssuePolicy
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Repository
class CouponCacheRepository(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    private val properties: CacheProperties,
    private val cacheMetrics: CacheMetrics,
) {

    fun getCouponOrLoad(id: Long, loader: () -> CouponResponse): CouponResponse =
        getOrLoad(
            key = "coupon:$id",
            valueType = CouponResponse::class.java,
            countMetrics = true,
            loader = loader,
        )

    fun getIssuePolicyOrLoad(id: Long, loader: () -> CouponIssuePolicy): CouponIssuePolicy =
        getOrLoad(
            key = "coupon:$id:issue-policy",
            valueType = CouponIssuePolicy::class.java,
            countMetrics = false,
            loader = loader,
        )

    private fun <T : Any> getOrLoad(
        key: String,
        valueType: Class<T>,
        countMetrics: Boolean,
        loader: () -> T,
    ): T {
        redis.opsForValue().get(key)?.let { cached ->
            if (countMetrics) {
                cacheMetrics.incrementCouponCacheHit()
            }
            return mapper.readValue(cached, valueType)
        }
        if (countMetrics) {
            cacheMetrics.incrementCouponDbRead()
        }
        val response = loader()
        redis.opsForValue().set(key, mapper.writeValueAsString(response), Duration.ofMillis(properties.ttlMs))
        return response
    }
}
