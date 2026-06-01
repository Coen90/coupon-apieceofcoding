#!/usr/bin/env bash
# part-5-1 검증: 실패 보관함(DLT) 자동 보상 + 보상 멱등성 + 매진 직후 보상 (5단원 7).
# 사전: docker compose 스택 + coupon-service(part-5-1 이미지) 가 떠 있어야 한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."
BASE="${BASE_URL:-http://localhost:8080}"
COUNT="${COUNT:-10}"

mysql_scalar() { docker compose exec -T -e MYSQL_PWD=coupon mysql mysql -ucoupon -BN coupon -e "$1"; }
redis_cli() { docker compose exec -T redis redis-cli "$@"; }
comp_metric() { curl -fsS "$BASE/metrics/compensation" | jq -r ".$1"; }

wait_service_ready() { # timeout
  for _ in $(seq 1 "${1:-60}"); do
    curl -fsS "$BASE/metrics/cache" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

fail=0
pass() { printf '\033[1;32m  ✓ 통과\033[0m %s\n' "$1"; }
ng()   { printf '\033[1;31m  ✗ 실패\033[0m %s\n' "$1"; fail=1; }
check() { # check "설명" 실제값 기대값
  if [[ "$2" == "$3" ]]; then pass "$1 ($2)"; else ng "$1 (실제 $2, 기대 $3)"; fi
}

wait_issued_row() { # couponId userId timeout
  local cid="$1" uid="$2" t="${3:-30}"
  for _ in $(seq 1 "$t"); do
    local n; n="$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE coupon_id=$cid AND user_id=$uid AND status='ISSUED'")"
    [[ "$n" == "1" ]] && return 0
    sleep 1
  done
  return 1
}

############################################
printf '\n\033[1;35m##### 1단계: 실패 보관함(DLT)에 쌓인 발급 %s건을 보상으로 자동으로 되돌리기 #####\033[0m\n' "$COUNT"
./scripts/load/reset.sh >/dev/null
# kafka 를 비우고, 보상 처리기가 깨끗한 상태에서 처음부터 다시 읽도록 서비스를 재시작한다.
./scripts/load/part-5/reset_kafka.sh
docker compose restart coupon-service >/dev/null
wait_service_ready 60 || { ng "coupon-service 재기동을 기다리다 실패"; exit 1; }
curl -fsS -X POST "$BASE/metrics/compensation/reset" >/dev/null
CID=$(./scripts/load/create_coupon.sh)
printf '만든 쿠폰 번호 = %s\n' "$CID"

COUPON_ID="$CID" COUNT="$COUNT" ./scripts/load/part-5/force_dlt.sh

printf '보상 처리기가 %s건을 다 되돌릴 때까지 기다리는 중...\n' "$COUNT"
for _ in $(seq 1 60); do
  total="$(comp_metric compensationTotal)"
  [[ "$total" == "$COUNT" ]] && break
  sleep 1
done
COUPON_ID="$CID" ./scripts/load/part-5/drift_report.sh

canceled="$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE coupon_id=$CID AND status='CANCELED'")"
stock="$(redis_cli GET "coupon:$CID:stock")"
users="$(redis_cli SCARD "coupon:$CID:users")"
check "보상 처리된 건수" "$(comp_metric compensationTotal)" "$COUNT"
check "취소로 기록된 발급 수" "$canceled" "$COUNT"
check "재고가 원래 수량으로 복구됨" "$stock" "5000"
check "발급자 명단에서 제거됨(명)" "$users" "0"

############################################
printf '\n\033[1;35m##### 2단계: 같은 보상 요청을 두 번 보내도 한 번만 반영되나 (멱등성) #####\033[0m\n'
TEST_UID=12345
curl -fsS -X POST "$BASE/api/coupons/$CID/issue" -H "X-User-Id: $TEST_UID" >/dev/null
wait_issued_row "$CID" "$TEST_UID" 30 || { ng "발급이 DB 에 기록되길 기다리다 실패"; }
stock_before="$(redis_cli GET "coupon:$CID:stock")"
IDEM="manual-idem-$CID"
r1=$(curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' \
  -d "{\"couponId\":$CID,\"userId\":$TEST_UID,\"compensationId\":\"$IDEM\"}" | jq -r '.compensated')
r2=$(curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' \
  -d "{\"couponId\":$CID,\"userId\":$TEST_UID,\"compensationId\":\"$IDEM\"}" | jq -r '.compensated')
stock_after="$(redis_cli GET "coupon:$CID:stock")"
printf '첫 호출 되돌림=%s, 두 번째 호출 되돌림=%s, 재고 %s -> %s\n' "$r1" "$r2" "$stock_before" "$stock_after"
check "첫 호출은 실제로 되돌림" "$r1" "true"
check "두 번째 같은 요청은 무시됨" "$r2" "false"
check "재고가 딱 1만 늘어남 (이중 보상 없음)" "$stock_after" "$(( stock_before + 1 ))"
check "무시된 중복 요청 수" "$(comp_metric compensationIdempotentHitTotal)" "1"

############################################
printf '\n\033[1;35m##### 3단계: 매진된 직후 보상하면 매진이 풀리고 새 발급이 되나 #####\033[0m\n'
SCID=$(curl -fsS -X POST "$BASE/api/coupons" -H 'Content-Type: application/json' \
  -d '{"name":"sellout-1","totalQuantity":1,"validityDays":7}' | jq -r '.id')
printf '재고 1장짜리 쿠폰 번호 = %s\n' "$SCID"
curl -fsS -X POST "$BASE/api/coupons/$SCID/issue" -H "X-User-Id: 1" >/dev/null
wait_issued_row "$SCID" 1 30 || true
sold_before="$(redis_cli EXISTS "coupon:$SCID:sold_out")"
stock_s0="$(redis_cli GET "coupon:$SCID:stock")"
check "발급 직후 '매진' 표시됨" "$sold_before" "1"
check "발급 직후 재고 0" "$stock_s0" "0"

curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' \
  -d "{\"couponId\":$SCID,\"userId\":1,\"compensationId\":\"manual-sellout-$SCID\"}" >/dev/null
sold_after="$(redis_cli EXISTS "coupon:$SCID:sold_out")"
stock_s1="$(redis_cli GET "coupon:$SCID:stock")"
check "보상 후 '매진' 표시 풀림" "$sold_after" "0"
check "보상 후 재고 1장 복구" "$stock_s1" "1"

# 매진이 풀렸으니 새 사용자가 발급 가능해야 한다.
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/coupons/$SCID/issue" -H "X-User-Id: 2")
check "매진 풀린 뒤 새 사용자 발급 성공(200)" "$code" "200"

############################################
if (( fail == 0 )); then
  printf '\n\033[1;32m===== part-5-1 검증: 모두 통과 =====\033[0m\n'
else
  printf '\n\033[1;31m===== part-5-1 검증: 실패한 항목이 있습니다 =====\033[0m\n'; exit 1
fi
