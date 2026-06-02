// 엣지 관문(Spring Cloud Gateway). coupon-service 와 별개 프로세스라 Spring Boot 버전도 독립이다.
// (본 앱은 Boot 4.0, Spring Cloud 는 아직 Boot 4 미지원이라 게이트웨이만 검증된 Boot 3.5 조합을 쓴다.)
// 빌드: coupon 디렉토리에서  ./gradlew -p gateway jibDockerBuild
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
    // RedisRateLimiter (Token Bucket) 는 reactive Redis 가 필요하다. 저장소는 발급과 같은 Redis 재사용.
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
}

jib {
    from {
        // 본 앱과 같은 base 이미지 재사용 (이미 로컬에 pull 됨). 21 바이트코드는 25 JRE 에서 그대로 돈다.
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
