#!/usr/bin/env bash
set -e
BASE="${BASE_URL:-http://localhost:8080}"

COUPON=$(curl -sS -X POST "$BASE/api/coupons" \
  -H 'Content-Type: application/json' \
  -d '{"name":"여름 할인","totalQuantity":100,"validityDays":7}' | jq -r '.id')

# 발급 전 대기실 통과 필요. 입장권 나올 때까지 폴링.
for _ in $(seq 1 10); do
  ADMITTED=$(curl -sS -X POST "$BASE/api/waiting-room/$COUPON" -H 'X-User-Id: 1' | jq -r '.admitted')
  [ "$ADMITTED" = "true" ] && break
  sleep 1
done

curl -sS -X POST "$BASE/api/coupons/$COUPON/issue" -H 'X-User-Id: 1' >/dev/null

# 발급은 비동기 저장(part-3)이라 응답에 id 가 없다. 목록에서 저장된 id 를 받아 쓴다.
for _ in $(seq 1 10); do
  ISSUANCE=$(curl -sS "$BASE/api/users/me/issuances" -H 'X-User-Id: 1' | jq -r '.[0].id // empty')
  [ -n "$ISSUANCE" ] && break
  sleep 1
done

curl -sS -X POST "$BASE/api/issuances/$ISSUANCE/use" -H 'X-User-Id: 1'
curl -sS "$BASE/api/users/me/issuances" -H 'X-User-Id: 1'
