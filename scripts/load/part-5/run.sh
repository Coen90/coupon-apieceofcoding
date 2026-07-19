#!/usr/bin/env bash
# part-5-2: 대사의 자동 보정과 알람을 한 번에 검증한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."
source ./scripts/load/part-5/_common.sh

COUNT="${COUNT:-10}"
export COUPON_RECONCILE_AUDIT_CRON="0 0 0 1 1 *"

metric() { curl -fsS "$BASE/metrics/reconcile" | jq -r ".$1"; }
reset_metrics() { curl -fsS -X POST "$BASE/metrics/reconcile/reset" >/dev/null; }
reconcile() { curl -fsS -X POST "$BASE/admin/reconcile/run" >/dev/null; }

# 자동 스케줄이 수동 검증과 겹치지 않게 한 시간 간격으로 재기동한다.
restart_service 3600000

printf '\n\033[1;35m##### 1. Redis users 누락 자동 보정 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_metrics
cid="$(./scripts/load/create_coupon.sh)"
COUPON_ID="$cid" COUNT="$COUNT" ./scripts/load/part-5/force_db_only.sh >/dev/null
reconcile
check "발급자 명단 복구" "$(redis_cli SCARD "coupon:$cid:users")" "$COUNT"
check "자동 보정 횟수" "$(metric reconcileAutoFixTotal)" "1"
check "DB 측 불일치" "$(metric redisDbDrift)" "0"

printf '\n\033[1;35m##### 2. DB 측 불일치는 알람만 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_metrics
cid="$(./scripts/load/create_coupon.sh)"
COUPON_ID="$cid" COUNT="$COUNT" ./scripts/load/part-5/force_dlt.sh >/dev/null
issued_before="$(mysql_scalar "SELECT issued_quantity FROM coupon WHERE id=$cid")"
stock_before="$(redis_cli GET "coupon:$cid:stock")"
reconcile
check "DB 측 불일치 감지" "$(metric redisDbDrift)" "$COUNT"
check "자동 보정하지 않음" "$(metric reconcileAutoFixTotal)" "0"
check "DB 발급 수 유지" "$(mysql_scalar "SELECT issued_quantity FROM coupon WHERE id=$cid")" "$issued_before"
check "Redis 재고 유지" "$(redis_cli GET "coupon:$cid:stock")" "$stock_before"

printf '\n\033[1;35m##### 3. 잘못된 매진 표시 자동 해제 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_metrics
cid="$(./scripts/load/create_coupon.sh)"
redis_cli SET "coupon:$cid:sold_out" 1 >/dev/null
reconcile
check "매진 표시 해제" "$(redis_cli EXISTS "coupon:$cid:sold_out")" "0"
check "자동 보정 횟수" "$(metric reconcileAutoFixTotal)" "1"
check "재고 음수" "$(metric stockNegative)" "0"

summary "part-5-2"
