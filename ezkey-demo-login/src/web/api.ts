// Demo Login — calls to this application's own back end (same origin; the session is an HttpOnly cookie).

export interface Me {
  user: { username: string; displayName: string; lastLoginAt: string | null };
  session: { createdAt: string; expiresAt: string };
}

export interface LoginStarted {
  loginId: string;
  challengeCode: number | null;
  expiresAt: string;
  timeoutSeconds: number;
}

export type WaitOutcome =
  | { state: 'pending'; read: boolean }
  | { state: 'approved' }
  | { state: 'refused' }
  | { state: 'invalid' }
  | { state: 'expired' };

export class ApiError extends Error {
  readonly code: string;
  constructor(code: string) {
    super(code);
    this.code = code;
  }
}

async function call<T>(method: 'GET' | 'POST', path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      method,
      credentials: 'same-origin',
      headers: method === 'POST' ? { 'Content-Type': 'application/json' } : undefined,
      body: method === 'POST' ? JSON.stringify(body ?? {}) : undefined,
      signal,
    });
  } catch (err) {
    if ((err as Error).name === 'AbortError') throw err;
    throw new ApiError('network');
  }
  const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) throw new ApiError(typeof data.error === 'string' ? data.error : 'unexpected');
  return data as T;
}

export const api = {
  info: () => call<{ configured: boolean; ttlSeconds: number }>('GET', '/api/info'),
  me: () => call<Me>('GET', '/api/me'),
  startLogin: (username: string, challengeRequested: boolean) =>
    call<LoginStarted>('POST', '/api/login', { username, challengeRequested }),
  wait: (loginId: string, signal: AbortSignal) => call<WaitOutcome>('POST', `/api/login/${loginId}/wait`, {}, signal),
  cancel: (loginId: string) => call<{ state: string }>('POST', `/api/login/${loginId}/cancel`, {}),
  logout: () => call<{ state: string }>('POST', '/api/logout', {}),
};
