#!/usr/bin/env bash
# part-5-0 베이스라인 러너. 두 종류의 불일치를 주입하고, 자동 복구 장치가 아직
# 없으므로 drift 가 그대로 남는 것을 보여준다 (part-5-1/5-2 의 before 그림).
#
# 사용:  ./scripts/load/part-5/run.sh
# 사전:  docker compose 스택 + coupon-service(part-5-0 이미지) 가 떠 있어야 한다.

set -euo pipefail
cd "$(dirname "$0")/../../.."

COUNT="${COUNT:-10}"

phase() {
  local label="$1"; local injector="$2"
  printf '\n\033[1;35m##### %s #####\033[0m\n' "$label"
  ./scripts/load/reset.sh >/dev/null
  local coupon_id
  coupon_id=$(./scripts/load/create_coupon.sh)
  printf '생성된 coupon_id = %s (total 5000)\n' "$coupon_id"
  COUPON_ID="$coupon_id" COUNT="$COUNT" "./scripts/load/part-5/$injector"
  COUPON_ID="$coupon_id" ./scripts/load/part-5/drift_report.sh
}

phase "① Worker INSERT 영구 실패 (force_dlt) → DB 측 불일치 잔존" force_dlt.sh
phase "② Redis 사용자 목록 휘발 (force_db_only) → 목록 측 불일치 잔존" force_db_only.sh

printf '\n\033[1;36m베이스라인: 두 불일치 모두 자동 복구 없이 그대로 남아 있다.\n'
printf '  part-5-1(보상)이 ①의 DLT 를 소비해 되돌리고, part-5-2(reconcile)가 ②를 SADD 로 자동 보정한다.\033[0m\n'
