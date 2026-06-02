# coupon-service

## 실행

```bash
# 이미지 빌드 (Docker daemon 필요)
./gradlew jibDockerBuild            # 앱 이미지 (coupon-service)
./gradlew -p gateway jibDockerBuild # 엣지 게이트웨이 이미지 (coupon-gateway)

docker compose up -d                # MySQL + Redis + Kafka + 앱 + 게이트웨이 기동
```

종료:

```bash
docker compose down -v     # 컨테이너 + 볼륨 제거
```

## API 호출

[scripts/api.sh](scripts/api.sh) (`jq` 필요). 발급 전 대기실 통과 흐름이 포함돼 있다.

## 부하/검증 (6단원: 트래픽 제어)

```bash
scripts/load/part-6/run.sh single   # 1대 통과 속도 (≈ admit-per-second)
scripts/load/part-6/run.sh scale     # 2대로도 통과 속도 유지 (ShedLock)
scripts/load/part-6/verify.sh        # 순서/멱등/게이트/fail-close 검증
scripts/load/part-6/edge.sh          # 엣지 Rate Limit: 어뷰저 컷 + 정상 통과
```
