#!/usr/bin/env bash
# coupon/issuance TRUNCATE 후 행 수 확인 (둘 다 0 이어야 정상).

# 명령 실패 / 미정의 변수 / 파이프 중간 실패 시 즉시 종료
set -euo pipefail

# docker-compose.yml 위치로 이동 후 mysql 컨테이너 안에서 TRUNCATE + 확인
cd "$(dirname "$0")/../.."

printf '\n\033[1;36m===== coupon, issuance 데이터 리셋 =====\033[0m\n'
# compensation_log 는 part-5-1 이상에서만 존재한다. 있으면 같이 비운다 (없으면 DO 0 으로 no-op).
# 안 비우면 회차 사이에 같은 issuanceAttemptId 가 멱등 hit 으로 잡혀 보상이 재현되지 않는다.
docker compose exec -T -e MYSQL_PWD=coupon mysql mysql -ucoupon -t coupon -e "
  SET FOREIGN_KEY_CHECKS=0;
  TRUNCATE issuance;
  TRUNCATE coupon;
  SET @has_cl := (SELECT COUNT(*) FROM information_schema.tables
                  WHERE table_schema = 'coupon' AND table_name = 'compensation_log');
  SET @sql := IF(@has_cl > 0, 'TRUNCATE compensation_log', 'DO 0');
  PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
  SET FOREIGN_KEY_CHECKS=1;
  SELECT (SELECT COUNT(*) FROM coupon)   AS coupon_rows,
         (SELECT COUNT(*) FROM issuance) AS issuance_rows;
"

# Redis 의 coupon stock 카운터, 발급자 set 도 비워야 회차 사이 잔존 상태가
# 다음 측정에 섞이지 않는다. 부하 테스트용 redis 라 FLUSHDB 가 안전하고 간단.
printf '\n\033[1;36m===== redis 데이터 리셋 (FLUSHDB) =====\033[0m\n'
docker compose exec -T redis redis-cli FLUSHDB
