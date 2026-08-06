#!/usr/bin/env bash
# part-6-1 대기실 러너. 진입 요청 급증으로 줄을 채운 뒤, 드레인이 매초 통과시킨 인원으로 통과 속도를 잰다.
#   run.sh single   # 1대
#   run.sh scale     # 2대로 늘려도 통과 속도가 유지되는지 (ShedLock)
set -euo pipefail
cd "$(dirname "$0")/../../.."

RATE="${RATE:-500}"
DURATION="${DURATION:-20s}"
EXPECTED_ADMIT_PER_SECOND="${COUPON_WAITING_ROOM_ADMIT_PER_SECOND:-100}"
TOLERANCE_PERCENT="${TOLERANCE_PERCENT:-30}"
QUANTITY="${QUANTITY:-1000000}"
PRIMARY_BASE="${BASE_URL:-http://localhost:8080}"
SECONDARY_BASE="${SECONDARY_BASE_URL:-http://localhost:8081}"

[[ "$DURATION" =~ ^[1-9][0-9]*s$ ]] || { echo "DURATION은 20s처럼 초 단위로 입력해야 한다" >&2; exit 1; }
SECS="${DURATION%s}"
(( RATE > EXPECTED_ADMIT_PER_SECOND )) || {
  echo "RATE는 통과 속도(${EXPECTED_ADMIT_PER_SECOND}/s)보다 커야 대기열이 유지된다" >&2
  exit 1
}

admitted_at() { curl -fsS "$1/metrics/traffic" | jq -r '.admitted'; }

wait_ready() {
  for _ in $(seq 1 60); do curl -fsS "$1/metrics/traffic" >/dev/null 2>&1 && return; sleep 1; done
  echo "$1 준비 실패"; exit 1
}

# measure <server-url>...  : 받은 서버들에 진입 요청 급증을 보내고 합산 통과 속도를 출력.
measure() {
  ./scripts/load/reset.sh >/dev/null
  local coupon_id; coupon_id=$(BASE_URL="$1" QUANTITY="$QUANTITY" ./scripts/load/part-6/create_big_coupon.sh)
  printf '쿠폰 %s 생성 (재고 %s). 진입 요청 급증으로 줄을 채운다.\n' "$coupon_id" "$QUANTITY"

  for server in "$@"; do curl -fsS -X POST "$server/metrics/traffic/reset" >/dev/null; done
  k6 run -e COUPON_ID="$coupon_id" -e BASES="$(IFS=','; echo "$*")" -e RATE="$RATE" -e DURATION="$DURATION" \
    scripts/load/part-6/waiting_room_flood.js

  local total=0
  for server in "$@"; do
    local n; n=$(admitted_at "$server")
    printf '  %s 통과 인원: %s\n' "$server" "$n"
    total=$(( total + n ))
  done
  local measured_rate=$(( total / SECS ))
  local lower_bound=$(( EXPECTED_ADMIT_PER_SECOND * (100 - TOLERANCE_PERCENT) / 100 ))
  local upper_bound=$(( EXPECTED_ADMIT_PER_SECOND * (100 + TOLERANCE_PERCENT) / 100 ))
  printf '\033[1;33m통과 속도: %s건 / %ss = 약 %s건/s (서버 %d대)\033[0m\n' "$total" "$SECS" "$measured_rate" "$#"

  if (( measured_rate < lower_bound || measured_rate > upper_bound )); then
    printf '\033[1;31m  ✗ 실패\033[0m 기대 %s건/s, 허용 범위 %s~%s건/s\n' \
      "$EXPECTED_ADMIT_PER_SECOND" "$lower_bound" "$upper_bound"
    return 1
  fi
  printf '\033[1;32m  ✓ 통과\033[0m 기대 %s건/s, 허용 범위 %s~%s건/s\n' \
    "$EXPECTED_ADMIT_PER_SECOND" "$lower_bound" "$upper_bound"
}

case "${1:-single}" in
  single)
    docker compose --profile scale stop coupon-service-2 >/dev/null
    printf '\n\033[1;36m===== 1대 통과 속도 =====\033[0m\n'
    measure "$PRIMARY_BASE"
    ;;
  scale)
    trap 'docker compose --profile scale stop coupon-service-2 >/dev/null 2>&1 || true' EXIT
    printf '\n\033[1;36m===== 2대 통과 속도 =====\033[0m\n'
    docker compose --profile scale up -d coupon-service-2 >/dev/null
    wait_ready "$SECONDARY_BASE"
    measure "$PRIMARY_BASE" "$SECONDARY_BASE"
    printf '\033[0;37mShedLock 이 매초 한 대만 드레인 → 2대로 늘려도 통과 속도가 유지된다.\033[0m\n'
    ;;
  *)
    echo "사용법: run.sh [single|scale]"; exit 1 ;;
esac
