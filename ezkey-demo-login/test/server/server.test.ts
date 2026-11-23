// Back-end tests: the real HTTP handler and database, with a fake Ezkey Integration API client.
import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { EzkeyIntegrationError, type AuthAttemptWaitResult } from 'ezkey-javascript-sdk';
import type { DemoLoginConfig } from '../../src/server/config.ts';
import { DemoLoginDb } from '../../src/server/db.ts';
import { createHandler } from '../../src/server/http.ts';
import { LoginFlow, type IntegrationClient } from '../../src/server/login-flow.ts';

const config: DemoLoginConfig = {
  port: 0,
  dbPath: ':memory:',
  staticDir: './does-not-exist',
  integrationApiUrl: 'http://ezkey.test',
  integrationKey: 'k',
  secretKey: 's',
  authTtlSeconds: 60,
  sessionHours: 8,
  cookieSecure: false,
  trustProxy: false,
  loginRateLimitPerMinute: 100,
};

class FakeEzkey implements IntegrationClient {
  calls: string[] = [];
  createError: unknown = null;
  waitError: unknown = null;
  cancelError: unknown = null;
  /** When set, the created attempt is already expired on the app clock. */
  createExpiresAt: string | null = null;
  waitResults: AuthAttemptWaitResult[] = [];

  async createAuthAttemptByUserIdentifier(userIdentifier: string, options: { challengeRequested: boolean }) {
    this.calls.push(`create:${userIdentifier}:${options.challengeRequested}`);
    if (this.createError) throw this.createError;
    return {
      authAttemptId: 41,
      authAttemptChallenge: options.challengeRequested ? 7 : null,
      timeoutSeconds: 60,
      expiresAt: this.createExpiresAt ?? new Date(Date.now() + 60_000).toISOString(),
    };
  }
  async waitForAuthAttempt(id: number) {
    this.calls.push(`wait:${id}`);
    if (this.waitError) throw this.waitError;
    return this.waitResults.shift() ?? { status: 'PENDING' as const, completed: false, timeoutReached: true };
  }
  async cancelAuthAttempt(id: number) {
    this.calls.push(`cancel:${id}`);
    if (this.cancelError) throw this.cancelError;
    return { authAttemptId: id, authAttemptStatus: 'EXPIRED' };
  }
}

let db: DemoLoginDb;
let ezkey: FakeEzkey;
let server: Server;
let base: string;

beforeEach(async () => {
  db = new DemoLoginDb(':memory:');
  db.addUser('alice', 'Alice Martin', 'alice@ezkey');
  ezkey = new FakeEzkey();
  server = createServer(createHandler(config, db, new LoginFlow(db, ezkey, 60)));
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});

afterEach(async () => {
  await new Promise((r) => server.close(r));
  db.close();
});

async function post(path: string, body: unknown = {}, headers: Record<string, string> = {}) {
  const res = await fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
  return { status: res.status, body: (await res.json()) as Record<string, unknown>, cookie: res.headers.get('set-cookie') };
}

test('approved: maps the app user to its Ezkey id, opens a session, /api/me works, logout ends it', async () => {
  const start = await post('/api/login', { username: ' Alice ', challengeRequested: true });
  assert.equal(start.status, 200);
  assert.equal(start.body.challengeCode, 7);
  assert.deepEqual(ezkey.calls, ['create:alice@ezkey:true']);

  ezkey.waitResults.push({ status: 'ACCEPTED', completed: true, timeoutReached: false });
  const done = await post(`/api/login/${start.body.loginId}/wait`);
  assert.equal(done.body.state, 'approved');
  assert.match(done.cookie ?? '', /demo_login_session=[^;]+; Path=\/; HttpOnly; SameSite=Strict/);
  const cookie = done.cookie!.split(';')[0];

  const me = await fetch(base + '/api/me', { headers: { cookie } });
  assert.equal(me.status, 200);
  assert.equal(((await me.json()) as { user: { username: string } }).user.username, 'alice');

  await post('/api/logout', {}, { cookie });
  assert.equal((await fetch(base + '/api/me', { headers: { cookie } })).status, 401);
});

