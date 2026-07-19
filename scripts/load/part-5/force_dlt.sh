#!/usr/bin/env bash
# 불일치 주입 ①: "Worker 가 DB INSERT 에 영구 실패" 재현 (5단원 2.1).
#
# Redis 쪽은 발급 완료 상태로 만들고 (stock -N, users +N), DB 에는 행을 만들지 않은 채
# issuance.requested.DLT 로 메시지 N 건을 직접 떨군다. 결과: DB 측 잔차 +N (알람 대상),
# 그리고 운영자가 확인할 DLT 메시지가 쌓인다.
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
issuance_attempt_ids=()
for i in $(seq 1 "$COUNT"); do
  user_ids+=( $(( USER_BASE + i )) )
  issuance_attempt_ids+=( "$(uuidgen | tr '[:upper:]' '[:lower:]')" )
done

# 재실행 가드: 같은 쿠폰에 이미 주입돼 있으면 (reset 없이 재실행) 재고가 또 깎이지 않게 중단.
# 오케스트레이터(run.sh)는 reset.sh 로 비운 뒤 부르므로 이 가드에 걸리지 않는다.
if [[ "$(redis_cli SISMEMBER "coupon:$COUPON_ID:users" "${user_ids[0]}")" == "1" ]]; then
  printf '\033[1;33m  이미 주입된 쿠폰입니다. 먼저 ./scripts/load/reset.sh 로 초기화한 뒤 다시 실행하세요.\033[0m\n'
  exit 1
fi

redis_cli DECRBY "coupon:$COUPON_ID:stock" "$COUNT" >/dev/null
redis_cli SADD "coupon:$COUPON_ID:users" "${user_ids[@]}" >/dev/null
for i in "${!user_ids[@]}"; do
  redis_cli SET "coupon:$COUPON_ID:issuance-attempt:${user_ids[$i]}" "${issuance_attempt_ids[$i]}" >/dev/null
done
printf '  Redis: 재고 %s 줄이고 발급자 명단에 %s명 추가 (%s~%s번)  ← 사용자에겐 발급 성공으로 보임\n' \
  "$COUNT" "$COUNT" "${user_ids[0]}" "${user_ids[$((COUNT - 1))]}"

# 2) DB 행은 만들지 않고, DLT 로 메시지 N 건을 직접 producer 로 떨군다.
#    payload 는 IssuanceRequested 모양 그대로. 컨슈머가 default-type 으로 역직렬화한다.
{
  for i in "${!user_ids[@]}"; do
    printf '{"couponId":%s,"userId":%s,"issuanceAttemptId":"%s","issuedAt":"%s","expiresAt":"%s"}\n' \
      "$COUPON_ID" "${user_ids[$i]}" "${issuance_attempt_ids[$i]}" "$issued_at" "$expires_at"
  done
} | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic "$TOPIC" >/dev/null
printf '  Kafka: DB 저장이 끝내 실패한 메시지 %s건을 DLT(실패 메시지 보관함)에 넣음\n' "$COUNT"
printf '\033[1;32m  완료: DB 에는 발급 기록이 없고 Redis 만 발급된 상태 → DB 쪽 숫자가 어긋납니다.\033[0m\n'
