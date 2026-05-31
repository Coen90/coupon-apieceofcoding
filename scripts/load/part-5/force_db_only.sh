#!/usr/bin/env bash
# 불일치 주입 ②: "Redis 사용자 목록 휘발" 재현 (5단원 5.2 두 번째 행).
#
# 정상 발급처럼 DB 에는 issuance 행 N개 + issued_quantity +N, Redis stock 도 N 차감하되,
# Redis 사용자 목록(SADD)만 빠뜨린다. 결과: DB 측 잔차 0, 목록 측 잔차 +N (자동 보정 대상).
# reconcile(part-5-2)이 DB 의 누락 user_id 를 SADD 로 되살릴 수 있어야 한다.
#
# 사용:  COUPON_ID=1 [COUNT=10] [USER_BASE=800000] scripts/load/part-5/force_db_only.sh

set -euo pipefail
COUPON_ID="${COUPON_ID:?COUPON_ID 환경변수 필요}"
COUNT="${COUNT:-10}"
USER_BASE="${USER_BASE:-800000}"
cd "$(dirname "$0")/../../.."

mysql_exec() { docker compose exec -T -e MYSQL_PWD=coupon mysql mysql -ucoupon -BN coupon -e "$1"; }
redis_cli() { docker compose exec -T redis redis-cli "$@"; }

printf '\n\033[1;36m===== force_db_only: Redis 사용자 목록 휘발 %s건 주입 (coupon %s) =====\033[0m\n' \
  "$COUNT" "$COUPON_ID"

# 1) DB 에 ISSUED 행 N개 INSERT (정상 발급이 DB 까지 도달한 것처럼).
values=""
first=0; last=0
for i in $(seq 1 "$COUNT"); do
  uid=$(( USER_BASE + i ))
  if (( i == 1 )); then first=$uid; fi
  last=$uid
  [[ -n "$values" ]] && values+=","
  values+="($uid, $COUPON_ID, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ISSUED')"
done
mysql_exec "INSERT INTO issuance (user_id, coupon_id, issued_at, expires_at, status) VALUES $values;
            UPDATE coupon SET issued_quantity = issued_quantity + $COUNT WHERE id = $COUPON_ID;"
printf '  DB: issuance %s행 INSERT (user %s..%s) + issued_quantity +%s\n' \
  "$COUNT" "$first" "$last" "$COUNT"

# 2) Redis stock 만 N 차감 (사용자 목록 SADD 는 일부러 생략 = 휘발 재현).
redis_cli DECRBY "coupon:$COUPON_ID:stock" "$COUNT" >/dev/null
printf '  Redis: stock -%s, users 는 그대로 (SADD 누락 = 휘발)\n' "$COUNT"
printf '\033[1;32m  완료.\033[0m\n'
