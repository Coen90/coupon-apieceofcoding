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
  printf '쿠폰을 새로 만들었어요 (번호 %s, 총 5000장)\n' "$coupon_id"
  COUPON_ID="$coupon_id" COUNT="$COUNT" "./scripts/load/part-5/$injector"
  COUPON_ID="$coupon_id" ./scripts/load/part-5/drift_report.sh
}

phase "상황 ① : 발급은 됐는데 DB 저장이 실패 (force_dlt)" force_dlt.sh
phase "상황 ② : DB 엔 발급됐는데 Redis 명단이 날아감 (force_db_only)" force_db_only.sh

printf '\n\033[1;36m정리: 지금은 두 경우 모두 자동으로 고쳐지지 않고 그대로 남아 있어요 (part-5-0 베이스라인).\n'
printf '  다음 단계에서 ① 은 part-5-1(보상)이 DLT를 확인한 운영자의 재처리 또는 보상을 지원하고,\n'
printf '  ② 는 part-5-2(reconcile)가 DB 명단을 보고 Redis 에 자동으로 채워줍니다.\033[0m\n'
