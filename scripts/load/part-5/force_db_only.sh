#!/usr/bin/env bash
set -euo pipefail
COUPON_ID="${COUPON_ID:?COUPON_ID 환경변수 필요}"
COUNT="${COUNT:-10}"
USER_BASE="${USER_BASE:-800000}"
cd "$(dirname "$0")/../../.."

mysql_exec() { docker compose exec -T -e MYSQL_PWD=coupon mysql mysql -ucoupon -BN coupon -e "$1"; }
redis_cli() { docker compose exec -T redis redis-cli "$@"; }

printf '\n\033[1;36m===== [상황 만들기] DB 엔 발급됐는데 Redis 명단이 날아간 경우 %s건 (force_db_only) =====\033[0m\n' \
  "$COUNT"

values=""
history_values=""
first=0; last=0
has_attempt_id="$(mysql_exec "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='coupon' AND table_name='issuance' AND column_name='issuance_attempt_id'")"
for i in $(seq 1 "$COUNT"); do
  uid=$(( USER_BASE + i ))
  if (( i == 1 )); then first=$uid; fi
  last=$uid
  [[ -n "$values" ]] && values+=","
  if [[ "$has_attempt_id" == "1" ]]; then
    issuance_attempt_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
    values+="($uid, $COUPON_ID, '$issuance_attempt_id', 'ISSUED', NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY))"
    [[ -n "$history_values" ]] && history_values+=","
    history_values+="('$issuance_attempt_id', $uid, $COUPON_ID, 'ISSUED', 'FORCE_DB_ONLY', NOW())"
  else
    values+="($uid, $COUPON_ID, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ISSUED')"
  fi
done
if [[ "$(mysql_exec "SELECT COUNT(*) FROM issuance WHERE coupon_id = $COUPON_ID AND user_id BETWEEN $first AND $last")" != "0" ]]; then
  printf '\033[1;33m  이미 주입된 쿠폰입니다. 먼저 ./scripts/load/reset.sh 로 초기화한 뒤 다시 실행하세요.\033[0m\n'
  exit 1
fi

if [[ "$has_attempt_id" == "1" ]]; then
  mysql_exec "INSERT INTO issuance (user_id, coupon_id, issuance_attempt_id, status, issued_at, expires_at) VALUES $values;
              INSERT INTO issuance_history (issuance_attempt_id, user_id, coupon_id, status, reason, recorded_at) VALUES $history_values;
              UPDATE coupon SET issued_quantity = issued_quantity + $COUNT WHERE id = $COUPON_ID;"
else
  mysql_exec "INSERT INTO issuance (user_id, coupon_id, issued_at, expires_at, status) VALUES $values;
              UPDATE coupon SET issued_quantity = issued_quantity + $COUNT WHERE id = $COUPON_ID;"
fi
printf '  DB: 발급 기록 %s건 추가 + 발급 수 %s 증가 (%s~%s번)\n' \
  "$COUNT" "$COUNT" "$first" "$last"

redis_cli DECRBY "coupon:$COUPON_ID:stock" "$COUNT" >/dev/null
printf '  Redis: 재고만 %s 줄이고 발급자 명단에는 안 넣음 (명단이 날아간 상황 재현)\n' "$COUNT"
printf '\033[1;32m  완료: DB 엔 기록이 있는데 Redis 명단엔 없음 → 명단 쪽 숫자가 어긋납니다.\033[0m\n'
