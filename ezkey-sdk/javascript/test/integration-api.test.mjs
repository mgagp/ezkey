// Unit tests for the Integration API client (run after `npm run build`: `npm test`).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { EzkeyIntegrationClient, EzkeyIntegrationError } = require('../dist/src/index.js');

function fakeFetch(handler) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init });
    const { status, body } = handler(url, init);
    return new Response(body == null ? '' : JSON.stringify(body), { status });
  };
  fn.calls = calls;
  return fn;
}

const base = { baseUrl: 'http://ezkey.test:7080/', integrationKey: 'ezkey_ikey_a', secretKey: 'ezkey_skey_b' };

test('create by user identifier sends Basic auth, trimmed identifier and options', async () => {
  const fetch = fakeFetch(() => ({
    status: 201,
    body: { authAttemptId: 7, authAttemptChallenge: 42, timeoutSeconds: 60, expiresAt: '2026-01-01T00:00:00Z' },
  }));
  const client = new EzkeyIntegrationClient({ ...base, fetch });
  const created = await client.createAuthAttemptByUserIdentifier('  alice ', { challengeRequested: true, ttlSeconds: 60 });

  assert.deepEqual(created, { authAttemptId: 7, authAttemptChallenge: 42, timeoutSeconds: 60, expiresAt: '2026-01-01T00:00:00Z' });
  const { url, init } = fetch.calls[0];
  assert.equal(url, 'http://ezkey.test:7080/api/v1/auth-attempts');
  assert.equal(init.method, 'POST');
  assert.equal(init.headers.Authorization, 'Basic ' + Buffer.from('ezkey_ikey_a:ezkey_skey_b').toString('base64'));
  assert.deepEqual(JSON.parse(init.body), { userIdentifier: 'alice', challengeRequested: true, ttlSeconds: 60 });
});

test('a missing challenge is reported as null', async () => {
  const fetch = fakeFetch(() => ({ status: 201, body: { authAttemptId: 1, timeoutSeconds: 120, expiresAt: 'x' } }));
  const created = await new EzkeyIntegrationClient({ ...base, fetch }).createAuthAttempt(5, { challengeRequested: false });
  assert.equal(created.authAttemptChallenge, null);
  assert.deepEqual(JSON.parse(fetch.calls[0].init.body), { enrollmentId: 5, challengeRequested: false });
});

test('blank user identifier is refused before any call', async () => {
  const fetch = fakeFetch(() => ({ status: 201, body: {} }));
  const client = new EzkeyIntegrationClient({ ...base, fetch });
  await assert.rejects(client.createAuthAttemptByUserIdentifier('   ', { challengeRequested: false }), /blank/);
  assert.equal(fetch.calls.length, 0);
});

test('wait passes timeout and polling and maps the result', async () => {
  const fetch = fakeFetch(() => ({ status: 200, body: { status: 'ACCEPTED', completed: true, timeoutReached: false } }));
  const result = await new EzkeyIntegrationClient({ ...base, fetch }).waitForAuthAttempt(9, { timeoutSeconds: 20, pollingSeconds: 1 });
  assert.deepEqual(result, { status: 'ACCEPTED', completed: true, timeoutReached: false });
  assert.equal(fetch.calls[0].url, 'http://ezkey.test:7080/api/v1/auth-attempts/9/wait?timeout=20&polling=1');
});

test('cancel maps the response', async () => {
  const fetch = fakeFetch(() => ({ status: 200, body: { authAttemptId: 9, authAttemptStatus: 'EXPIRED' } }));
  const result = await new EzkeyIntegrationClient({ ...base, fetch }).cancelAuthAttempt(9);
  assert.deepEqual(result, { authAttemptId: 9, authAttemptStatus: 'EXPIRED' });
  assert.equal(fetch.calls[0].init.method, 'POST');
});

test('an RFC 9457 problem becomes an EzkeyIntegrationError', async () => {
  const fetch = fakeFetch(() => ({
    status: 409,
    body: { type: 'https://ezkey.io/problems/auth-attempt-pending-exists', title: 'Conflict', detail: 'A live attempt exists' },
  }));
  const err = await new EzkeyIntegrationClient({ ...base, fetch })
    .createAuthAttemptByUserIdentifier('alice', { challengeRequested: false })
    .catch((e) => e);
  assert.ok(err instanceof EzkeyIntegrationError);
  assert.equal(err.status, 409);
  assert.equal(err.problemType, 'https://ezkey.io/problems/auth-attempt-pending-exists');
  assert.equal(err.detail, 'A live attempt exists');
});

test('an unreachable server gives status 0', async () => {
  const fetch = async () => {
    throw new TypeError('fetch failed');
  };
  const err = await new EzkeyIntegrationClient({ ...base, fetch }).cancelAuthAttempt(1).catch((e) => e);
  assert.ok(err instanceof EzkeyIntegrationError);
  assert.equal(err.status, 0);
});
