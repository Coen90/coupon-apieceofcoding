#!/usr/bin/env bash
# part-5-2 검증: reconcile 자동 보정(목록 휘발/매진 플래그) + DB 측 알람 (5단원 5, 7).
# 사전: docker compose 스택 + coupon-service(part-5-2 이미지) 가 떠 있어야 한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."
BASE="${BASE_URL:-http://localhost:8080}"
COUNT="${COUNT:-10}"

mysql_scalar() { docker compose exec -T -e MYSQL_PWD=coupon mysql mysql -ucoupon -BN coupon -e "$1"; }
redis_cli() { docker compose exec -T redis redis-cli "$@"; }
recon_metric() { curl -fsS "$BASE/metrics/reconcile" | jq -r ".$1"; }
run_reconcile() { curl -fsS -X POST "$BASE/admin/reconcile/run"; }
reset_recon_metrics() { curl -fsS -X POST "$BASE/metrics/reconcile/reset" >/dev/null; }
wait_service_ready() {
  for _ in $(seq 1 60); do curl -fsS "$BASE/metrics/cache" >/dev/null 2>&1 && return 0; sleep 1; done
  return 1
}

fail=0
pass() { printf '\033[1;32mPASS\033[0m %s\n' "$1"; }
ng()   { printf '\033[1;31mFAIL\033[0m %s\n' "$1"; fail=1; }
check() { if [[ "$2" == "$3" ]]; then pass "$1 ($2)"; else ng "$1 (got $2, want $3)"; fi; }

# 보상 처리기가 stray DLT 를 소비해 reconcile 검증을 흔들지 않도록, kafka 를 비우고 재기동.
./scripts/load/part-5/reset_kafka.sh
docker compose restart coupon-service >/dev/null
wait_service_ready || { ng "coupon-service 재기동 대기 실패"; exit 1; }

############################################
printf '\n\033[1;35m##### Phase A: Redis 사용자 목록 휘발 -> SADD 자동 보정 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_recon_metrics
CID=$(./scripts/load/create_coupon.sh)
COUPON_ID="$CID" COUNT="$COUNT" ./scripts/load/part-5/force_db_only.sh >/dev/null
printf '주입 후 목록 측 잔차 확인:\n'; COUPON_ID="$CID" ./scripts/load/part-5/drift_report.sh
run_reconcile | jq -c '.'; echo
users_after="$(redis_cli SCARD "coupon:$CID:users")"
check "휘발된 사용자 목록 SADD 복구 (users=주입수)" "$users_after" "$COUNT"
check "reconcile_auto_fix_total" "$(recon_metric reconcileAutoFixTotal)" "1"
check "redis_db_drift (DB 측은 정합)" "$(recon_metric redisDbDrift)" "0"

############################################
printf '\n\033[1;35m##### Phase B: DB 측 불일치 -> 알람만 (자동 보정 불가) #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_recon_metrics
CID2=$(./scripts/load/create_coupon.sh)
# PRODUCE_DLT=0: DLT 메시지 없이 순수 DB 측 drift 만. 보상 처리기가 소비할 게 없어 격리된다.
COUPON_ID="$CID2" COUNT="$COUNT" PRODUCE_DLT=0 ./scripts/load/part-5/force_dlt.sh >/dev/null
issued_before="$(mysql_scalar "SELECT issued_quantity FROM coupon WHERE id=$CID2")"
stock_before="$(redis_cli GET "coupon:$CID2:stock")"
run_reconcile | jq -c '.'; echo
check "redis_db_drift 알람" "$(recon_metric redisDbDrift)" "$COUNT"
check "자동 보정 안 함 (auto_fix=0)" "$(recon_metric reconcileAutoFixTotal)" "0"
check "DB issued_quantity 그대로" "$(mysql_scalar "SELECT issued_quantity FROM coupon WHERE id=$CID2")" "$issued_before"
check "Redis stock 그대로" "$(redis_cli GET "coupon:$CID2:stock")" "$stock_before"

############################################
printf '\n\033[1;35m##### Phase C: 매진 플래그 잘못 살아남음 -> DEL 자동 보정 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_recon_metrics
CID3=$(./scripts/load/create_coupon.sh)
redis_cli SET "coupon:$CID3:sold_out" 1 >/dev/null   # 재고는 5000 인데 플래그만 살아있는 상태 주입
sold_before="$(redis_cli EXISTS "coupon:$CID3:sold_out")"
run_reconcile | jq -c '.'; echo
sold_after="$(redis_cli EXISTS "coupon:$CID3:sold_out")"
check "보정 전 매진 플래그 있음" "$sold_before" "1"
check "보정 후 매진 플래그 해제" "$sold_after" "0"
check "reconcile_auto_fix_total" "$(recon_metric reconcileAutoFixTotal)" "1"

############################################
neg="$(recon_metric stockNegative)"
check "재고 음수 발생 (항상 0)" "$neg" "0"
check "reconcile_false_alarm_total (이번 시나리오는 0)" "$(recon_metric reconcileFalseAlarmTotal)" "0"

if (( fail == 0 )); then
  printf '\n\033[1;32m===== part-5-2 검증 전체 PASS =====\033[0m\n'
else
  printf '\n\033[1;31m===== part-5-2 검증 실패 항목 있음 =====\033[0m\n'; exit 1
fi
