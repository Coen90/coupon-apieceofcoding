-- compensate-issuance.lua : 발급의 역연산을 한 덩어리로 (5단원 4.2)
-- KEYS[1] = coupon:{id}:stock
-- KEYS[2] = coupon:{id}:users
-- KEYS[3] = coupon:{id}:sold_out
-- KEYS[4] = compensation:{compensationId}    (2차 멱등 키)
-- ARGV[1] = userId
-- ARGV[2] = idempotencyTtlSeconds
-- 반환: 1=실제 보상, 0=이미 보상됨(멱등 hit) 또는 목록에 없음

-- 1차: 멱등 키. 이미 있으면 DB 만 성공하고 Redis 만 재시도되는 경우를 막는다.
if redis.call('SET', KEYS[4], '1', 'NX', 'EX', ARGV[2]) == false then
  return 0
end

-- 2차: 이미 다른 경로로 목록에서 빠진 사용자면 카운터를 또 올리지 않는다.
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 0 then
  return 0
end

redis.call('SREM', KEYS[2], ARGV[1])
local newStock = redis.call('INCR', KEYS[1])

-- 보상 후 재고가 양수가 되면 매진 플래그를 푼다 (조건부 해제).
if newStock >= 1 then
  redis.call('DEL', KEYS[3])
end

return 1
