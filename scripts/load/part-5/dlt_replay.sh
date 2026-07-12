#!/usr/bin/env bash
# 운영자가 DLT 내용을 확인한 뒤 같은 issuanceAttemptId로 원본 토픽에 재처리 요청한다.
# 사용: BASE=http://localhost:8080 COUPON_ID=1 USER_ID=42 ISSUANCE_ATTEMPT_ID=... \
#       ISSUED_AT=2026-07-12T01:00:00 EXPIRES_AT=2026-07-19T01:00:00 ./dlt_replay.sh

set -euo pipefail

: "${BASE:=http://localhost:8080}"
COUPON_ID="${COUPON_ID:?COUPON_ID 환경변수 필요}"
USER_ID="${USER_ID:?USER_ID 환경변수 필요}"
ISSUANCE_ATTEMPT_ID="${ISSUANCE_ATTEMPT_ID:?ISSUANCE_ATTEMPT_ID 환경변수 필요}"
ISSUED_AT="${ISSUED_AT:?ISSUED_AT 환경변수 필요}"
EXPIRES_AT="${EXPIRES_AT:?EXPIRES_AT 환경변수 필요}"

curl -fsS -X POST "$BASE/admin/dlt/replay" \
  -H 'Content-Type: application/json' \
  -d "{\"couponId\":$COUPON_ID,\"userId\":$USER_ID,\"issuanceAttemptId\":\"$ISSUANCE_ATTEMPT_ID\",\"issuedAt\":\"$ISSUED_AT\",\"expiresAt\":\"$EXPIRES_AT\"}"
printf '\n원본 issuanceAttemptId로 재처리 요청을 넣었습니다.\n'
