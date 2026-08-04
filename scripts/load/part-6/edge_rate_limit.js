// 엣지 Rate Limit 검증. 게이트웨이로 어뷰저(같은 IP)와 정상(각자 다른 IP)을 동시에 보낸다.
// 게이트웨이가 클라이언트 IP로 한도를 걸므로, k6 가 LB 역할로 X-Forwarded-For 로 클라이언트를 구분한다.
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const COUPON_ID = __ENV.COUPON_ID || '1';
const BASE = __ENV.BASE_URL || 'http://localhost:8090';
const DURATION = __ENV.DURATION || '10s';

const abuserPassed = new Counter('abuser_passed');
const abuserBlocked = new Counter('abuser_blocked');
const abuserFailed = new Counter('abuser_failed');
const normalPassed = new Counter('normal_passed');
const normalBlocked = new Counter('normal_blocked');
const normalFailed = new Counter('normal_failed');

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
    abuser_failed: ['count==0'],
    normal_failed: ['count==0'],
  },
};

export function abuser() {
  const res = http.post(`${BASE}/api/waiting-room/${COUPON_ID}`, null, {
    headers: { 'X-Forwarded-For': '198.51.100.10', 'X-User-Id': '424242' },
  });
  if (res.status === 200) abuserPassed.add(1);
  else if (res.status === 429) abuserBlocked.add(1);
  else abuserFailed.add(1);
}

export function normal() {
  const clientIp = `203.0.113.${(__VU % 250) + 1}`;  // VU 한 명 = 클라이언트 한 명
  const userId = String(9_000_000_000 + __VU * 1_000_000 + __ITER);
  const res = http.post(`${BASE}/api/waiting-room/${COUPON_ID}`, null, {
    headers: { 'X-Forwarded-For': clientIp, 'X-User-Id': userId },
  });
  if (res.status === 200) normalPassed.add(1);
  else if (res.status === 429) normalBlocked.add(1);
  else normalFailed.add(1);
}
