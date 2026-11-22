// Demo Login — HTTP layer: JSON API under /api, the built single-page app for everything else.

import { createHash, randomBytes } from 'node:crypto';
import { readFile, stat } from 'node:fs/promises';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { extname, join, normalize, resolve } from 'node:path';
import type { DemoLoginConfig } from './config.ts';
import type { DemoLoginDb, User } from './db.ts';
import { LoginError, type LoginFlow } from './login-flow.ts';
import { log } from './log.ts';

const SESSION_COOKIE = 'demo_login_session';
const MAX_BODY_BYTES = 4096;

const SECURITY_HEADERS: Record<string, string> = {
  'Content-Security-Policy':
    "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'X-Frame-Options': 'DENY',
  'Cross-Origin-Opener-Policy': 'same-origin',
};

const MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.json': 'application/json',
  '.woff2': 'font/woff2',
};

export function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

/** Fixed-window per-IP limiter for sign-in starts (in memory: one container, few users). */
class RateLimiter {
  private readonly hits = new Map<string, { windowStart: number; count: number }>();
  private readonly limit: number;

  constructor(limit: number) {
    this.limit = limit;
  }

  allow(key: string): boolean {
    const now = Date.now();
    const entry = this.hits.get(key);
    if (!entry || now - entry.windowStart >= 60_000) {
      if (this.hits.size > 10_000) this.hits.clear();
      this.hits.set(key, { windowStart: now, count: 1 });
      return true;
    }
    entry.count += 1;
    return entry.count <= this.limit;
  }
}

