# coupon-service

## 실행

```bash
# 이미지 빌드 및 실행 (Docker daemon 필요)
./gradlew --stop && ./gradlew jibDockerBuild && docker compose up -d
```

종료:

```bash
docker compose down -v     # 컨테이너 + 볼륨 제거
```

## API 호출

[scripts/api.sh](scripts/api.sh) (`jq` 필요)

## 스크립트 테스트

프로젝트 루트에서 테스트하려는 Part의 스크립트를 실행합니다.

```bash
./scripts/load/part-1/run.sh
./scripts/load/part-2/run.sh
./scripts/load/part-3/run.sh
./scripts/load/part-4/run.sh
./scripts/load/part-5/run.sh
./scripts/load/part-6/run.sh
```

현재 `part-1` 브랜치에는 `scripts/load/part-1/run.sh`가 없습니다.
Part 2부터는 해당 Part가 시작되는 브랜치와 이후 브랜치에서 실행 스크립트를 사용할 수 있습니다.

## 강의 브랜치

Part 0은 별도 `part-0` 브랜치 없이 `main`을 사용합니다.

| Part | 브랜치 |
| --- | --- |
| Part 0 | `main` |
| Part 1 | `part-1` |
| Part 2 | `part-2-1-load-test`<br>`part-2-2-pessimistic-lock`<br>`part-2-3-redis-lua` |
| Part 3 | `part-3-1-load-test`<br>`part-3-2a-inmemory-queue`<br>`part-3-2b-event-listener`<br>`part-3-2c-kafka` |
| Part 4 | `part-4-0-load-test`<br>`part-4-1a-cache-naive`<br>`part-4-1b-single-flight`<br>`part-4-1c-swr`<br>`part-4-2-sold-out-signal` |
| Part 5 | `part-5-0-drift-injection`<br>`part-5-1-dlt-replay`<br>`part-5-2-reconcile` |
| Part 6 | `part-6-0-load-test`<br>`part-6-1-waiting-room`<br>`part-6-2-edge-rate-limit` |

## 라이선스

Copyright 2026 apieceofcoding

이 저장소의 자체 작성 예제 코드는 [Apache License 2.0](LICENSE)으로 제공됩니다.
외부 라이브러리와 Docker 이미지는 각 프로젝트의 라이선스를 따르며,
Gradle과 Docker를 통해 사용자의 로컬 환경에 직접 설치됩니다.
