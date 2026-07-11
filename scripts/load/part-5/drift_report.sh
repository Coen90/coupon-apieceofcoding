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

# 없는 쿠폰이면 빈 값이 산술에서 0 으로 처리돼 '정상'으로 오보고하므로, 먼저 존재를 확인한다.
if [[ "$(mysql_scalar "SELECT COUNT(*) FROM coupon WHERE id = $COUPON_ID")" != "1" ]]; then
  printf '쿠폰 %s 가 DB 에 없습니다. COUPON_ID 를 확인하세요.\n' "$COUPON_ID" >&2
  exit 1
fi

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
  printf '\033[1;33m  => DB 가 %s건 덜 세고 있어요. 발급은 됐는데 DB 저장이 실패한 경우입니다\n' "$db_gap"
  printf '     (사용자에겐 발급 성공 응답이 이미 나갔어요).\n'
  printf '     - 보통은 자동 복구됩니다: part-5-1 보상 처리기(Compensator)가 실패 메시지 보관함(DLT)을\n'
  printf '       읽어 자동으로 되돌려요. 잠시 뒤 이 점검을 다시 돌려 차이가 0 으로 줄었는지 확인하세요.\n'
  printf '       (참고로 reconcile 은 DB 측을 함부로 자동 보정하지 않고 알람만 띄웁니다. 복구는 보상이 맡아요.)\n'
  printf '     - 차이가 안 줄면 (DLT 에 메시지가 없거나 보상 처리기가 멈춘 경우) 사람이 직접 처리합니다.\n'
  printf '       [사람이 할 일]\n'
  printf '         1) scripts/load/part-3/kafka_dlt_peek.sh 로 무엇이 왜 실패했는지 확인\n'
  printf '         2) 일시적 실패(DB 잠깐 장애 등)면 → 재처리: 그 발급을 DB 에 다시 저장해 사용자가 쿠폰 유지\n'
  printf '            영구 실패(깨진 데이터, 마감 지남, 정책상 취소)면 → 보상으로 되돌림:\n'
  printf "            curl -X POST localhost:8080/admin/compensate -H 'Content-Type: application/json' \\\\\n"
  printf '              -d '"'"'{"couponId":%s,"userId":<발급자번호>,"operationId":"<원본 발급 operationId>"}'"'"'\n' "$COUPON_ID"
  printf '            (재고를 1 되돌리고, 발급자 명단에서 빼고, 발급 기록을 취소로 남김)\033[0m\n'
elif (( db_gap == 0 && list_gap > 0 )); then
  printf '\033[1;33m  => Redis 발급자 명단에서 %s명이 비어 있어요. 명단만 날아간 경우라,\n' "$list_gap"
  printf '     DB 기록을 보고 자동으로 되살릴 수 있습니다 (part-5-2 점검 배치가 SADD 로 자동 복구).\033[0m\n'
else
  printf '\033[1;31m  => DB 와 명단이 둘 다 어긋났어요. 자동으로는 못 고치니 사람이 확인합니다.\n'
  printf '     [사람이 할 일]\n'
  printf '       1) 먼저 쿠폰 총 수량(total_quantity) 설정값이 맞는지 확인한다 (오설정이 가장 흔한 원인).\n'
  printf '       2) 발급된 쿠폰을 시스템이 도로 빼앗는 방향이면 절대 자동화하지 말고,\n'
  printf '          DB(issuance)와 Redis(stock/users)를 직접 대조해 어느 쪽이 맞는지 정한 뒤 손으로 맞춘다.\033[0m\n'
fi
