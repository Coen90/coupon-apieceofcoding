#!/usr/bin/env bash
# 발급 정책 조회 캐시 실험용 쿠폰을 만들고 ID를 출력한다.
# 시작 시각을 미래로 설정해 issue API가 정책 조회 후 NotStarted로 끝나도록 한다.

set -euo pipefail

curl -fsS -X POST http://localhost:8080/api/coupons \
  -H 'Content-Type: application/json' \
  -d '{"name":"issue policy cache test","totalQuantity":1,"validityDays":7,"startsAt":"2099-01-01T00:00:00"}' \
  | jq -r '.id'
