/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyIntegrationClient
 * Description: Integration API client (API key, machine to machine): create, wait for and cancel
 * authentication attempts. Uses the standard fetch API only, so it runs on Node 18+ and on any
 * runtime that provides fetch.
 */

/** Configuration of the Integration API client. */
export interface EzkeyIntegrationConfig {
  /** Integration API base URL, e.g. `http://localhost:7080` or `https://integration-api.example.com`. */
  baseUrl: string;
  /** Public integration key (`ezkey_ikey_...`). */
  integrationKey: string;
  /** Secret key (`ezkey_skey_...`). Keep it on the server; never send it to a browser. */
  secretKey: string;
  /** Timeout of each HTTP request in milliseconds (default 60 000; the wait call long-polls). */
  requestTimeoutMs?: number;
  /** Optional fetch implementation (defaults to the global `fetch`). */
  fetch?: typeof fetch;
}

/** Options shared by both ways of creating an authentication attempt. */
export interface AuthAttemptCreateOptions {
  /** Ask Ezkey for a two-digit challenge code that the user picks on the device. */
  challengeRequested: boolean;
  /** Lifetime of the attempt in seconds (minimum 15; the server caps it, typically at 120). */
  ttlSeconds?: number;
  /** Optional short title shown on the device (max 200 characters). */
  contextTitle?: string;
  /** Optional message shown on the device (max 2 000 characters). */
  contextMessage?: string;
}

/** Result of creating an authentication attempt. */
export interface AuthAttemptCreated {
  authAttemptId: number;
  /** Two-digit challenge code to show the user, or null when none was requested. */
  authAttemptChallenge: number | null;
  timeoutSeconds: number;
  expiresAt: string;
}

/** Status of an authentication attempt as reported by the wait call. */
export type AuthAttemptStatus = 'PENDING' | 'READ' | 'INVALID' | 'REJECTED' | 'ACCEPTED' | 'EXPIRED';

/** Result of a wait call. */
export interface AuthAttemptWaitResult {
  status: AuthAttemptStatus;
  /** True when the attempt reached a final state. */
  completed: boolean;
  /** True when the server-side wait ended before the attempt completed. */
  timeoutReached: boolean;
}

/** Result of a cancel call (a cancelled attempt is reported as `EXPIRED`). */
export interface AuthAttemptCancelled {
  authAttemptId: number;
  authAttemptStatus: string;
}

/**
 * Error raised by the Integration API client.
 *
 * `status` is the HTTP status (0 when the server could not be reached). `problemType`, `title` and
 * `detail` come from the RFC 9457 problem document when the server sent one.
 */
export class EzkeyIntegrationError extends Error {
  readonly status: number;
  readonly problemType: string | null;
  readonly title: string | null;
  readonly detail: string | null;

  constructor(
    message: string,
    status: number,
    problemType: string | null = null,
    title: string | null = null,
    detail: string | null = null,
  ) {
    super(message);
    this.name = 'EzkeyIntegrationError';
    this.status = status;
    this.problemType = problemType;
    this.title = title;
    this.detail = detail;
  }
}

const AUTH_ATTEMPTS_PATH = '/api/v1/auth-attempts';
const DEFAULT_REQUEST_TIMEOUT_MS = 60_000;

/**
 * Integration API client: what an application backend needs to sign its users in with Ezkey.
 *
 * ```ts
 * const ezkey = new EzkeyIntegrationClient({ baseUrl, integrationKey, secretKey });
 * const attempt = await ezkey.createAuthAttemptByUserIdentifier('alice', { challengeRequested: true });
 * const result = await ezkey.waitForAuthAttempt(attempt.authAttemptId, { timeoutSeconds: 25 });
 * ```
 */
export class EzkeyIntegrationClient {
  private readonly baseUrl: string;
  private readonly authorization: string;
  private readonly requestTimeoutMs: number;
  private readonly fetchImpl: typeof fetch;

  constructor(config: EzkeyIntegrationConfig) {
    if (!config.baseUrl) throw new Error('baseUrl is required');
    if (!config.integrationKey || !config.secretKey) {
      throw new Error('integrationKey and secretKey are required');
    }
    this.baseUrl = config.baseUrl.replace(/\/+$/, '');
    this.authorization = 'Basic ' + toBase64(`${config.integrationKey}:${config.secretKey}`);
    this.requestTimeoutMs = config.requestTimeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS;
    const fetchImpl = config.fetch ?? (globalThis as { fetch?: typeof fetch }).fetch;
    if (!fetchImpl) throw new Error('No fetch implementation available (Node 18+ required)');
    this.fetchImpl = fetchImpl;
  }