test('the session token is stored hashed, never in clear', async () => {
  const start = await post('/api/login', { username: 'alice' });
  ezkey.waitResults.push({ status: 'ACCEPTED', completed: true, timeoutReached: false });
  const done = await post(`/api/login/${start.body.loginId}/wait`);
  const token = done.cookie!.split(';')[0].split('=')[1];
  assert.equal(db.findSession(token), null);
});

test('unknown or disabled app user never reaches Ezkey', async () => {
  const unknown = await post('/api/login', { username: 'mallory' });
  assert.deepEqual([unknown.status, unknown.body.error], [403, 'account-unavailable']);
  db.setUserActive('alice', false);
  const disabled = await post('/api/login', { username: 'alice' });
  assert.deepEqual([disabled.status, disabled.body.error], [403, 'account-unavailable']);
  assert.deepEqual(ezkey.calls, []);
});

test('refused, invalid and expired outcomes are reported without a session', async () => {
  for (const [status, state] of [['REJECTED', 'refused'], ['INVALID', 'invalid'], ['EXPIRED', 'expired']] as const) {
    const start = await post('/api/login', { username: 'alice' });
    ezkey.waitResults.push({ status, completed: true, timeoutReached: false });
    const done = await post(`/api/login/${start.body.loginId}/wait`);
    assert.equal(done.body.state, state);
    assert.equal(done.cookie, null);
    // A finished login cannot be waited on again.
    assert.equal((await post(`/api/login/${start.body.loginId}/wait`)).body.error, 'login-not-found');
  }
});

test('pending reports whether the device opened the request', async () => {
  const start = await post('/api/login', { username: 'alice' });
  ezkey.waitResults.push({ status: 'READ', completed: false, timeoutReached: true });
  assert.deepEqual((await post(`/api/login/${start.body.loginId}/wait`)).body, { state: 'pending', read: true });
});

test('cancel withdraws the attempt in Ezkey', async () => {
  const start = await post('/api/login', { username: 'alice' });
  assert.equal((await post(`/api/login/${start.body.loginId}/cancel`)).body.state, 'cancelled');
  assert.deepEqual(ezkey.calls, ['create:alice@ezkey:true', 'cancel:41']);
});

test('user disabled while approving: the application still refuses', async () => {
  const start = await post('/api/login', { username: 'alice' });
  db.setUserActive('alice', false);
  ezkey.waitResults.push({ status: 'ACCEPTED', completed: true, timeoutReached: false });
  const done = await post(`/api/login/${start.body.loginId}/wait`);
  assert.deepEqual([done.status, done.body.error, done.cookie], [403, 'account-unavailable', null]);
});

test('Ezkey errors map to stable codes', async () => {
  const cases: Array<[EzkeyIntegrationError, number, string]> = [
    [new EzkeyIntegrationError('x', 0), 503, 'service-unavailable'],
    [new EzkeyIntegrationError('x', 401), 503, 'configuration-error'],
    [new EzkeyIntegrationError('x', 403), 403, 'enrollment-other-integration'],
    [new EzkeyIntegrationError('x', 400), 409, 'enrollment-unavailable'],
    [new EzkeyIntegrationError('x', 404), 409, 'enrollment-unavailable'],
    [new EzkeyIntegrationError('x', 422), 409, 'enrollment-unavailable'],
    [new EzkeyIntegrationError('x', 409, 'https://ezkey.io/problems/auth-attempt-pending-exists'), 409, 'attempt-pending'],
    [new EzkeyIntegrationError('x', 409, 'https://ezkey.io/problems/auth-attempt-creation-cooldown'), 409, 'cooldown'],
    [new EzkeyIntegrationError('x', 409, 'https://ezkey.io/problems/enrollment-suspended'), 409, 'cooldown'],
    [new EzkeyIntegrationError('x', 429), 429, 'rate-limited'],
    [new EzkeyIntegrationError('x', 500), 503, 'service-unavailable'],
  ];
  for (const [error, status, code] of cases) {
    ezkey.createError = error;
    const res = await post('/api/login', { username: 'alice' });
    assert.deepEqual([res.status, res.body.error], [status, code], `HTTP ${error.status} ${error.problemType ?? ''}`);
  }
});

test('blank, non-string, and oversized usernames never reach Ezkey', async () => {
  const blank = await post('/api/login', { username: '   ' });
  const number = await post('/api/login', { username: 12 });
  const longName = await post('/api/login', { username: 'a'.repeat(129) });
  assert.deepEqual([blank.status, blank.body.error], [400, 'invalid-request']);
  assert.deepEqual([number.status, number.body.error], [400, 'invalid-request']);
  assert.deepEqual([longName.status, longName.body.error], [400, 'invalid-request']);
  assert.deepEqual(ezkey.calls, []);
});

