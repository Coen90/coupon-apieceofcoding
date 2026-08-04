// 매진 전 폭주 베이스라인. 발급 엔드포인트로 직접 쏟아붓는다.
// userId 를 매 요청 고유하게 만들어 전부 발급 경로를 타게 한다 (서버 도착은 run.sh 가 잰다).
import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';

const COUPON_ID = __ENV.COUPON_ID || '1';
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 1000);
const DURATION = __ENV.DURATION || '20s';

const issueLatency = new Trend('issue_latency', true);
const status2xx = new Counter('status_2xx');
const statusOther = new Counter('status_other');

export const options = {
  scenarios: {
    issue_flood: {
      executor: 'constant-arrival-rate',
      rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: Math.min(RATE, 2000), maxVUs: 4000,
    },
  },
};

export default function () {
  const userId = String(__VU * 10_000_000 + __ITER);
  const res = http.post(`${BASE}/api/coupons/${COUPON_ID}/issue`, null, {
    headers: { 'X-User-Id': userId },
  });
  issueLatency.add(res.timings.duration);
  if (res.status >= 200 && res.status < 300) status2xx.add(1);
  else statusOther.add(1);
}
