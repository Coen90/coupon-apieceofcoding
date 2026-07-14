#!/usr/bin/env bash
# 장애 복구를 확인한 운영자가 대기 중인 DLT를 최대 100건 재처리한다.
# 사용: BASE=http://localhost:8080 ./dlt_replay.sh

set -euo pipefail

: "${BASE:=http://localhost:8080}"
curl -fsS -X POST "$BASE/admin/issuance/dlt/replay"
printf '\n대기 중인 DLT 메시지를 최대 100건 재처리했습니다.\n'
