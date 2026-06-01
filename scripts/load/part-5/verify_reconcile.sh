#!/usr/bin/env bash
# part-5-2 검증: reconcile 자동 보정(명단 휘발/매진 표시) + DB 측 알람 (5단원 5, 7).
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
pass() { printf '\033[1;32m  ✓ 통과\033[0m %s\n' "$1"; }
ng()   { printf '\033[1;31m  ✗ 실패\033[0m %s\n' "$1"; fail=1; }
check() { if [[ "$2" == "$3" ]]; then pass "$1 ($2)"; else ng "$1 (실제 $2, 기대 $3)"; fi; }

# 보상 처리기가 남은 실패 메시지를 소비해 reconcile 검증을 흔들지 않도록, kafka 를 비우고 재시작.
./scripts/load/part-5/reset_kafka.sh
docker compose restart coupon-service >/dev/null
wait_service_ready || { ng "coupon-service 재기동을 기다리다 실패"; exit 1; }

############################################
printf '\n\033[1;35m##### A단계: 발급자 명단이 날아간 경우 -> 점검 배치가 다시 채워주나 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_recon_metrics
CID=$(./scripts/load/create_coupon.sh)
COUPON_ID="$CID" COUNT="$COUNT" ./scripts/load/part-5/force_db_only.sh >/dev/null
printf '주입 직후 상태:\n'; COUPON_ID="$CID" ./scripts/load/part-5/drift_report.sh
printf '점검 배치 1회 실행 결과: '; run_reconcile | jq -c '.'
users_after="$(redis_cli SCARD "coupon:$CID:users")"
check "날아간 발급자 명단을 자동으로 되살림(명)" "$users_after" "$COUNT"
check "자동으로 고친 횟수" "$(recon_metric reconcileAutoFixTotal)" "1"
check "DB 쪽 어긋남(0이면 정상)" "$(recon_metric redisDbDrift)" "0"

############################################
printf '\n\033[1;35m##### B단계: DB 쪽이 어긋난 경우 -> 알람만, 함부로 자동으로 못 고침 #####\033[0m\n'
./scripts/load/reset.sh >/dev/null
reset_recon_metrics
CID2=$(./scripts/load/create_coupon.sh)
# PRODUCE_DLT=0: 실패 메시지는 안 만들고 Redis 만 어긋난 순수 DB 측 차이. 보상 처리기가 소비할 게 없어 격리된다.
COUPON_ID="$CID2" COUNT="$COUNT" PRODUCE_DLT=0 ./scripts/load/part-5/force_dlt.sh >/dev/null
issued_before="$(mysql_scalar "SELECT issued_quantity FROM coupon WHERE id=$CID2")"
stock_before="$(redis_cli GET "coupon:$CID2:stock")"
printf '점검 배치 1회 실행 결과: '; run_reconcile | jq -c '.'
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
sold_before="$(redis_cli EXISTS "coupon:$CID3:sold_out")"
printf '점검 배치 1회 실행 결과: '; run_reconcile | jq -c '.'
sold_after="$(redis_cli EXISTS "coupon:$CID3:sold_out")"
check "고치기 전: 매진 표시 있음" "$sold_before" "1"
check "고친 후: 매진 표시 꺼짐" "$sold_after" "0"
check "자동으로 고친 횟수" "$(recon_metric reconcileAutoFixTotal)" "1"

############################################
check "재고가 음수로 내려간 쿠폰 수(항상 0)" "$(recon_metric stockNegative)" "0"
check "잘못된 경보 횟수(이번엔 0)" "$(recon_metric reconcileFalseAlarmTotal)" "0"

if (( fail == 0 )); then
  printf '\n\033[1;32m===== part-5-2 검증: 모두 통과 =====\033[0m\n'
else
  printf '\n\033[1;31m===== part-5-2 검증: 실패한 항목이 있습니다 =====\033[0m\n'; exit 1
fi
