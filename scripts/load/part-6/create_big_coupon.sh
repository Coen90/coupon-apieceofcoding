#!/usr/bin/env bash
# 재고를 크게 잡은 쿠폰을 만들고 ID 출력. 트래픽 급증이 매진으로 끝나지 않게 한다.
set -euo pipefail

QUANTITY="${QUANTITY:-1000000}"
BASE="${BASE_URL:-http://localhost:8080}"
curl -fsS -X POST "$BASE/api/coupons" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"traffic test\",\"totalQuantity\":${QUANTITY},\"validityDays\":7}" \
  | jq -r '.id'
