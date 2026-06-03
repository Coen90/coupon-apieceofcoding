// 엣지 관문(Spring Cloud Gateway). 별개 프로세스라 Boot 버전 독립 (본 앱 Boot 4, Spring Cloud 는 아직
// Boot 4 미지원이라 게이트웨이만 Boot 3.5). 빌드: ./gradlew -p gateway jibDockerBuild
plugins {
    java
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.cloud.tools.jib") version "3.4.4"
}

group = "com.apiece"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.3")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    // RedisRateLimiter 는 reactive Redis 필요. 저장소는 같은 Redis 재사용.
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
}

jib {
    from {
        // 본 앱과 같은 base 재사용. 21 바이트코드는 25 JRE 에서 그대로 돈다.
        image = "docker://eclipse-temurin:25-jre@sha256:04262e8782d6b034ee5d7c1c5d4e8938fcf2063a76b4bfcd84e5d994d09c27bc"
        platforms {
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
    to {
        image = "coupon-gateway"
        tags = setOf("latest")
    }
    container {
        mainClass = "com.apiece.gateway.GatewayApplication"
        ports = listOf("8080")
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}
