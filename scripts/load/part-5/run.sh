#!/usr/bin/env bash
# part-5-1: DLT replay와 보상을 한 번에 검증한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."
source ./scripts/load/part-5/_common.sh

COUNT="${COUNT:-3}"
USER_BASE="${USER_BASE:-900000}"

wait_dlt_id() { # issuanceAttemptId
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

printf '\n\033[1;35m##### 1. 장애 복구 후 DLT replay #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
restart_service
cid="$(./scripts/load/create_coupon.sh)"
COUPON_ID="$cid" COUNT=1 USER_BASE="$USER_BASE" ./scripts/load/part-5/force_dlt.sh >/dev/null
attempt_id="$(redis_cli GET "coupon:$cid:issuance-attempt:$((USER_BASE + 1))")"
wait_dlt_id "$attempt_id" >/dev/null || ng "DLT 로그 대기 실패"
curl -fsS -X POST "$BASE/admin/issuance/dlt/replay" >/dev/null
for _ in $(seq 1 30); do
  [[ "$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE issuance_attempt_id='$attempt_id'")" == "1" ]] && break
  sleep 1
done
check "같은 발급 시도로 DB 저장 완료" \
  "$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE issuance_attempt_id='$attempt_id'")" "1"

printf '\n\033[1;35m##### 2. 보상과 중복 호출 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
restart_service
curl -fsS -X POST "$BASE/metrics/compensation/reset" >/dev/null
cid="$(./scripts/load/create_coupon.sh)"
COUPON_ID="$cid" COUNT="$COUNT" USER_BASE="$USER_BASE" ./scripts/load/part-5/force_dlt.sh >/dev/null
for i in $(seq 1 "$COUNT"); do
  uid=$((USER_BASE + i))
  attempt_id="$(redis_cli GET "coupon:$cid:issuance-attempt:$uid")"
  dlt_id="$(wait_dlt_id "$attempt_id")" || { ng "DLT 로그 대기 실패"; continue; }
  compensate "$dlt_id" >/dev/null
  compensate "$dlt_id" >/dev/null
done
check "실제 보상 수" "$(curl -fsS "$BASE/metrics/compensation" | jq -r '.compensationTotal')" "$COUNT"
check "취소 이력 수" \
  "$(mysql_scalar "SELECT COUNT(*) FROM issuance_history WHERE coupon_id=$cid AND status='CANCELED'")" "$COUNT"
check "재고 복구" "$(redis_cli GET "coupon:$cid:stock")" "5000"
check "발급자 명단 복구" "$(redis_cli SCARD "coupon:$cid:users")" "0"

printf '\n\033[1;35m##### 3. 매진 직후 보상 #####\033[0m\n'
cid="$(curl -fsS -X POST "$BASE/api/coupons" -H 'Content-Type: application/json' \
  -d '{"name":"sellout-1","totalQuantity":1,"validityDays":7}' | jq -r '.id')"
COUPON_ID="$cid" COUNT=1 USER_BASE=0 ./scripts/load/part-5/force_dlt.sh >/dev/null
redis_cli SET "coupon:$cid:sold_out" 1 >/dev/null
attempt_id="$(redis_cli GET "coupon:$cid:issuance-attempt:1")"
dlt_id="$(wait_dlt_id "$attempt_id")" || { ng "DLT 로그 대기 실패"; exit 1; }
compensate "$dlt_id" >/dev/null
check "재고 1장 복구" "$(redis_cli GET "coupon:$cid:stock")" "1"
check "매진 표시 해제" "$(redis_cli EXISTS "coupon:$cid:sold_out")" "0"

summary "part-5-1"
