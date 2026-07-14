# 부하 / 검증 스크립트

part-2 ~ part-6 시나리오 실행 스크립트. 측정값 해석과 설계 배경은 각 단원 design 문서 참고.

## 사전 준비

```bash
brew install k6 jq
docker compose up -d
```

## 브랜치 전환 시

브랜치마다 서비스 코드가 다르다. 전환하면 이미지를 새로 굽고 컨테이너만 교체한다 (mysql/redis/kafka 는 유지).

```bash
git checkout <branch>
./gradlew jibDockerBuild
docker compose up -d --force-recreate coupon-service
```

`issuance_attempt_id`로 이름을 바꾼 p5-1 이상을 기존 DB 볼륨에서 처음 실행할 때는 학습용 데이터이므로 한 번만 `docker compose down -v` 후 다시 올린다.

발급 후 재발급은 `issuance` 최신 행을 갱신하고 `issuance_history`에 이력을 남긴다. `force_db_only.sh`도 두 테이블에 함께 기록한다.

## 디렉토리

```
공유      reset.sh, create_coupon.sh
part-2/   동시성: over_issuance.js, run.sh, verify.sh
part-3/   큐 디커플링: issue_burst.js, verify_burst.sh, run.sh, kafka_lag.sh, kafka_dlt_peek.sh
part-4/   캐시+매진 상태: coupon_burst.js, post_sellout_refresh.js, sell_out.sh, run.sh
part-5/   보상+정합: force_dlt.sh, force_db_only.sh, drift_report.sh, dlt_replay.sh, run.sh, verify_compensation.sh, verify_reconcile.sh
part-6/   트래픽 제어 베이스라인: create_big_coupon.sh, issue_flood.js, run.sh
```

## 실행

```bash
# part-2 (동시성)
./scripts/load/part-2/run.sh

# part-3 (큐 디커플링): reset, create, k6, verify 통합 러너
./scripts/load/part-3/run.sh
scripts/load/part-3/kafka_dlt_peek.sh   # DLT 확인 (3-2c)

# part-4 (캐시 + 매진 상태)
./scripts/load/part-4/run.sh            # coupon | sellout | all

# part-5 (보상 + 정합): 부하보다 "주입 + 검증"
./scripts/load/part-5/run.sh                  # 5-0 주입 후 drift 잔존 (베이스라인)
./scripts/load/part-5/verify_compensation.sh  # 5-1 운영자 보상 + 멱등성
./scripts/load/part-5/dlt_replay.sh           # 장애 복구 후 대기 중인 DLT를 최대 100건 재처리
./scripts/load/part-5/verify_reconcile.sh     # 5-2 lease 대사 + 자동 보정 + 알람
# part-6 (트래픽 제어 베이스라인): reset, 큰 쿠폰 생성, k6, 서버 도착량 측정 통합 러너
./scripts/load/part-6/run.sh
RATE=2000 DURATION=30s ./scripts/load/part-6/run.sh
QUANTITY=2000000 BASE_URL=http://localhost:8080 ./scripts/load/part-6/run.sh
```

# part-6 (트래픽 제어)
./scripts/load/part-6/run.sh   # 6-0 part-6-0-load-test: 매진 전 발급 폭주 베이스라인
./scripts/load/part-6/run.sh   # 6-1 part-6-1-waiting-room: Redis 대기실 통과 속도
./scripts/load/part-6/edge.sh  # 6-2 part-6-2-edge-rate-limit: 엣지 Rate Limit
```
part-5 는 두 등식 `total = 발급누적 + Redis재고 = Redis사용자 + Redis재고` 의 잔차로 불일치를 잰다. p5-1 이상에서는 모든 issuance 주입 이벤트에 발급 시도별 `issuanceAttemptId`를 넣는다. `force_dlt` 는 DB 측(알람 대상), `force_db_only` 는 목록 측(자동 보정 대상)을 깬다.
DLT consumer는 메시지와 실패 원인을 `issuance_dlt_log`에 기록한다. 운영자는 `GET /admin/issuance/dlt`로 확인하고, 장애가 복구되면 `POST /admin/issuance/dlt/replay`를 호출한다. 데이터 오류나 정책 취소는 `POST /admin/issuance/dlt/compensate?id=<id>`로 보상한다.
part-6 는 매진 전 폭주 베이스라인이다. 기본값은 `RATE=1000`, `DURATION=20s`, `QUANTITY=1000000` 이며, `/metrics/traffic/reset` 으로 카운터를 초기화한 뒤 발급 엔드포인트 도착량을 `/metrics/traffic` 에서 읽어 초당 도착 속도를 출력한다.
part-6 은 브랜치별 대표 스크립트 한 줄로 실행한다. 6-0 은 대기실 없는 발급 폭주 베이스라인, 6-1 은 Redis 대기실 통과 속도, 6-2 는 게이트웨이 Rate Limit 으로 어뷰저를 앞단에서 자르는 흐름을 확인한다.
part-6 은 6-0 에서 대기실 없는 발급 폭주 베이스라인, 6-1 에서 Redis 대기실 통과 속도, 6-2 에서 게이트웨이 Rate Limit 으로 어뷰저를 앞단에서 자르는 흐름을 확인한다.
part-6 는 매진 전 폭주 베이스라인이다. 기본값은 `RATE=1000`, `DURATION=20s`, `QUANTITY=1000000` 이며, `/metrics/traffic/reset` 으로 카운터를 초기화한 뒤 발급 엔드포인트 도착량을 `/metrics/traffic` 에서 읽어 초당 도착 속도를 출력한다.
