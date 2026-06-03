#!/usr/bin/env bash
# part-6-1 대기실 러너. 진입 폭주로 줄을 채운 뒤, 드레인이 매초 통과시킨 인원으로 통과 속도를 잰다.
#   run.sh single   # 1대
#   run.sh scale     # 2대로 늘려도 통과 속도가 유지되는지 (ShedLock)
set -euo pipefail
cd "$(dirname "$0")/../../.."

RATE="${RATE:-500}"
DURATION="${DURATION:-20s}"
SECS="${DURATION%s}"

admitted_at() { curl -fsS "$1/metrics/traffic" | jq -r '.admitted'; }

wait_ready() {
  for _ in $(seq 1 60); do curl -fsS "$1/metrics/traffic" >/dev/null 2>&1 && return; sleep 1; done
  echo "$1 준비 실패"; exit 1
}

# measure <server-url>...  : 받은 서버들에 진입 폭주를 보내고 합산 통과 속도를 출력.
measure() {
  ./scripts/load/reset.sh >/dev/null
  local coupon_id; coupon_id=$(./scripts/load/part-6/create_big_coupon.sh)
  printf '쿠폰 %s 생성. 진입 폭주로 줄을 채운다.\n' "$coupon_id"

  for server in "$@"; do curl -fsS -X POST "$server/metrics/traffic/reset" >/dev/null; done
  k6 run -e COUPON_ID="$coupon_id" -e BASES="$(IFS=','; echo "$*")" -e RATE="$RATE" -e DURATION="$DURATION" \
    scripts/load/part-6/waiting_room_flood.js

  local total=0
  for server in "$@"; do
    local n; n=$(admitted_at "$server")
    printf '  %s 통과 인원: %s\n' "$server" "$n"
    total=$(( total + n ))
  done
  printf '\033[1;33m통과 속도: %s건 / %ss = 약 %s건/s (서버 %d대)\033[0m\n' "$total" "$SECS" "$(( total / SECS ))" "$#"
}

case "${1:-single}" in
  single)
    printf '\n\033[1;36m===== 1대 통과 속도 =====\033[0m\n'
    measure http://localhost:8080
    ;;
  scale)
    printf '\n\033[1;36m===== 2대 통과 속도 =====\033[0m\n'
    docker compose --profile scale up -d coupon-service-2 >/dev/null
    wait_ready http://localhost:8081
    measure http://localhost:8080 http://localhost:8081
    printf '\033[0;37mShedLock 이 매초 한 대만 드레인 → 2대로 늘려도 통과 속도가 유지된다\n'
    printf '(서버마다 따로 드레인하는 in-memory 였다면 2배로 깨질 자리).\033[0m\n'
    ;;
  *)
    echo "사용법: run.sh [single|scale]"; exit 1 ;;
esac
