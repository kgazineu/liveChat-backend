// HTTP tier — finds the CPU/connection ceiling.
// Login is the heavy path (BCrypt verify + JWT sign), so we hammer it, then
// hit an authenticated read with the fresh token.
//   k6 run benchmark/stress.js
//   VUS_PEAK=2000 k6 run benchmark/stress.js   # push harder
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { API, POOL_SIZE, PASSWORD, email } from './config.js';

const peak = parseInt(__ENV.VUS_PEAK || '1000', 10);
const loginDur = new Trend('login_duration', true);

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: Math.round(peak * 0.05) },
        { duration: '1m',  target: Math.round(peak * 0.2) },
        { duration: '1m',  target: Math.round(peak * 0.5) },
        { duration: '2m',  target: peak },   // sustained peak — where it should break
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  // Thresholds flag degradation (non-abort) so the run completes and shows the wall.
  thresholds: {
    http_req_failed:   ['rate<0.02'],
    http_req_duration: ['p(95)<800', 'p(99)<2000'],
  },
};

export default function () {
  const i = Math.floor(Math.random() * POOL_SIZE);
  const login = http.post(`${API}/users/login`,
    JSON.stringify({ email: email(i), password: PASSWORD }),
    { headers: { 'content-type': 'application/json' }, tags: { name: 'login' } });
  loginDur.add(login.timings.duration);

  const ok = check(login, { 'login 200': (r) => r.status === 200 });
  if (!ok) return;

  const token = login.json('token');
  http.get(`${API}/users/me`,
    { headers: { authorization: `Bearer ${token}` }, tags: { name: 'me' } });
}
