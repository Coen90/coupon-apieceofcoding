# coupon-service

## 실행

```bash
# 이미지 빌드 (Docker daemon 필요)
./gradlew jibDockerBuild            # 앱 + Gateway 이미지 (coupon-service, coupon-gateway)

docker compose up -d                # MySQL + Redis + Kafka + 앱 + 게이트웨이 기동
```

`gateway`는 Gradle 멀티모듈에 포함돼 있어서 별도 `./gradlew -p gateway jibDockerBuild`를 실행하지 않아도 된다.

종료:

```bash
docker compose down -v     # 컨테이너 + 볼륨 제거
```

## API 호출

[scripts/api.sh](scripts/api.sh) (`jq` 필요). 발급 전 대기실 통과 흐름이 포함돼 있다.
