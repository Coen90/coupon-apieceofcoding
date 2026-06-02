// 엣지 Rate Limit 검증. 게이트웨이(8090)로 어뷰저와 정상 사용자를 동시에 보낸다.
//   - 어뷰저: 한 사용자 id 로 초당 수백 번. Token Bucket 한도를 넘는 만큼 429 로 컷.
//   - 정상: 매 요청 다른 사용자 id. 각자 버킷이라 한 번도 안 막힌다.
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const COUPON_ID = __ENV.COUPON_ID || '1';
const BASE = __ENV.BASE_URL || 'http://localhost:8090';
const DURATION = __ENV.DURATION || '10s';

const abuserPassed = new Counter('abuser_passed');
const abuserBlocked = new Counter('abuser_blocked');
const normalPassed = new Counter('normal_passed');
const normalBlocked = new Counter('normal_blocked');

export const options = {
  scenarios: {
    abuser: {
      executor: 'constant-arrival-rate', exec: 'abuser',
      rate: Number(__ENV.ABUSER_RATE || 200), timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 200, maxVUs: 400,
    },
    normal: {
      executor: 'constant-arrival-rate', exec: 'normal',
      rate: Number(__ENV.NORMAL_RATE || 20), timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 50, maxVUs: 100,
    },
  },
  thresholds: {
    normal_blocked: ['count==0'],  // 정상 사용자는 한 번도 안 막혀야
    abuser_blocked: ['count>0'],   // 어뷰저는 엣지에서 컷돼야
  },
};

export function abuser() {
  const res = http.post(`${BASE}/api/waiting-room/${COUPON_ID}`, null, { headers: { 'X-User-Id': '424242' } });
  if (res.status === 200) abuserPassed.add(1);
  else if (res.status === 429) abuserBlocked.add(1);
}

export function normal() {
  const userId = `9${__VU}${(__ITER + 1) * 1000}`;
  const res = http.post(`${BASE}/api/waiting-room/${COUPON_ID}`, null, { headers: { 'X-User-Id': userId } });
  if (res.status === 200) normalPassed.add(1);
  else if (res.status === 429) normalBlocked.add(1);
}
