-- compensate-issuance.lua : 발급의 역연산을 한 덩어리로 (5단원 4.2)
-- KEYS[1] = coupon:{id}:stock
-- KEYS[2] = coupon:{id}:users
-- KEYS[3] = coupon:{id}:sold_out
-- KEYS[4] = compensation:{issuanceAttemptId}        (빠른 중복 방지 키)
-- KEYS[5] = coupon:{id}:issuance-attempt:{userId}    (현재 발급 issuanceAttemptId)
-- ARGV[1] = userId
-- ARGV[2] = idempotencyTtlSeconds
-- ARGV[3] = issuanceAttemptId
-- 반환: 1=실제 보상, 0=이미 보상, -1=다른 발급

-- 오래된 DLT가 새 발급을 되돌리지 않도록 현재 발급 세대를 먼저 확인한다.
if redis.call('GET', KEYS[5]) ~= ARGV[3] then
  if redis.call('EXISTS', KEYS[4]) == 1 then
    return 0
  end
  return -1
end

-- DB만 성공했던 부분 실패는 Redis 키가 없으므로 여기서 한 번 복구할 수 있다.
if redis.call('SET', KEYS[4], '1', 'NX', 'EX', ARGV[2]) == false then
  return 0
end

redis.call('SREM', KEYS[2], ARGV[1])
redis.call('DEL', KEYS[5])
local newStock = redis.call('INCR', KEYS[1])

-- 보상 후 재고가 양수가 되면 매진 플래그를 푼다 (조건부 해제).
if newStock >= 1 then
  redis.call('DEL', KEYS[3])
end

return 1
