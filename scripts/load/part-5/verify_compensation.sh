#!/usr/bin/env bash
# part-5-1 검증: DLT 운영자 보상 + 보상 멱등성 + 매진 직후 보상 (5단원 7).
# 사전: docker compose 스택 + coupon-service(part-5-1 이미지) 가 떠 있어야 한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."
source ./scripts/load/part-5/_common.sh
COUNT="${COUNT:-10}"
USER_BASE="${USER_BASE:-900000}"

comp_metric() { curl -fsS "$BASE/metrics/compensation" | jq -r ".$1"; }

wait_dlt_id() { # issuanceAttemptId [timeout=30]
  for _ in $(seq 1 "${2:-30}"); do
    id=$(curl -fsS "$BASE/admin/issuance/dlt" | jq -r --arg attempt "$1" '.[] | select(.issuanceAttemptId == $attempt) | .id' | tail -1)
    [[ -n "$id" ]] && { printf '%s' "$id"; return 0; }
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
  dlt_id="$(wait_dlt_id "$issuance_attempt_id")" || ng "issuance_dlt_log에 메시지가 들어오길 기다리다 실패"
  curl -fsS -X POST "$BASE/admin/issuance/dlt/compensate?id=$dlt_id" >/dev/null
done
COUPON_ID="$CID" ./scripts/load/part-5/drift_report.sh

check "보상 처리된 건수" "$(comp_metric compensationTotal)" "$COUNT"
check "취소 이력 수" "$(mysql_scalar "SELECT COUNT(*) FROM issuance_history WHERE coupon_id=$CID AND status='CANCELED'")" "$COUNT"
check "재고가 원래 수량으로 복구됨" "$(redis_cli GET "coupon:$CID:stock")" "5000"
check "발급자 명단에서 제거됨(명)" "$(redis_cli SCARD "coupon:$CID:users")" "0"

############################################
printf '\n\033[1;35m##### 2단계: 같은 보상 요청을 두 번 보내도 한 번만 반영되나 (멱등성) #####\033[0m\n'
TEST_UID=950001
COUPON_ID="$CID" COUNT=1 USER_BASE=950000 ./scripts/load/part-5/force_dlt.sh >/dev/null
stock_before="$(redis_cli GET "coupon:$CID:stock")"
issuance_attempt_id="$(redis_cli GET "coupon:$CID:issuance-attempt:$TEST_UID")"
dlt_id="$(wait_dlt_id "$issuance_attempt_id")" || ng "issuance_dlt_log 대기 실패"
r1=$(curl -fsS -X POST "$BASE/admin/issuance/dlt/compensate?id=$dlt_id" | jq -r '.status')
r2=$(curl -fsS -X POST "$BASE/admin/issuance/dlt/compensate?id=$dlt_id" | jq -r '.status')
check "첫 호출은 보상 완료" "$r1" "COMPENSATED"
check "두 번째 호출도 같은 결과" "$r2" "COMPENSATED"
check "재고가 딱 1만 늘어남 (이중 보상 없음)" "$(redis_cli GET "coupon:$CID:stock")" "$(( stock_before + 1 ))"

############################################
printf '\n\033[1;35m##### 3단계: 매진된 직후 보상하면 매진이 풀리고 새 발급이 되나 #####\033[0m\n'
SCID=$(curl -fsS -X POST "$BASE/api/coupons" -H 'Content-Type: application/json' \
  -d '{"name":"sellout-1","totalQuantity":1,"validityDays":7}' | jq -r '.id')
printf '재고 1장짜리 쿠폰 번호 = %s\n' "$SCID"
COUPON_ID="$SCID" COUNT=1 USER_BASE=0 ./scripts/load/part-5/force_dlt.sh >/dev/null
redis_cli SET "coupon:$SCID:sold_out" 1 >/dev/null
check "발급 직후 '매진' 표시됨" "$(redis_cli EXISTS "coupon:$SCID:sold_out")" "1"
check "발급 직후 재고 0" "$(redis_cli GET "coupon:$SCID:stock")" "0"

issuance_attempt_id="$(redis_cli GET "coupon:$SCID:issuance-attempt:1")"
dlt_id="$(wait_dlt_id "$issuance_attempt_id")" || ng "issuance_dlt_log 대기 실패"
curl -fsS -X POST "$BASE/admin/issuance/dlt/compensate?id=$dlt_id" >/dev/null
check "보상 후 '매진' 표시 풀림" "$(redis_cli EXISTS "coupon:$SCID:sold_out")" "0"
check "보상 후 재고 1장 복구" "$(redis_cli GET "coupon:$SCID:stock")" "1"
check "매진 풀린 뒤 새 사용자 발급 성공(200)" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/coupons/$SCID/issue" -H "X-User-Id: 2")" "200"

summary "part-5-1"
