#!/usr/bin/env bash
# part-5-1 검증: DLT 운영자 보상 + 보상 멱등성 + 매진 직후 보상 (5단원 7).
# 사전: docker compose 스택 + coupon-service(part-5-1 이미지) 가 떠 있어야 한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."
source ./scripts/load/part-5/_common.sh
COUNT="${COUNT:-10}"
USER_BASE="${USER_BASE:-900000}"

comp_metric() { curl -fsS "$BASE/metrics/compensation" | jq -r ".$1"; }

wait_issued_row() { # couponId userId [timeout=30]
  for _ in $(seq 1 "${3:-30}"); do
    [[ "$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE coupon_id=$1 AND user_id=$2 AND status='ISSUED'")" == "1" ]] && return 0
    sleep 1
  done
  return 1
}

############################################
printf '\n\033[1;35m##### 1단계: DLT를 확인한 운영자가 발급 %s건을 보상하기 #####\033[0m\n' "$COUNT"
./scripts/load/reset.sh >/dev/null
restart_service              # kafka를 비우고 DLT를 새로 만든다
curl -fsS -X POST "$BASE/metrics/compensation/reset" >/dev/null
CID=$(./scripts/load/create_coupon.sh)
printf '만든 쿠폰 번호 = %s\n' "$CID"

COUPON_ID="$CID" COUNT="$COUNT" ./scripts/load/part-5/force_dlt.sh

printf 'DLT를 확인했다고 가정하고 issuanceAttemptId별 수동 보상을 실행하는 중...\n'
for i in $(seq 1 "$COUNT"); do
  uid=$((USER_BASE + i))
  issuance_attempt_id="$(redis_cli GET "coupon:$CID:issuance-attempt:$uid")"
  curl -fsS -X POST "$BASE/admin/compensate" \
    -H 'Content-Type: application/json' \
    -d "{\"couponId\":$CID,\"userId\":$uid,\"issuanceAttemptId\":\"$issuance_attempt_id\"}" >/dev/null
done
COUPON_ID="$CID" ./scripts/load/part-5/drift_report.sh

check "보상 처리된 건수" "$(comp_metric compensationTotal)" "$COUNT"
check "취소로 기록된 발급 수" "$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE coupon_id=$CID AND status='CANCELED'")" "$COUNT"
check "재고가 원래 수량으로 복구됨" "$(redis_cli GET "coupon:$CID:stock")" "5000"
check "발급자 명단에서 제거됨(명)" "$(redis_cli SCARD "coupon:$CID:users")" "0"

############################################
printf '\n\033[1;35m##### 2단계: 같은 보상 요청을 두 번 보내도 한 번만 반영되나 (멱등성) #####\033[0m\n'
TEST_UID=12345
curl -fsS -X POST "$BASE/api/coupons/$CID/issue" -H "X-User-Id: $TEST_UID" >/dev/null
wait_issued_row "$CID" "$TEST_UID" || ng "발급이 DB 에 기록되길 기다리다 실패"
stock_before="$(redis_cli GET "coupon:$CID:stock")"
issuance_attempt_id="$(mysql_scalar "SELECT issuance_attempt_id FROM issuance WHERE coupon_id=$CID AND user_id=$TEST_UID")"
body="{\"couponId\":$CID,\"userId\":$TEST_UID,\"issuanceAttemptId\":\"$issuance_attempt_id\"}"
r1=$(curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' -d "$body" | jq -r '.compensated')
r2=$(curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' -d "$body" | jq -r '.compensated')
check "첫 호출은 실제로 되돌림" "$r1" "true"
check "두 번째 같은 요청은 무시됨" "$r2" "false"
check "재고가 딱 1만 늘어남 (이중 보상 없음)" "$(redis_cli GET "coupon:$CID:stock")" "$(( stock_before + 1 ))"
check "무시된 중복 요청 수" "$(comp_metric compensationIdempotentHitTotal)" "1"

############################################
printf '\n\033[1;35m##### 3단계: 매진된 직후 보상하면 매진이 풀리고 새 발급이 되나 #####\033[0m\n'
SCID=$(curl -fsS -X POST "$BASE/api/coupons" -H 'Content-Type: application/json' \
  -d '{"name":"sellout-1","totalQuantity":1,"validityDays":7}' | jq -r '.id')
printf '재고 1장짜리 쿠폰 번호 = %s\n' "$SCID"
curl -fsS -X POST "$BASE/api/coupons/$SCID/issue" -H "X-User-Id: 1" >/dev/null
wait_issued_row "$SCID" 1 || true
check "발급 직후 '매진' 표시됨" "$(redis_cli EXISTS "coupon:$SCID:sold_out")" "1"
check "발급 직후 재고 0" "$(redis_cli GET "coupon:$SCID:stock")" "0"

curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' \
  -d "{\"couponId\":$SCID,\"userId\":1,\"issuanceAttemptId\":\"$(mysql_scalar "SELECT issuance_attempt_id FROM issuance WHERE coupon_id=$SCID AND user_id=1")\"}" >/dev/null
check "보상 후 '매진' 표시 풀림" "$(redis_cli EXISTS "coupon:$SCID:sold_out")" "0"
check "보상 후 재고 1장 복구" "$(redis_cli GET "coupon:$SCID:stock")" "1"
check "매진 풀린 뒤 새 사용자 발급 성공(200)" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/coupons/$SCID/issue" -H "X-User-Id: 2")" "200"

summary "part-5-1"
