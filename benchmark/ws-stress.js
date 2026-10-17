// WebSocket/STOMP tier — finds the ceiling on live connections + chat throughput.
// Opens N raw STOMP-1.2 connections (no SockJS), each authenticates on CONNECT
// and streams SEND frames to a real direct channel it participates in.
// Requires benchmark/.pool.json  ->  run `node benchmark/seed.mjs` first.
//   k6 run benchmark/ws-stress.js
//   WS_PEAK=3000 WS_MSG_INTERVAL_MS=200 k6 run benchmark/ws-stress.js
import ws from 'k6/ws';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { WS_URL } from './config.js';

const pool = JSON.parse(open('./.pool.json')); // init context; errors clearly if missing

const peak      = parseInt(__ENV.WS_PEAK || '1000', 10);
const holdSec   = parseInt(__ENV.WS_HOLD || '60', 10);          // how long each socket stays open
const intervalMs = parseInt(__ENV.WS_MSG_INTERVAL_MS || '500', 10); // per-connection send cadence

const sent      = new Counter('stomp_msgs_sent');
const errors    = new Counter('stomp_error_frames');
const connected = new Counter('stomp_connected');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: Math.round(peak * 0.1) },
        { duration: '1m',  target: Math.round(peak * 0.5) },
        { duration: '2m',  target: peak },   // sustained peak
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    stomp_error_frames: ['count<1'],   // any ERROR frame = server rejecting/backpressuring
    ws_connecting:      ['p(95)<1500'],
  },
};

// STOMP 1.2 framing: COMMAND\nheader:value\n\n<body>\0
const NUL = '\u0000';
const frame = (cmd, headers, body = '') =>
  cmd + '\n' + Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n') + '\n\n' + body + NUL;

export default function () {
  const me = pool[__VU % pool.length];
  const dest = `/app/direct-channels/${me.channelId}/messages`;

  ws.connect(WS_URL, {}, (socket) => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', {
        'accept-version': '1.2',
        host: new URL(WS_URL).host,
        Authorization: `Bearer ${me.token}`,
        'heart-beat': '0,0',
      }));
    });

    socket.on('message', (msg) => {
      const cmd = msg.split('\n', 1)[0];
      if (cmd === 'CONNECTED') {
        connected.add(1);
        socket.setInterval(() => {
          socket.send(frame('SEND',
            { destination: dest, 'content-type': 'application/json' },
            JSON.stringify({ content: `load ${Date.now()}` })));
          sent.add(1);
        }, intervalMs);
      } else if (cmd === 'ERROR') {
        errors.add(1);
        socket.close();
      }
    });

    socket.setTimeout(() => socket.close(), holdSec * 1000);
    socket.on('error', (e) => { if (String(e.error()).indexOf('closed') < 0) errors.add(1); });
  });

  check(null, { 'ws opened a connection': () => true });
}