  /**
   * Creates an authentication attempt for the user your application knows as `userIdentifier`.
   * Ezkey resolves it to the active, verified enrollment of that user within the integration of
   * the API key.
   */
  async createAuthAttemptByUserIdentifier(
    userIdentifier: string,
    options: AuthAttemptCreateOptions,
  ): Promise<AuthAttemptCreated> {
    const trimmed = (userIdentifier ?? '').trim();
    if (!trimmed) throw new Error('userIdentifier must not be blank');
    return this.create({ userIdentifier: trimmed, ...createBody(options) });
  }

  /** Creates an authentication attempt for a known enrollment id. */
  async createAuthAttempt(enrollmentId: number, options: AuthAttemptCreateOptions): Promise<AuthAttemptCreated> {
    return this.create({ enrollmentId, ...createBody(options) });
  }

  /**
   * Long-polls until the attempt completes or `timeoutSeconds` (1–300, default 30) elapses.
   * Call it again while `completed` is false and the attempt has not expired.
   */
  async waitForAuthAttempt(
    authAttemptId: number,
    options: { timeoutSeconds?: number; pollingSeconds?: number } = {},
  ): Promise<AuthAttemptWaitResult> {
    const timeout = options.timeoutSeconds ?? 30;
    const polling = options.pollingSeconds ?? 2;
    const body = await this.request(
      'GET',
      `${AUTH_ATTEMPTS_PATH}/${encodeURIComponent(String(authAttemptId))}/wait?timeout=${timeout}&polling=${polling}`,
      undefined,
      200,
    );
    return {
      status: body.status as AuthAttemptStatus,
      completed: body.completed === true,
      timeoutReached: body.timeoutReached === true,
    };
  }

  /** Cancels a pending attempt (only `PENDING` or `READ` attempts can be cancelled). */
  async cancelAuthAttempt(authAttemptId: number): Promise<AuthAttemptCancelled> {
    const body = await this.request(
      'POST',
      `${AUTH_ATTEMPTS_PATH}/${encodeURIComponent(String(authAttemptId))}/cancel`,
      undefined,
      200,
    );
    return {
      authAttemptId: typeof body.authAttemptId === 'number' ? body.authAttemptId : authAttemptId,
      authAttemptStatus: String(body.authAttemptStatus ?? ''),
    };
  }

  private async create(body: Record<string, unknown>): Promise<AuthAttemptCreated> {
    const res = await this.request('POST', AUTH_ATTEMPTS_PATH, body, 201);
    return {
      authAttemptId: Number(res.authAttemptId),
      authAttemptChallenge: typeof res.authAttemptChallenge === 'number' ? res.authAttemptChallenge : null,
      timeoutSeconds: typeof res.timeoutSeconds === 'number' ? res.timeoutSeconds : 120,
      expiresAt: String(res.expiresAt ?? ''),
    };
  }

  private async request(
    method: 'GET' | 'POST',
    path: string,
    body: Record<string, unknown> | undefined,
    expectedStatus: number,
  ): Promise<Record<string, unknown>> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.requestTimeoutMs);
    let response: Response;
    try {
      response = await this.fetchImpl(this.baseUrl + path, {
        method,
        headers: {
          Authorization: this.authorization,
          Accept: 'application/json',
          ...(body ? { 'Content-Type': 'application/json' } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });
    } catch (err) {
      const reason = controller.signal.aborted ? 'request timed out' : 'server unreachable';
      throw new EzkeyIntegrationError(`Integration API ${reason}: ${(err as Error).message}`, 0);
    } finally {
      clearTimeout(timer);
    }

    const text = await response.text();
    let json: Record<string, unknown> = {};
    if (text) {
      try {
        json = JSON.parse(text) as Record<string, unknown>;
      } catch {
        json = {};
      }
    }
    if (response.status !== expectedStatus) {
      const str = (v: unknown): string | null => (typeof v === 'string' && v ? v : null);
      const title = str(json.title);
      const detail = str(json.detail);
      throw new EzkeyIntegrationError(
        `Integration API ${method} ${path.split('?')[0]} returned ${response.status}` +
          (title ? `: ${title}` : ''),
        response.status,
        str(json.type),
        title,
        detail,
      );
    }
    return json;
  }
}

function createBody(options: AuthAttemptCreateOptions): Record<string, unknown> {
  const body: Record<string, unknown> = { challengeRequested: options.challengeRequested };
  if (options.ttlSeconds != null) body.ttlSeconds = options.ttlSeconds;
  if (options.contextTitle != null) body.contextTitle = options.contextTitle;
  if (options.contextMessage != null) body.contextMessage = options.contextMessage;
  return body;
}

function toBase64(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary);
}
