// Demo Login — the sign-in flow: application user lookup, then Ezkey through the TypeScript SDK.

import { randomBytes } from 'node:crypto';
import { EzkeyIntegrationClient, EzkeyIntegrationError } from 'ezkey-javascript-sdk';
import type { DemoLoginDb, User } from './db.ts';
import { log } from './log.ts';

/** What the browser is told when a sign-in cannot go on. Codes are stable; the UI translates them. */
export type LoginErrorCode =
  | 'invalid-request'
  | 'account-unavailable'
  | 'enrollment-unavailable'
  | 'enrollment-other-integration'
  | 'attempt-pending'
  | 'cooldown'
  | 'rate-limited'
  | 'not-configured'
  | 'configuration-error'
  | 'service-unavailable'
  | 'login-not-found'
  | 'unexpected';

export class LoginError extends Error {
  readonly code: LoginErrorCode;
  readonly httpStatus: number;

  constructor(code: LoginErrorCode, httpStatus: number, message?: string) {
    super(message ?? code);
    this.code = code;
    this.httpStatus = httpStatus;
  }
}

export interface LoginStarted {
  loginId: string;
  challengeCode: number | null;
  expiresAt: string;
  timeoutSeconds: number;
}

export type LoginOutcome =
  | { state: 'pending'; read: boolean }
  | { state: 'approved'; user: User }
  | { state: 'refused' }
  | { state: 'invalid' }
  | { state: 'expired' };

interface PendingLogin {
  userId: number;
  username: string;
  authAttemptId: number;
  expiresAtMs: number;
}

/** Subset of the SDK client the flow uses (lets tests supply a fake). */
export type IntegrationClient = Pick<
  EzkeyIntegrationClient,
  'createAuthAttemptByUserIdentifier' | 'waitForAuthAttempt' | 'cancelAuthAttempt'
>;

const MAX_PENDING_LOGINS = 1000;
const WAIT_SLICE_SECONDS = 25;
const USERNAME_MAX = 128;

export class LoginFlow {
  private readonly pending = new Map<string, PendingLogin>();
  private readonly db: DemoLoginDb;
  private readonly ezkey: IntegrationClient | null;
  private readonly ttlSeconds: number;

  constructor(db: DemoLoginDb, ezkey: IntegrationClient | null, ttlSeconds: number) {
    this.db = db;
    this.ezkey = ezkey;
    this.ttlSeconds = ttlSeconds;
  }

  get configured(): boolean {
    return this.ezkey != null;
  }

  async start(rawUsername: unknown, challengeRequested: boolean): Promise<LoginStarted> {
    const username = typeof rawUsername === 'string' ? rawUsername.trim() : '';
    if (!username || username.length > USERNAME_MAX) throw new LoginError('invalid-request', 400);

    // The application owns its users: a name it does not know never reaches Ezkey.
    const user = this.db.findUser(username);
    if (!user || !user.active) {
      log.info('login_refused_by_app', { reason: user ? 'user-inactive' : 'user-unknown' });
      throw new LoginError('account-unavailable', 403);
    }
    if (!this.ezkey) {
      log.error('login_not_configured', { hint: 'set EZKEY_INTEGRATION_KEY and EZKEY_SECRET_KEY' });
      throw new LoginError('not-configured', 503);
    }

    this.sweep();
    if (this.pending.size >= MAX_PENDING_LOGINS) throw new LoginError('rate-limited', 429);

    let created;
    try {
      created = await this.ezkey.createAuthAttemptByUserIdentifier(user.ezkeyUserIdentifier, {
        challengeRequested,
        ttlSeconds: this.ttlSeconds,
      });
    } catch (err) {
      throw mapEzkeyError(err, 'create', user.username);
    }

    const loginId = randomBytes(24).toString('base64url');
    const expiresAtMs = Date.parse(created.expiresAt) || Date.now() + created.timeoutSeconds * 1000;
    this.pending.set(loginId, {
      userId: user.id,
      username: user.username,
      authAttemptId: created.authAttemptId,
      expiresAtMs,
    });
    log.info('login_started', { user: user.username, authAttemptId: created.authAttemptId });
    return {
      loginId,
      challengeCode: created.authAttemptChallenge,
      expiresAt: new Date(expiresAtMs).toISOString(),
      timeoutSeconds: created.timeoutSeconds,
    };
  }

