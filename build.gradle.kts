buildscript {
	dependencies {
		classpath("org.ow2.asm:asm:9.8")
	}
}

plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.0.7"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
	id("com.google.cloud.tools.jib") version "3.4.4"
}

group = "com.apiece"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
	// SoldOutState 의 in-process fast-path 캐시.
	implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
	runtimeOnly("com.mysql:mysql-connector-j")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk:1.13.13")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

jib {
	from {
		image = "eclipse-temurin:25-jre"
		platforms {
			platform {
				architecture = "arm64"
				os = "linux"
			}
		}
	}
	to {
		image = "coupon-service"
		tags = setOf("latest", project.version.toString())
	}
	container {
		ports = listOf("8080")
		creationTime.set("USE_CURRENT_TIMESTAMP")
	}
}
