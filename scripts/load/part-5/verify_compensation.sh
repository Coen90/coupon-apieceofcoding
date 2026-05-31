#!/usr/bin/env bash
# part-5-1 검증: DLT 보상 자동 처리 + 보상 멱등성 + 매진 직후 보상 (5단원 7).
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
pass() { printf '\033[1;32mPASS\033[0m %s\n' "$1"; }
ng()   { printf '\033[1;31mFAIL\033[0m %s\n' "$1"; fail=1; }
check() { # check "label" actual expected
  if [[ "$2" == "$3" ]]; then pass "$1 ($2)"; else ng "$1 (got $2, want $3)"; fi
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
printf '\n\033[1;35m##### Phase 1: DLT 보상 자동 처리 (%s건) #####\033[0m\n' "$COUNT"
./scripts/load/reset.sh >/dev/null
# kafka 를 클린 상태로 만들고, 보상 처리기가 클린 DLT 를 earliest 부터 재구독하도록 서비스 재시작.
./scripts/load/part-5/reset_kafka.sh
docker compose restart coupon-service >/dev/null
wait_service_ready 60 || { ng "coupon-service 재기동 대기 실패"; exit 1; }
curl -fsS -X POST "$BASE/metrics/compensation/reset" >/dev/null
CID=$(./scripts/load/create_coupon.sh)
printf 'coupon_id=%s\n' "$CID"

COUPON_ID="$CID" COUNT="$COUNT" ./scripts/load/part-5/force_dlt.sh

printf '보상 처리기 드레인 대기 (compensation_total == %s)...\n' "$COUNT"
for _ in $(seq 1 60); do
  total="$(comp_metric compensationTotal)"
  [[ "$total" == "$COUNT" ]] && break
  sleep 1
done
COUPON_ID="$CID" ./scripts/load/part-5/drift_report.sh

canceled="$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE coupon_id=$CID AND status='CANCELED'")"
stock="$(redis_cli GET "coupon:$CID:stock")"
users="$(redis_cli SCARD "coupon:$CID:users")"
check "compensation_total" "$(comp_metric compensationTotal)" "$COUNT"
check "CANCELED 행 수" "$canceled" "$COUNT"
check "재고 복구 (stock=total)" "$stock" "5000"
check "사용자 목록 SREM 됨" "$users" "0"

############################################
printf '\n\033[1;35m##### Phase 2: 보상 멱등성 (같은 compensationId 2회) #####\033[0m\n'
TEST_UID=12345
curl -fsS -X POST "$BASE/api/coupons/$CID/issue" -H "X-User-Id: $TEST_UID" >/dev/null
wait_issued_row "$CID" "$TEST_UID" 30 || { ng "발급 worker INSERT 대기 실패"; }
stock_before="$(redis_cli GET "coupon:$CID:stock")"
IDEM="manual-idem-$CID"
r1=$(curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' \
  -d "{\"couponId\":$CID,\"userId\":$TEST_UID,\"compensationId\":\"$IDEM\"}" | jq -r '.compensated')
r2=$(curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' \
  -d "{\"couponId\":$CID,\"userId\":$TEST_UID,\"compensationId\":\"$IDEM\"}" | jq -r '.compensated')
stock_after="$(redis_cli GET "coupon:$CID:stock")"
printf '1회차 compensated=%s, 2회차 compensated=%s, stock %s -> %s\n' "$r1" "$r2" "$stock_before" "$stock_after"
check "1회차는 실제 보상" "$r1" "true"
check "2회차는 멱등 hit" "$r2" "false"
check "재고는 +1 만 (이중 보상 없음)" "$stock_after" "$(( stock_before + 1 ))"
check "idempotent_hit_total" "$(comp_metric compensationIdempotentHitTotal)" "1"

############################################
printf '\n\033[1;35m##### Phase 3: 매진 직후 보상 -> 매진 플래그 해제 #####\033[0m\n'
SCID=$(curl -fsS -X POST "$BASE/api/coupons" -H 'Content-Type: application/json' \
  -d '{"name":"sellout-1","totalQuantity":1,"validityDays":7}' | jq -r '.id')
printf 'small coupon_id=%s (total 1)\n' "$SCID"
curl -fsS -X POST "$BASE/api/coupons/$SCID/issue" -H "X-User-Id: 1" >/dev/null
wait_issued_row "$SCID" 1 30 || true
sold_before="$(redis_cli EXISTS "coupon:$SCID:sold_out")"
stock_s0="$(redis_cli GET "coupon:$SCID:stock")"
check "발급 직후 매진 플래그 설정" "$sold_before" "1"
check "발급 직후 재고 0" "$stock_s0" "0"

curl -fsS -X POST "$BASE/admin/compensate" -H 'Content-Type: application/json' \
  -d "{\"couponId\":$SCID,\"userId\":1,\"compensationId\":\"manual-sellout-$SCID\"}" >/dev/null
sold_after="$(redis_cli EXISTS "coupon:$SCID:sold_out")"
stock_s1="$(redis_cli GET "coupon:$SCID:stock")"
check "보상 후 매진 플래그 해제" "$sold_after" "0"
check "보상 후 재고 1 복구" "$stock_s1" "1"

# 매진이 풀렸으니 새 사용자가 발급 가능해야 한다.
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/coupons/$SCID/issue" -H "X-User-Id: 2")
check "보상 후 새 발급 성공(200)" "$code" "200"

############################################
if (( fail == 0 )); then
  printf '\n\033[1;32m===== part-5-1 검증 전체 PASS =====\033[0m\n'
else
  printf '\n\033[1;31m===== part-5-1 검증 실패 항목 있음 =====\033[0m\n'; exit 1
fi
