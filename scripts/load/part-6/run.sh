#!/usr/bin/env bash
# part-6-0 베이스라인 러너. 발급 엔드포인트로 폭주를 보내고 서버측 카운터로 도착 속도를 잰다.
# 대기실이 없으니 도착 속도 = 전송 속도(RATE). 사용: [RATE=2000 DURATION=20s] run.sh
set -euo pipefail
cd "$(dirname "$0")/../../.."

BASE="${BASE_URL:-http://localhost:8080}"
RATE="${RATE:-1000}"
DURATION="${DURATION:-20s}"

printf '\n\033[1;36m===== part-6-0 베이스라인: 매진 전 폭주 (대기실 없음) =====\033[0m\n'
./scripts/load/reset.sh >/dev/null
coupon_id=$(./scripts/load/part-6/create_big_coupon.sh)
printf '쿠폰 %s 생성 (재고 100만, 매진이 측정에 끼지 않게)\n' "$coupon_id"

curl -fsS -X POST "$BASE/metrics/traffic/reset" >/dev/null
k6 run -e COUPON_ID="$coupon_id" -e RATE="$RATE" -e DURATION="$DURATION" scripts/load/part-6/issue_flood.js

arrivals=$(curl -fsS "$BASE/metrics/traffic" | jq -r '.issueArrivals')
secs=${DURATION%s}
arrival_rate=$(( arrivals / secs ))
printf '\n\033[1;33m서버 도착(발급 처리 도달): %s건 / %ss = 약 %s건/s\033[0m\n' "$arrivals" "$secs" "$arrival_rate"
printf '\033[0;37m대기실이 없으니 보낸 만큼(RATE=%s/s) 그대로 서버에 도착한다. 다음 단계에서 이 도착 속도를 통과 속도로 눌러본다.\033[0m\n' "$RATE"

# 베이스라인 기대: 대기실이 없으니 도착 속도가 전송 속도(RATE)에 가깝다 (눌리지 않음). 하한 밖이면 실패.
TOLERANCE_PERCENT="${TOLERANCE_PERCENT:-30}"
floor=$(( RATE * (100 - TOLERANCE_PERCENT) / 100 ))
printf '\n[판정] 기대 도착 속도 약 RATE=%s건/s (허용 하한 %s건/s)\n' "$RATE" "$floor"
if (( arrival_rate >= floor )); then
  printf '\033[1;32m  ✓ 통과\033[0m 대기실 없이 보낸 만큼 서버에 도착 (실측 %s건/s)\n' "$arrival_rate"
  printf '\n\033[1;32m===== part-6-0 테스트 통과 =====\033[0m\n'
else
  printf '\033[1;31m  ✗ 실패\033[0m 도착 속도가 전송보다 크게 낮음 (실측 %s건/s, 하한 %s건/s)\n' "$arrival_rate" "$floor"
  printf '\n\033[1;31m===== part-6-0 테스트 실패 =====\033[0m\n'; exit 1
fi