export function createHandler(config: DemoLoginConfig, db: DemoLoginDb, flow: LoginFlow) {
  const staticRoot = resolve(config.staticDir);
  const limiter = new RateLimiter(config.loginRateLimitPerMinute);

  function send(res: ServerResponse, status: number, body: unknown, headers: Record<string, string> = {}): void {
    res.writeHead(status, {
      ...SECURITY_HEADERS,
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store',
      ...headers,
    });
    res.end(JSON.stringify(body));
  }

  function sendError(res: ServerResponse, err: LoginError): void {
    send(res, err.httpStatus, { error: err.code });
  }

  function clientIp(req: IncomingMessage): string {
    if (config.trustProxy) {
      const forwarded = req.headers['x-forwarded-for'];
      const first = (Array.isArray(forwarded) ? forwarded[0] : forwarded)?.split(',')[0]?.trim();
      if (first) return first;
    }
    return req.socket.remoteAddress ?? 'unknown';
  }

  function readCookie(req: IncomingMessage, name: string): string | null {
    for (const part of (req.headers.cookie ?? '').split(';')) {
      const [k, ...v] = part.trim().split('=');
      if (k === name) return v.join('=') || null;
    }
    return null;
  }

  function sessionCookie(value: string, maxAgeSeconds: number): string {
    return [
      `${SESSION_COOKIE}=${value}`,
      'Path=/',
      'HttpOnly',
      'SameSite=Strict',
      `Max-Age=${maxAgeSeconds}`,
      ...(config.cookieSecure ? ['Secure'] : []),
    ].join('; ');
  }

  function currentSession(req: IncomingMessage) {
    const token = readCookie(req, SESSION_COOKIE);
    if (!token || token.length > 128) return null;
    const tokenHash = hashToken(token);
    const session = db.findSession(tokenHash);
    if (!session) return null;
    const user = db.findUserById(session.userId);
    if (!user || !user.active) return null;
    return { tokenHash, session, user };
  }

  /** Unsafe requests must be JSON and same-origin (with SameSite=Strict, this closes CSRF). */
  function isSameOriginJson(req: IncomingMessage): boolean {
    const type = req.headers['content-type'] ?? '';
    if (!type.toLowerCase().startsWith('application/json')) return false;
    const origin = req.headers.origin;
    if (!origin) return true;
    try {
      const host = req.headers['x-forwarded-host'] && config.trustProxy ? String(req.headers['x-forwarded-host']) : req.headers.host;
      return new URL(origin).host === host;
    } catch {
      return false;
    }
  }

  async function readJson(req: IncomingMessage): Promise<Record<string, unknown>> {
    const chunks: Buffer[] = [];
    let size = 0;
    for await (const chunk of req) {
      size += (chunk as Buffer).length;
      if (size > MAX_BODY_BYTES) throw new LoginError('invalid-request', 413);
      chunks.push(chunk as Buffer);
    }
    if (size === 0) return {};
    try {
      const parsed = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {};
    } catch {
      throw new LoginError('invalid-request', 400);
    }
  }

  function userView(user: User) {
    return { username: user.username, displayName: user.displayName ?? user.username };
  }

  async function api(req: IncomingMessage, res: ServerResponse, path: string): Promise<void> {
    const method = req.method ?? 'GET';

    if (method !== 'GET' && !isSameOriginJson(req)) {
      send(res, 403, { error: 'invalid-request' });
      return;
    }

    if (method === 'GET' && path === '/api/info') {
      send(res, 200, { configured: flow.configured, ttlSeconds: config.authTtlSeconds });
      return;
    }

    if (method === 'POST' && path === '/api/login') {
      if (!limiter.allow(clientIp(req))) {
        log.warn('login_rate_limited', { ip: clientIp(req) });
        sendError(res, new LoginError('rate-limited', 429));
        return;
      }
      const body = await readJson(req);
      const started = await flow.start(body.username, body.challengeRequested !== false);
      send(res, 200, started);
      return;
    }

    const waitMatch = /^\/api\/login\/([A-Za-z0-9_-]{16,64})\/(wait|cancel)$/.exec(path);
    if (waitMatch && method === 'POST' && waitMatch[2] === 'wait') {
      const outcome = await flow.wait(waitMatch[1]);
      if (outcome.state === 'approved') {
        const token = randomBytes(32).toString('base64url');
        const expiresAt = new Date(Date.now() + config.sessionHours * 3600_000);
        db.createSession(hashToken(token), outcome.user.id, expiresAt);
        db.recordLogin(outcome.user.id);
        send(res, 200, { state: 'approved', user: userView(outcome.user) }, {
          'Set-Cookie': sessionCookie(token, config.sessionHours * 3600),
        });
        return;
      }
      send(res, 200, outcome);
      return;
    }
    if (waitMatch && method === 'POST' && waitMatch[2] === 'cancel') {
      await flow.cancel(waitMatch[1]);
      send(res, 200, { state: 'cancelled' });
      return;
    }

    if (method === 'GET' && path === '/api/me') {
      const current = currentSession(req);
      if (!current) {
        send(res, 401, { error: 'not-signed-in' });
        return;
      }
      send(res, 200, {
        user: { ...userView(current.user), lastLoginAt: current.user.lastLoginAt },
        session: { createdAt: current.session.createdAt, expiresAt: current.session.expiresAt },
      });
      return;
    }

    if (method === 'POST' && path === '/api/logout') {
      const current = currentSession(req);
      if (current) {
        db.deleteSession(current.tokenHash);
        log.info('logout', { user: current.user.username });
      }
      send(res, 200, { state: 'signed-out' }, { 'Set-Cookie': sessionCookie('', 0) });
      return;
    }

    send(res, 404, { error: 'not-found' });
  }

  async function serveStatic(req: IncomingMessage, res: ServerResponse, path: string): Promise<void> {
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      res.writeHead(405, SECURITY_HEADERS).end();
      return;
    }
    const candidate = normalize(join(staticRoot, decodeURIComponent(path)));
    let file = candidate.startsWith(staticRoot) ? candidate : join(staticRoot, 'index.html');
    try {
      if (!(await stat(file)).isFile()) file = join(staticRoot, 'index.html');
    } catch {
      file = join(staticRoot, 'index.html'); // client-side routes fall back to the app shell
    }
    try {
      const content = await readFile(file);
      const isAsset = file.includes(`${join(staticRoot, 'assets')}`);
      res.writeHead(200, {
        ...SECURITY_HEADERS,
        'Content-Type': MIME[extname(file)] ?? 'application/octet-stream',
        'Cache-Control': isAsset ? 'public, max-age=31536000, immutable' : 'no-cache',
      });
      res.end(req.method === 'HEAD' ? undefined : content);
    } catch {
      res.writeHead(404, { ...SECURITY_HEADERS, 'Content-Type': 'text/plain' }).end('Not found');
    }
  }

  return async function handle(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const path = new URL(req.url ?? '/', 'http://local').pathname;
    try {
      if (path === '/healthz') {
        send(res, 200, { status: 'UP' });
      } else if (path.startsWith('/api/')) {
        await api(req, res, path);
      } else {
        await serveStatic(req, res, path);
      }
    } catch (err) {
      if (err instanceof LoginError) {
        sendError(res, err);
      } else {
        log.error('request_failed', { path, error: (err as Error)?.message ?? String(err) });
        if (!res.headersSent) send(res, 500, { error: 'unexpected' });
      }
    }
  };
}