  /** Long-polls Ezkey for at most WAIT_SLICE_SECONDS; the browser calls again while pending. */
  async wait(loginId: string): Promise<LoginOutcome> {
    const login = this.pending.get(loginId);
    if (!login || !this.ezkey) throw new LoginError('login-not-found', 404);

    const remaining = Math.ceil((login.expiresAtMs - Date.now()) / 1000);
    const slice = Math.max(1, Math.min(WAIT_SLICE_SECONDS, remaining + 2));
    let result;
    try {
      result = await this.ezkey.waitForAuthAttempt(login.authAttemptId, { timeoutSeconds: slice, pollingSeconds: 1 });
    } catch (err) {
      this.pending.delete(loginId);
      throw mapEzkeyError(err, 'wait', login.username);
    }

    switch (result.status) {
      case 'ACCEPTED': {
        this.pending.delete(loginId);
        const user = this.db.findUserById(login.userId);
        if (!user || !user.active) {
          // Deactivated or removed while the user was approving: the application still decides.
          log.info('login_refused_by_app', { reason: 'user-changed-during-login', user: login.username });
          throw new LoginError('account-unavailable', 403);
        }
        log.info('login_approved', { user: user.username, authAttemptId: login.authAttemptId });
        return { state: 'approved', user };
      }
      case 'REJECTED':
        this.pending.delete(loginId);
        log.info('login_rejected', { user: login.username, authAttemptId: login.authAttemptId });
        return { state: 'refused' };
      case 'INVALID':
        this.pending.delete(loginId);
        log.info('login_invalid', { user: login.username, authAttemptId: login.authAttemptId });
        return { state: 'invalid' };
      case 'EXPIRED':
        this.pending.delete(loginId);
        log.info('login_expired', { user: login.username, authAttemptId: login.authAttemptId });
        return { state: 'expired' };
      default:
        if (Date.now() > login.expiresAtMs + 5000) {
          this.pending.delete(loginId);
          log.info('login_expired', { user: login.username, authAttemptId: login.authAttemptId, by: 'app-clock' });
          return { state: 'expired' };
        }
        return { state: 'pending', read: result.status === 'READ' };
    }
  }

  async cancel(loginId: string): Promise<void> {
    const login = this.pending.get(loginId);
    if (!login || !this.ezkey) return;
    this.pending.delete(loginId);
    try {
      await this.ezkey.cancelAuthAttempt(login.authAttemptId);
      log.info('login_cancelled', { user: login.username, authAttemptId: login.authAttemptId });
    } catch (err) {
      // Already final (409) or gone: nothing left to cancel. Keep it observable.
      const status = err instanceof EzkeyIntegrationError ? err.status : -1;
      log.warn('login_cancel_failed', { user: login.username, authAttemptId: login.authAttemptId, status });
    }
  }

  private sweep(): void {
    const cutoff = Date.now() - 60_000;
    for (const [id, login] of this.pending) {
      if (login.expiresAtMs < cutoff) this.pending.delete(id);
    }
  }
}

/** Turns an SDK error into what the browser may see; the precise cause goes to the log. */
export function mapEzkeyError(err: unknown, step: 'create' | 'wait', username: string): LoginError {
  if (!(err instanceof EzkeyIntegrationError)) {
    log.error('ezkey_call_failed', { step, user: username, error: (err as Error)?.message ?? String(err) });
    return new LoginError('unexpected', 500);
  }
  const type = err.problemType ?? '';
  log.warn('ezkey_call_refused', { step, user: username, status: err.status, problemType: type || null, detail: err.detail });

  if (err.status === 0) return new LoginError('service-unavailable', 503);
  if (err.status === 401) return new LoginError('configuration-error', 503);
  if (err.status === 429) return new LoginError('rate-limited', 429);
  if (err.status === 403) return new LoginError('enrollment-other-integration', 403);
  if (err.status === 409) {
    return type.includes('cooldown') || type.includes('suspended')
      ? new LoginError('cooldown', 409)
      : new LoginError('attempt-pending', 409);
  }
  if (err.status === 400 || err.status === 404 || err.status === 422) {
    return new LoginError('enrollment-unavailable', 409);
  }
  if (err.status >= 500) return new LoginError('service-unavailable', 503);
  return new LoginError('unexpected', 500);
}
