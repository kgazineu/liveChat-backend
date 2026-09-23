// One-time seed: creates the fixed account pool, a direct channel per account,
// and a friend request from every account to INVITE_TARGET.
// Writes .pool.json = [{ email, token, channelId }] for ws-stress.js.
// Node 18+ (built-in fetch). Run once:  node benchmark/seed.mjs
//
//   POOL_SIZE=5000 node benchmark/seed.mjs        # bigger pool = more DB rows
//   CONCURRENCY=50 POOL_SIZE=10000 node benchmark/seed.mjs
import { writeFileSync } from 'node:fs';
import { API, POOL_SIZE, PASSWORD, email } from './config.js';

const CONCURRENCY = parseInt(process.env.CONCURRENCY || '200', 10);
const INVITE_TARGET = process.env.INVITE_TARGET || 'kaiangazineu@kaian.com';

const post = (path, body, token) => fetch(API + path, {
  method: 'POST',
  headers: { 'content-type': 'application/json', ...(token && { authorization: `Bearer ${token}` }) },
  body: JSON.stringify(body),
});

// One retry on network blips / transient 5xx — a stressed server drops requests,
// and a single blip shouldn't abort a 10k seed.
async function withRetry(fn) {
  try { return await fn(); }
  catch { return await fn(); }
}

// Run fn over items in parallel batches — keeps large pools from crawling.
async function batched(items, fn) {
  const out = new Array(items.length);
  for (let i = 0; i < items.length; i += CONCURRENCY) {
    const slice = items.slice(i, i + CONCURRENCY);
    const res = await Promise.all(slice.map((it, j) => fn(it, i + j)));
    res.forEach((r, j) => { out[i + j] = r; });
    process.stdout.write(`\r  ${Math.min(i + CONCURRENCY, items.length)}/${items.length}`);
  }
  process.stdout.write('\n');
  return out;
}

async function main() {
  if (POOL_SIZE < 2) throw new Error('POOL_SIZE must be >= 2 (channels need two users)');
  const idx = [...Array(POOL_SIZE).keys()];
  console.log(`Seeding ${POOL_SIZE} accounts against ${API} (concurrency ${CONCURRENCY}) ...`);

  // Every phase tolerates per-item failure (count, keep going) so a stopped/re-run
  // seed or a transient blip never aborts the whole thing. 409/create-or-get/
  // already-sent are all treated as success — re-running is safe.

  // 1. Register (409 = already exists, fine — idempotent).
  console.log('register:');
  let regFail = 0;
  await batched(idx, (i) => withRetry(async () => {
    const r = await post('/users/register', { name: `Load ${i}`, email: email(i), password: PASSWORD });
    if (!r.ok && r.status !== 409) regFail++;
  }).catch(() => { regFail++; }));
  if (regFail) console.warn(`  ${regFail} registers failed (retried) — rerun to fill the gaps.`);

  // 2. Login -> tokens (null where login failed; those users are skipped downstream).
  console.log('login:');
  const tokens = await batched(idx, (i) => withRetry(async () => {
    const r = await post('/users/login', { email: email(i), password: PASSWORD });
    return r.ok ? (await r.json()).token : null;
  }).catch(() => null));
  const valid = idx.filter((i) => tokens[i]);
  if (!valid.length) throw new Error('no account logged in — is the API up?');
  if (valid.length < POOL_SIZE) console.warn(`  ${POOL_SIZE - valid.length} accounts have no token — rerun to complete them.`);
  const owner = tokens[valid[0]];

  // 3. One GET /users to map email -> id (cheaper than a search per user).
  // Retried — under concurrent load this single request being refused must not
  // abort the whole seed.
  const idByEmail = await withRetry(async () => {
    const all = await fetch(`${API}/users`, { headers: { authorization: `Bearer ${owner}` } });
    if (!all.ok) throw new Error(`GET /users -> ${all.status}`);
    return new Map((await all.json()).map((u) => [u.email, u.id]));
  });

  // 4. owner opens a 1:1 channel with each other valid account (create-or-get).
  console.log('channels:');
  const channelId = new Array(POOL_SIZE);
  const others = valid.filter((i) => i !== valid[0]);
  await batched(others, (i) => withRetry(async () => {
    const r = await post('/direct-channels', { participantId: idByEmail.get(email(i)) }, owner);
    if (r.ok) channelId[i] = (await r.json()).id;
  }).catch(() => { }));
  channelId[valid[0]] = channelId[others[0]]; // owner also participates in that channel

  // 5. Every valid account sends a friend request to INVITE_TARGET.
  const targetId = idByEmail.get(INVITE_TARGET);
  if (!targetId) {
    console.warn(`\n!! ${INVITE_TARGET} not found — skipping friend requests. Register that account first to include this step.`);
  } else {
    console.log(`friend requests -> ${INVITE_TARGET}:`);
    let failed = 0;
    await batched(valid, (i) => withRetry(async () => {
      if (idByEmail.get(email(i)) === targetId) return; // don't self-request
      const r = await post('/friendships/send', { targetUserId: targetId }, tokens[i]);
      if (!r.ok) failed++; // 500 usually = already active/sent on a rerun — non-fatal
    }).catch(() => { failed++; }));
    if (failed) console.warn(`  ${failed} friend requests non-200 (likely already sent on a previous run).`);
  }

  // 6. Persist the pool — only accounts with a token and a channel.
  const pool = valid.filter((i) => channelId[i]).map((i) => ({ email: email(i), token: tokens[i], channelId: channelId[i] }));
  writeFileSync(new URL('./.pool.json', import.meta.url), JSON.stringify(pool, null, 2));
  console.log(`Wrote benchmark/.pool.json with ${pool.length} entries. Tokens valid ~2h.`);
}

main().catch((e) => { console.error('\n' + e.message); process.exit(1); });
