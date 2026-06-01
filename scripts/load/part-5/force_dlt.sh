#!/usr/bin/env bash
# 불일치 주입 ①: "Worker 가 DB INSERT 에 영구 실패" 재현 (5단원 2.1).
#
# Redis 쪽은 발급 완료 상태로 만들고 (stock -N, users +N), DB 에는 행을 만들지 않은 채
# issuance.requested.DLT 로 메시지 N 건을 직접 떨군다. 결과: DB 측 잔차 +N (알람 대상),
# 그리고 보상 처리기(part-5-1)가 소비할 DLT 메시지가 쌓인다.
#
# 사용:  COUPON_ID=1 [COUNT=10] [USER_BASE=900000] scripts/load/part-5/force_dlt.sh

set -euo pipefail
COUPON_ID="${COUPON_ID:?COUPON_ID 환경변수 필요}"
COUNT="${COUNT:-10}"
USER_BASE="${USER_BASE:-900000}"
TOPIC="${TOPIC:-issuance.requested.DLT}"
cd "$(dirname "$0")/../../.."

redis_cli() { docker compose exec -T redis redis-cli "$@"; }

issued_at="$(date -u +%Y-%m-%dT%H:%M:%S)"
expires_at="$(date -u -v+7d +%Y-%m-%dT%H:%M:%S)"

printf '\n\033[1;36m===== [상황 만들기] 발급은 됐는데 DB 저장이 실패한 경우 %s건 (force_dlt) =====\033[0m\n' \
  "$COUNT"

# 1) Redis 는 발급 완료 상태로 (정상 발급이 끝난 직후처럼): 재고 N 차감 + 사용자 N 추가.
user_ids=()
for i in $(seq 1 "$COUNT"); do user_ids+=( $(( USER_BASE + i )) ); done
redis_cli DECRBY "coupon:$COUPON_ID:stock" "$COUNT" >/dev/null
redis_cli SADD "coupon:$COUPON_ID:users" "${user_ids[@]}" >/dev/null
printf '  Redis: 재고 %s 줄이고 발급자 명단에 %s명 추가 (%s~%s번)  ← 사용자에겐 발급 성공으로 보임\n' \
  "$COUNT" "$COUNT" "${user_ids[0]}" "${user_ids[-1]}"

# 2) DB 행은 만들지 않고, DLT 로 메시지 N 건을 직접 producer 로 떨군다.
#    payload 는 IssuanceRequested 모양 그대로. 컨슈머가 default-type 으로 역직렬화한다.
{
  for uid in "${user_ids[@]}"; do
    printf '{"couponId":%s,"userId":%s,"issuedAt":"%s","expiresAt":"%s"}\n' \
      "$COUPON_ID" "$uid" "$issued_at" "$expires_at"
  done
} | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic "$TOPIC" >/dev/null

printf '  Kafka: DB 저장이 끝내 실패한 메시지 %s건을 DLT(실패 메시지 보관함)에 넣음\n' "$COUNT"
printf '\033[1;32m  완료: DB 에는 발급 기록이 없고 Redis 만 발급된 상태 → DB 쪽 숫자가 어긋납니다.\033[0m\n'
