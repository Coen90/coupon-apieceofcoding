# part-5 검증 공용 헬퍼. verify_compensation.sh / verify_reconcile.sh 에서 source 한다.
# 실행 파일이 아니라 라이브러리라 shebang 이 없다. 호출 측에서 repo 루트로 cd 한 뒤 source 한다.

BASE="${BASE_URL:-http://localhost:8080}"

mysql_scalar() { docker compose exec -T -e MYSQL_PWD=coupon mysql mysql -ucoupon -BN coupon -e "$1"; }
redis_cli()    { docker compose exec -T redis redis-cli "$@"; }

wait_service_ready() { # [timeout=60]
  for _ in $(seq 1 "${1:-60}"); do
    curl -fsS "$BASE/metrics/cache" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

# kafka 를 비우고 서비스를 깨끗한 상태로 재기동한다 (보상 처리기가 처음부터 다시 읽음).
# 인자로 reconcile 주기(ms)를 주면 그 값으로 띄워(force-recreate) 스케줄 간섭을 막고, 없으면 기본 주기로 재시작.
restart_service() { # [reconcile_interval_ms]
  ./scripts/load/part-5/reset_kafka.sh
  if [[ -n "${1:-}" ]]; then
    COUPON_RECONCILE_INTERVAL_MS="$1" docker compose up -d --force-recreate coupon-service >/dev/null
  else
    docker compose restart coupon-service >/dev/null
  fi
  wait_service_ready || { ng "coupon-service 재기동을 기다리다 실패"; exit 1; }
}

fail=0
pass()  { printf '\033[1;32m  ✓ 통과\033[0m %s\n' "$1"; }
ng()    { printf '\033[1;31m  ✗ 실패\033[0m %s\n' "$1"; fail=1; }
check() { if [[ "$2" == "$3" ]]; then pass "$1 ($2)"; else ng "$1 (실제 $2, 기대 $3)"; fi; }

# 전체 결과 한 줄. 실패가 하나라도 있으면 1 로 종료.
summary() { # label
  if (( fail == 0 )); then
    printf '\n\033[1;32m===== %s 검증: 모두 통과 =====\033[0m\n' "$1"
  else
    printf '\n\033[1;31m===== %s 검증: 실패한 항목이 있습니다 =====\033[0m\n' "$1"
    exit 1
  fi
}
