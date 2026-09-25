// Shared config for seed.mjs (Node) and the k6 scripts.
// Works in both runtimes: k6 exposes __ENV, Node exposes process.env.
const env = (typeof __ENV !== 'undefined')
  ? __ENV
  : (typeof process !== 'undefined' ? process.env : {});

export const API = env.API || 'https://livechat-api.kaiangazineu.dev';
export const WS_URL = env.WS_URL || 'wss://livechat-api.kaiangazineu.dev/ws';

// Fixed pool of test accounts — never infinite creation.
export const POOL_SIZE = parseInt(env.POOL_SIZE || '10000', 10);
export const PASSWORD = env.PASSWORD || 'LoadTest123!'; // 8..72 chars, the only policy

export const email = (i) => `loadtest+${i}@example.com`;