test('a failed wait drops the pending login and opens no session', async () => {
  const start = await post('/api/login', { username: 'alice' });
  ezkey.waitError = new EzkeyIntegrationError('down', 0);
  const done = await post(`/api/login/${start.body.loginId}/wait`);
  assert.deepEqual([done.status, done.body.error, done.cookie], [503, 'service-unavailable', null]);
  assert.equal((await post(`/api/login/${start.body.loginId}/wait`)).body.error, 'login-not-found');
});

test('cancel still ends the login when Ezkey refuses the cancel', async () => {
  const start = await post('/api/login', { username: 'alice' });
  ezkey.cancelError = new EzkeyIntegrationError('gone', 409);
  const done = await post(`/api/login/${start.body.loginId}/cancel`);
  assert.deepEqual([done.status, done.body.state], [200, 'cancelled']);
  assert.equal((await post(`/api/login/${start.body.loginId}/wait`)).body.error, 'login-not-found');
});

test('a still-pending Ezkey result expires once the app clock is past the attempt', async () => {
  ezkey.createExpiresAt = new Date(Date.now() - 10_000).toISOString();
  const start = await post('/api/login', { username: 'alice' });
  ezkey.waitResults.push({ status: 'PENDING', completed: false, timeoutReached: true });
  const done = await post(`/api/login/${start.body.loginId}/wait`);
  assert.equal(done.body.state, 'expired');
  assert.equal(done.cookie, null);
  assert.equal((await post(`/api/login/${start.body.loginId}/wait`)).body.error, 'login-not-found');
});

test('not configured: refused before any call', async () => {
  const flow = new LoginFlow(db, null, 60);
  await assert.rejects(flow.start('alice', true), /not-configured/);
});

test('unsafe requests must be same-origin JSON', async () => {
  const form = await fetch(base + '/api/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'username=alice' });
  assert.equal(form.status, 403);
  const crossOrigin = await post('/api/login', { username: 'alice' }, { Origin: 'https://evil.example' });
  assert.equal(crossOrigin.status, 403);
  assert.deepEqual(ezkey.calls, []);
});

test('security headers are set on API responses', async () => {
  const res = await fetch(base + '/api/info');
  assert.match(res.headers.get('content-security-policy') ?? '', /frame-ancestors 'none'/);
  assert.equal(res.headers.get('x-content-type-options'), 'nosniff');
  assert.equal(res.headers.get('cache-control'), 'no-store');
});

test('a forged session cookie is ignored', async () => {
  const res = await fetch(base + '/api/me', { headers: { cookie: 'demo_login_session=forged-token' } });
  assert.equal(res.status, 401);
});

test('static files: the shell at /, real files only, 404 otherwise', async () => {
  const { mkdtempSync, writeFileSync, mkdirSync } = await import('node:fs');
  const { tmpdir } = await import('node:os');
  const { join } = await import('node:path');
  const dir = mkdtempSync(join(tmpdir(), 'demo-login-web-'));
  writeFileSync(join(dir, 'index.html'), '<!doctype html><title>shell</title>');
  mkdirSync(join(dir, 'assets'));
  writeFileSync(join(dir, 'assets', 'app.js'), 'console.log(1)');
  const staticServer = createServer(createHandler({ ...config, staticDir: dir }, db, new LoginFlow(db, ezkey, 60)));
  await new Promise<void>((r) => staticServer.listen(0, '127.0.0.1', r));
  const url = `http://127.0.0.1:${(staticServer.address() as AddressInfo).port}`;
  try {
    assert.equal((await fetch(url + '/')).status, 200);
    const asset = await fetch(url + '/assets/app.js');
    assert.equal(asset.status, 200);
    assert.match(asset.headers.get('cache-control') ?? '', /immutable/);
    for (const path of ['/actuator/health', '/monitoring', '/../package.json', '/%2e%2e/package.json', '/%E0%A4%A']) {
      assert.equal((await fetch(url + path)).status, 404, path);
    }
  } finally {
    await new Promise((r) => staticServer.close(r));
  }
});
