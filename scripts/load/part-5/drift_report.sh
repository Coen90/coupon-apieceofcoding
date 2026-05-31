#!/usr/bin/env bash
# 쿠폰 한 건의 4개 값과 두 등식 잔차를 출력한다 (5단원 5.1).
#
#   total_quantity = (DB issued_quantity) + (Redis stock)   ... ①  DB 측
#                  = (Redis users)         + (Redis stock)   ... ②  목록 측
#
# 사용:  COUPON_ID=1 scripts/load/part-5/drift_report.sh

set -euo pipefail
COUPON_ID="${COUPON_ID:?COUPON_ID 환경변수 필요}"
cd "$(dirname "$0")/../../.."

mysql_scalar() {
  docker compose exec -T -e MYSQL_PWD=coupon mysql mysql -ucoupon -BN coupon -e "$1"
}
redis_cli() {
  docker compose exec -T redis redis-cli "$@"
}

total="$(mysql_scalar "SELECT total_quantity FROM coupon WHERE id = $COUPON_ID")"
issued="$(mysql_scalar "SELECT issued_quantity FROM coupon WHERE id = $COUPON_ID")"
issued_rows="$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE coupon_id = $COUPON_ID AND status = 'ISSUED'")"
canceled_rows="$(mysql_scalar "SELECT COUNT(*) FROM issuance WHERE coupon_id = $COUPON_ID AND status = 'CANCELED'")"
stock="$(redis_cli GET "coupon:$COUPON_ID:stock")"
users="$(redis_cli SCARD "coupon:$COUPON_ID:users")"
sold_out="$(redis_cli EXISTS "coupon:$COUPON_ID:sold_out")"

# canceled_rows 는 CANCELED enum 이 아직 없는 브랜치(part-5-0)에서 0 이 나오면 그대로 둔다.
stock="${stock:-0}"

db_residual=$(( total - (issued + stock) ))     # ① 잔차: DB 측 불일치
list_residual=$(( total - (users + stock) ))    # ② 잔차: 목록 측 불일치

printf '\n\033[1;36m===== drift report (coupon %s) =====\033[0m\n' "$COUPON_ID"
printf '  total_quantity      = %s\n' "$total"
printf '  DB issued_quantity  = %s   (ISSUED rows %s, CANCELED rows %s)\n' "$issued" "$issued_rows" "$canceled_rows"
printf '  Redis stock         = %s\n' "$stock"
printf '  Redis users (SCARD) = %s\n' "$users"
printf '  Redis sold_out      = %s\n' "$sold_out"
printf '  ----\n'
printf '  DB 측 잔차   total-(issued+stock) = \033[1m%s\033[0m\n' "$db_residual"
printf '  목록 측 잔차 total-(users+stock)  = \033[1m%s\033[0m\n' "$list_residual"

if (( db_residual == 0 && list_residual == 0 )); then
  printf '\033[1;32m  → 정합 (두 등식 모두 성립)\033[0m\n'
elif (( db_residual > 0 && list_residual == 0 )); then
  printf '\033[1;33m  → DB 측 불일치 (+%s): Worker INSERT 실패 계열. 알람 대상 (자동 보정 불가)\033[0m\n' "$db_residual"
elif (( db_residual == 0 && list_residual > 0 )); then
  printf '\033[1;33m  → 목록 측 불일치 (+%s): Redis 사용자 목록 휘발 계열. 자동 보정 대상 (SADD)\033[0m\n' "$list_residual"
else
  printf '\033[1;31m  → 복합/음수 불일치 (DB측 %s, 목록측 %s): 사람이 확인\033[0m\n' "$db_residual" "$list_residual"
fi
