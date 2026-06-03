#!/usr/bin/env bash
# part-6-2 엣지 Rate Limit 검증. 어뷰저 폭주 중 서버(앱 8080)에 도달하는 건 토큰 한도만큼뿐,
# 나머지는 게이트웨이(8090)에서 429 로 컷. 사용: ./scripts/load/part-6/edge.sh
set -euo pipefail
cd "$(dirname "$0")/../../.."

GATEWAY="${GATEWAY:-http://localhost:8090}"
APP="${APP_BASE:-http://localhost:8080}"
DURATION="${DURATION:-10s}"

printf '\n\033[1;36m===== part-6-2 엣지 Rate Limit: 어뷰저 컷 + 정상 통과 =====\033[0m\n'
docker compose up -d gateway >/dev/null
for _ in $(seq 1 60); do curl -fsS "$GATEWAY/metrics/traffic" >/dev/null 2>&1 && break; sleep 1; done

./scripts/load/reset.sh >/dev/null
coupon_id=$(./scripts/load/part-6/create_big_coupon.sh)   # 앱(8080)에 직접 생성
printf '쿠폰 %s 생성. 어뷰저(한 id)와 정상(매번 다른 id)을 게이트웨이로 동시 전송.\n' "$coupon_id"

curl -fsS -X POST "$APP/metrics/traffic/reset" >/dev/null
k6 run -e COUPON_ID="$coupon_id" -e BASE_URL="$GATEWAY" -e DURATION="$DURATION" \
  scripts/load/part-6/edge_rate_limit.js

enters=$(curl -fsS "$APP/metrics/traffic" | jq -r '.waitingRoomEnters')
printf '\n\033[1;33m서버(앱) 대기실 진입 도달 수: %s건\033[0m\n' "$enters"
printf '\033[0;37m어뷰저 폭주는 토큰 한도만큼만 서버에 닿고 나머지는 엣지에서 429 로 컷된다.\n'
printf '정상 사용자는 각자 버킷이라 한 번도 안 막힌다 (k6 normal_blocked=0).\033[0m\n'
