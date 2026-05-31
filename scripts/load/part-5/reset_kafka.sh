#!/usr/bin/env bash
# kafka 를 클린 상태로 되돌린다. 회차 사이 DLT 잔존 메시지와 컨슈머 그룹의 스테일
# 오프셋이 보상 검증에 섞이지 않도록, 토픽만 지우는 대신 kafka 컨테이너를 재생성한다
# (KRaft 데이터가 볼륨 없이 컨테이너 안에 있어 재생성하면 전부 비워진다).
# mysql/redis 는 건드리지 않는다.
#
# 사용:  scripts/load/part-5/reset_kafka.sh

set -euo pipefail
cd "$(dirname "$0")/../../.."

printf '\n\033[1;36m===== kafka 재생성 (토픽/오프셋 클린) =====\033[0m\n'
docker compose up -d --force-recreate kafka >/dev/null 2>&1

for _ in $(seq 1 40); do
  status="$(docker compose ps kafka --format '{{.Status}}' 2>/dev/null || true)"
  [[ "$status" == *healthy* ]] && break
  sleep 1
done
printf '  kafka: %s\n' "$(docker compose ps kafka --format '{{.Status}}')"
