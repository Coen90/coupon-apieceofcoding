package com.apiece.coupon.infrastructure.cache

import com.apiece.coupon.application.CacheMetrics
import com.apiece.coupon.application.CouponIssuePolicy
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Repository
class CouponCacheRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val cacheProperties: CacheProperties,
    private val cacheMetrics: CacheMetrics,
) {

    fun getIssuePolicyOrLoad(id: Long, loader: () -> CouponIssuePolicy): CouponIssuePolicy =
        getOrLoad(
            key = "coupon:$id:issue-policy",
            loader = loader,
        )

    private fun getOrLoad(
        key: String,
        loader: () -> CouponIssuePolicy,
    ): CouponIssuePolicy {
        redisTemplate.opsForValue().get(key)?.let { cached ->
            cacheMetrics.incrementCouponCacheHit()
            return objectMapper.readValue(cached, CouponIssuePolicy::class.java)
        }
        cacheMetrics.incrementCouponDbRead()
        val response = loader()
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), Duration.ofMillis(cacheProperties.ttlMs))
        return response
    }
}
