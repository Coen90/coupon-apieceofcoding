#!/usr/bin/env bash
# 쿠폰 한 건의 현재 숫자(총 수량, DB 발급 수, Redis 재고/명단/매진)를 읽어,
# Redis 와 DB 가 서로 맞는지 알기 쉽게 점검해 출력한다 (5단원 5.1).
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
stock="${stock:-0}"

db_sum=$(( issued + stock ))      # DB 기준으로 본 "이미 나간 수 + 남은 재고"
list_sum=$(( users + stock ))     # 명단 기준으로 본 "이미 나간 수 + 남은 재고"
db_gap=$(( total - db_sum ))      # DB 기준이 총 수량과 어긋난 정도
list_gap=$(( total - list_sum ))  # 명단 기준이 총 수량과 어긋난 정도

sold_label="없음"; [[ "$sold_out" == "1" ]] && sold_label="있음"
gap_text() { (( $1 == 0 )) && printf '총 %s 과 일치' "$total" || printf '총 %s 과 %s 차이' "$total" "$1"; }

printf '\n\033[1;36m===== 쿠폰 %s번 상태 점검 =====\033[0m\n' "$COUPON_ID"
printf '  총 수량              : %s\n' "$total"
printf '  DB 발급 수           : %s   (정상 %s건, 취소 %s건)\n' "$issued" "$issued_rows" "$canceled_rows"
printf '  Redis 남은 재고      : %s\n' "$stock"
printf '  Redis 발급자 명단    : %s명\n' "$users"
printf '  매진 표시            : %s\n' "$sold_label"
printf '\n'
printf "  '총 수량 = 이미 나간 수 + 남은 재고' 가 두 방식 모두 맞아야 정상입니다.\n"
printf '    DB 기준   : 발급 %s + 재고 %s = %s   ( %s )\n' "$issued" "$stock" "$db_sum" "$(gap_text "$db_gap")"
printf '    명단 기준 : 명단 %s + 재고 %s = %s   ( %s )\n' "$users" "$stock" "$list_sum" "$(gap_text "$list_gap")"

if (( db_gap == 0 && list_gap == 0 )); then
  printf '\033[1;32m  => 정상입니다. Redis 와 DB 의 숫자가 딱 맞아요.\033[0m\n'
elif (( db_gap > 0 && list_gap == 0 )); then
  printf '\033[1;33m  => DB 가 %s건 덜 세고 있어요. 발급은 됐는데 DB 저장이 실패한 경우라,\n' "$db_gap"
  printf '     자동으로는 못 고치고 사람이 확인해야 합니다.\033[0m\n'
elif (( db_gap == 0 && list_gap > 0 )); then
  printf '\033[1;33m  => Redis 발급자 명단에서 %s명이 비어 있어요. 명단만 날아간 경우라,\n' "$list_gap"
  printf '     DB 기록을 보고 자동으로 되살릴 수 있습니다.\033[0m\n'
else
  printf '\033[1;31m  => DB 와 명단이 둘 다 어긋났어요 (총 수량 설정 오류 등). 사람이 확인해야 합니다.\033[0m\n'
fi
