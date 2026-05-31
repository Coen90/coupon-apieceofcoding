package com.apiece.coupon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// @EnableScheduling: reconcile 배치를 매 분 1회 돌리기 위함 (5단원 5).
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class CouponApplication

fun main(args: Array<String>) {
	runApplication<CouponApplication>(*args)
}
