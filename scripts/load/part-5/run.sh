#!/usr/bin/env bash
# 서버 기능을 감지해 part-5-0, 5-1, 5-2 시나리오 중 하나를 실행한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."
source ./scripts/load/part-5/_common.sh

COUNT="${COUNT:-10}"

baseline() {
  for injector in force_dlt.sh force_db_only.sh; do
    ./scripts/load/reset.sh >/dev/null
    cid="$(./scripts/load/create_coupon.sh)"
    COUPON_ID="$cid" COUNT="$COUNT" "./scripts/load/part-5/$injector" >/dev/null
    COUPON_ID="$cid" ./scripts/load/part-5/drift_report.sh
  done
  printf '\n\033[1;32m===== part-5-0 불일치 주입 완료 =====\033[0m\n'
}

wait_dlt_id() {
  for _ in $(seq 1 30); do
    id="$(curl -fsS "$BASE/admin/issuance/dlt" | jq -r --arg attempt "$1" \
      '.[] | select(.issuanceAttemptId == $attempt) | .id' | tail -1)"
    [[ -n "$id" ]] && { printf '%s' "$id"; return 0; }
    sleep 1
  done
  return 1
}

compensate() {
  curl -fsS -X POST "$BASE/admin/issuance/dlt/compensate" \
    -H 'Content-Type: application/json' -d "{\"id\":$1}"
}

compensation() {
  local user_base=900000
  restart_service

  printf '\n\033[1;35m##### DLT replay #####\033[0m\n'
  ./scripts/load/reset.sh >/dev/null
  cid="$(./scripts/load/create_coupon.sh)"
  COUPON_ID="$cid" COUNT=1 USER_BASE="$user_base" ./scripts/load/part-5/force_dlt.sh >/dev/null
  attempt_id="$(redis_cli GET "coupon:$cid:issuance-attempt:$((user_base + 1))")"
  wait_dlt_id "$attempt_id" >/dev/null || ng "DLT 로그 대기 실패"
  curl -fsS -X POST "$BASE/admin/issuance/dlt/replay" >/dev/null
  for _ in $(seq 1 30); do
    [[ "$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE issuance_attempt_id='$attempt_id'")" == "1" ]] && break
    sleep 1
  done
  check "같은 발급 시도로 DB 저장" \
    "$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE issuance_attempt_id='$attempt_id'")" "1"

  printf '\n\033[1;35m##### 보상과 중복 호출 #####\033[0m\n'
  ./scripts/load/reset.sh >/dev/null
  restart_service
  curl -fsS -X POST "$BASE/metrics/compensation/reset" >/dev/null
  cid="$(./scripts/load/create_coupon.sh)"
  COUPON_ID="$cid" COUNT=3 USER_BASE="$user_base" ./scripts/load/part-5/force_dlt.sh >/dev/null
  for i in 1 2 3; do
    attempt_id="$(redis_cli GET "coupon:$cid:issuance-attempt:$((user_base + i))")"
    dlt_id="$(wait_dlt_id "$attempt_id")" || { ng "DLT 로그 대기 실패"; continue; }
    compensate "$dlt_id" >/dev/null
    compensate "$dlt_id" >/dev/null
  done
  check "실제 보상 수" "$(curl -fsS "$BASE/metrics/compensation" | jq -r '.compensationTotal')" "3"
  check "재고 복구" "$(redis_cli GET "coupon:$cid:stock")" "5000"
  check "발급자 명단 복구" "$(redis_cli SCARD "coupon:$cid:users")" "0"
  summary "part-5-1"
}

reconcile() {
  export COUPON_RECONCILE_AUDIT_CRON="0 0 0 1 1 *"
  restart_service 3600000

  printf '\n\033[1;35m##### Redis users 누락 자동 보정 #####\033[0m\n'
  ./scripts/load/reset.sh >/dev/null
  curl -fsS -X POST "$BASE/metrics/reconcile/reset" >/dev/null
  cid="$(./scripts/load/create_coupon.sh)"
  COUPON_ID="$cid" COUNT="$COUNT" ./scripts/load/part-5/force_db_only.sh >/dev/null
  curl -fsS -X POST "$BASE/admin/reconcile/run" >/dev/null
  check "발급자 명단 복구" "$(redis_cli SCARD "coupon:$cid:users")" "$COUNT"
  check "자동 보정 횟수" "$(curl -fsS "$BASE/metrics/reconcile" | jq -r '.reconcileAutoFixTotal')" "1"

  printf '\n\033[1;35m##### DB 측 불일치는 알람만 #####\033[0m\n'
  ./scripts/load/reset.sh >/dev/null
  curl -fsS -X POST "$BASE/metrics/reconcile/reset" >/dev/null
  cid="$(./scripts/load/create_coupon.sh)"
  COUPON_ID="$cid" COUNT="$COUNT" ./scripts/load/part-5/force_dlt.sh >/dev/null
  stock_before="$(redis_cli GET "coupon:$cid:stock")"
  curl -fsS -X POST "$BASE/admin/reconcile/run" >/dev/null
  check "DB 측 불일치 감지" "$(curl -fsS "$BASE/metrics/reconcile" | jq -r '.redisDbDrift')" "$COUNT"
  check "Redis 재고 유지" "$(redis_cli GET "coupon:$cid:stock")" "$stock_before"
  summary "part-5-2"
}

wait_service_ready || { printf 'coupon-service 준비 실패\n' >&2; exit 1; }

if curl -fsS "$BASE/metrics/reconcile" >/dev/null 2>&1; then
  reconcile
elif curl -fsS "$BASE/admin/issuance/dlt" >/dev/null 2>&1; then
  compensation
else
  baseline
fi
