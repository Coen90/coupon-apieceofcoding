// 이미 등록한 사용자들이 상태 조회를 반복하는 상황을 측정한다.
// 첫 호출에서만 등록하고 이후에는 GET 상태 조회만 보낸다.
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const COUPON_ID = __ENV.COUPON_ID || '1';
const BASES = (__ENV.BASES || 'http://localhost:8080').split(',');
const RATE = Number(__ENV.POLL_RATE || 500);
const DURATION = __ENV.DURATION || '20s';

const statusLatency = new Trend('waiting_room_status_latency', true);
const statusSuccess = new Counter('waiting_room_status_success');
const statusFailure = new Counter('waiting_room_status_failure');

export const options = {
  scenarios: {
    status_poll: {
      executor: 'constant-arrival-rate',
      rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: Math.min(RATE, 2000), maxVUs: 4000,
    },
  },
};

export default function () {
  const base = BASES[__VU % BASES.length];
  const userId = String(6000000000 + __VU);

  if (__ITER === 0) {
    http.post(`${base}/api/waiting-room/${COUPON_ID}`, null, {
      headers: { 'X-User-Id': userId },
    });
  }

  const response = http.get(`${base}/api/waiting-room/${COUPON_ID}`, {
    headers: { 'X-User-Id': userId },
  });
  statusLatency.add(response.timings.duration);
  if (response.status === 200) statusSuccess.add(1);
  else statusFailure.add(1);
}
