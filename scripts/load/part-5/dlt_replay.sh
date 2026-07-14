#!/usr/bin/env bash
# 운영자가 DLT inbox의 메시지를 확인한 뒤 원본 토픽으로 재처리 요청한다.
# 사용: BASE=http://localhost:8080 DLT_MESSAGE_ID=12 ./dlt_replay.sh

set -euo pipefail

: "${BASE:=http://localhost:8080}"
DLT_MESSAGE_ID="${DLT_MESSAGE_ID:?DLT_MESSAGE_ID 환경변수 필요}"

curl -fsS -X POST "$BASE/admin/dlt/messages/$DLT_MESSAGE_ID/replay"
printf '\nDLT inbox 메시지를 원본 토픽으로 재처리하고 상태를 REPLAYED로 바꿨습니다.\n'
