// k6 load test: https://k6.io  ->  k6 run loadtest/create-orders.js
// Needs the stack running (docker compose up --build). Reports throughput and latency
// you can quote honestly on your CV (e.g. "sustained X orders/s at p95 < Y ms").
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
  scenarios: {
    steady: { executor: 'constant-arrival-rate', rate: 100, timeUnit: '1s', duration: '1m', preAllocatedVUs: 50 },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

export function setup() {
  const res = http.post(`${BASE}/dev/token?customerId=load-test`);
  return { token: res.json('access_token') };
}

export default function (data) {
  const res = http.post(`${BASE}/api/v1/orders`,
    JSON.stringify({ amount: (Math.random() * 200 + 1).toFixed(2), currency: 'GBP' }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}`,
                 'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}` } });
  check(res, { 'order accepted (201)': (r) => r.status === 201 });
}
