#!/usr/bin/env bash
# part-6-0 베이스라인 러너. 발급 엔드포인트로 트래픽 급증을 보내고 서버 측 카운터로 도착 속도를 잰다.
# 대기실이 없으니 도착 속도 = 전송 속도(RATE). 사용: [RATE=2000 DURATION=20s] run.sh
set -euo pipefail
cd "$(dirname "$0")/../../.."

BASE="${BASE_URL:-http://localhost:8080}"
RATE="${RATE:-1000}"
DURATION="${DURATION:-20s}"
QUANTITY="${QUANTITY:-1000000}"
TOLERANCE_PERCENT="${TOLERANCE_PERCENT:-30}"

[[ "$DURATION" =~ ^[1-9][0-9]*s$ ]] || { echo "DURATION은 20s처럼 초 단위로 입력해야 한다" >&2; exit 1; }
SECS="${DURATION%s}"

printf '\n\033[1;36m===== part-6-0 베이스라인: 매진 전 트래픽 급증 (대기실 없음) =====\033[0m\n'
./scripts/load/reset.sh >/dev/null
coupon_id=$(BASE_URL="$BASE" QUANTITY="$QUANTITY" ./scripts/load/part-6/create_big_coupon.sh)
printf '쿠폰 %s 생성 (재고 %s, 매진이 측정에 끼지 않게)\n' "$coupon_id" "$QUANTITY"

curl -fsS -X POST "$BASE/metrics/traffic/reset" >/dev/null
k6 run -e COUPON_ID="$coupon_id" -e BASE_URL="$BASE" -e RATE="$RATE" -e DURATION="$DURATION" \
  scripts/load/part-6/issue_flood.js

arrivals=$(curl -fsS "$BASE/metrics/traffic" | jq -r '.issueArrivals')
arrival_rate=$(( arrivals / SECS ))
lower_bound=$(( RATE * (100 - TOLERANCE_PERCENT) / 100 ))
printf '\n\033[1;33m서버 도착: %s건 / %ss = 약 %s건/s\033[0m\n' "$arrivals" "$SECS" "$arrival_rate"

if (( arrival_rate < lower_bound )); then
  printf '\033[1;31m  ✗ 실패\033[0m 기대 %s건/s, 허용 하한 %s건/s\n' "$RATE" "$lower_bound"
  exit 1
fi
printf '\033[1;32m  ✓ 통과\033[0m 기대 %s건/s, 허용 하한 %s건/s\n' "$RATE" "$lower_bound"
