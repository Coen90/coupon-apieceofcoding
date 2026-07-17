#!/usr/bin/env bash
# part-5-2 검증: reconcile 자동 보정(명단 휘발/매진 표시) + DB 측 알람 (5단원 5, 7).
# 사전: docker compose 스택 + coupon-service(part-5-2 이미지) 가 떠 있어야 한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."
source ./scripts/load/part-5/_common.sh
COUNT="${COUNT:-10}"

recon_metric() { curl -fsS "$BASE/metrics/reconcile" | jq -r ".$1"; }
reset_recon_metrics() { curl -fsS -X POST "$BASE/metrics/reconcile/reset" >/dev/null; }
run_reconcile() { printf '점검 배치 1회 실행 결과: '; curl -fsS -X POST "$BASE/admin/reconcile/run" | jq -c '.'; }

# kafka 를 비우고, 검증 동안 최근 대사와 일일 전수 audit이 수동 트리거와 겹치지 않게 재기동.
restart_service 3600000

############################################
printf '\n\033[1;35m##### A단계: 발급자 명단이 날아간 경우 -> 점검 배치가 다시 채워주나 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_recon_metrics
CID=$(./scripts/load/create_coupon.sh)
COUPON_ID="$CID" COUNT="$COUNT" ./scripts/load/part-5/force_db_only.sh >/dev/null
printf '주입 직후 상태:\n'; COUPON_ID="$CID" ./scripts/load/part-5/drift_report.sh
run_reconcile
check "날아간 발급자 명단을 자동으로 되살림(명)" "$(redis_cli SCARD "coupon:$CID:users")" "$COUNT"
check "자동으로 고친 횟수" "$(recon_metric reconcileAutoFixTotal)" "1"
check "DB 쪽 어긋남(0이면 정상)" "$(recon_metric redisDbDrift)" "0"

############################################
printf '\n\033[1;35m##### B단계: DB 쪽이 어긋난 경우 -> 알람만, 함부로 자동으로 못 고침 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_recon_metrics
CID2=$(./scripts/load/create_coupon.sh)
# PRODUCE_DLT=0: 실패 메시지 없이 Redis 만 어긋난 순수 DB 측 차이 (운영자 DLT 처리가 개입하지 않음).
COUPON_ID="$CID2" COUNT="$COUNT" PRODUCE_DLT=0 ./scripts/load/part-5/force_dlt.sh >/dev/null
issued_before="$(mysql_scalar "SELECT issued_quantity FROM coupon WHERE id=$CID2")"
stock_before="$(redis_cli GET "coupon:$CID2:stock")"
run_reconcile
check "DB 쪽 어긋남 감지(건)" "$(recon_metric redisDbDrift)" "$COUNT"
check "자동으로 고치지 않음" "$(recon_metric reconcileAutoFixTotal)" "0"
check "DB 발급 수 그대로 둠" "$(mysql_scalar "SELECT issued_quantity FROM coupon WHERE id=$CID2")" "$issued_before"
check "Redis 재고 그대로 둠" "$(redis_cli GET "coupon:$CID2:stock")" "$stock_before"

############################################
printf '\n\033[1;35m##### C단계: 재고는 남았는데 매진 표시가 잘못 켜진 경우 -> 자동으로 끄기 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_recon_metrics
CID3=$(./scripts/load/create_coupon.sh)
redis_cli SET "coupon:$CID3:sold_out" 1 >/dev/null   # 재고는 5000 인데 매진 표시만 켜진 상태 주입
# 발급 활동이 있었던 쿠폰으로 간주해 대사 발급 시각 인덱스에 등록 (grace 보다 과거 시각).
redis_cli EVAL \
  "redis.call('ZADD', KEYS[1], (tonumber(redis.call('TIME')[1]) - 20) * 1000, ARGV[1])" \
  1 "coupon:reconcile:recent" "$CID3" >/dev/null
check "고치기 전: 매진 표시 있음" "$(redis_cli EXISTS "coupon:$CID3:sold_out")" "1"
run_reconcile
check "고친 후: 매진 표시 꺼짐" "$(redis_cli EXISTS "coupon:$CID3:sold_out")" "0"
check "자동으로 고친 횟수" "$(recon_metric reconcileAutoFixTotal)" "1"

############################################
check "재고가 음수로 내려간 쿠폰 수(항상 0)" "$(recon_metric stockNegative)" "0"

summary "part-5-2"
