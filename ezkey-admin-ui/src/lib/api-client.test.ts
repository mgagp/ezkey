import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const replace = vi.fn();
const authMocks = vi.hoisted(() => ({
  getBrowserCredentials: vi.fn<() => RequestCredentials | undefined>(() => undefined),
  getCsrfHeaderName: vi.fn(() => 'X-CSRF-TOKEN'),
  getCsrfToken: vi.fn<() => string | null>(() => null),
  getToken: vi.fn(),
  isUnsafeHttpMethod: vi.fn((method?: string) => !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method ?? 'GET').toUpperCase())),
}));
const lifecycleMocks = vi.hoisted(() => ({
  clearLocalAuthOnSessionInvalidation: vi.fn(),
}));

vi.mock('./auth', () => ({
  getBrowserCredentials: authMocks.getBrowserCredentials,
  getCsrfHeaderName: authMocks.getCsrfHeaderName,
  getCsrfToken: authMocks.getCsrfToken,
  getToken: authMocks.getToken,
  isUnsafeHttpMethod: authMocks.isUnsafeHttpMethod,
}));

vi.mock('./auth-session-lifecycle', () => ({
  clearLocalAuthOnSessionInvalidation: lifecycleMocks.clearLocalAuthOnSessionInvalidation,
}));

import { ApiError, fetchApi, fetchBlobUrl } from './api-client';

function jsonResponse(status: number, body: unknown): Response {
  const raw = typeof body === 'string' ? body : JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : ''),
    },
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(raw),
  } as Response;
}

describe('fetchApi', () => {
  beforeEach(() => {
    vi.stubGlobal('window', { location: { replace } });
    vi.stubGlobal('fetch', vi.fn());
    lifecycleMocks.clearLocalAuthOnSessionInvalidation.mockReset();
    authMocks.getBrowserCredentials.mockReset();
    authMocks.getBrowserCredentials.mockReturnValue(undefined);
    authMocks.getCsrfHeaderName.mockReset();
    authMocks.getCsrfHeaderName.mockReturnValue('X-CSRF-TOKEN');
    authMocks.getCsrfToken.mockReset();
    authMocks.getCsrfToken.mockReturnValue(null);
    authMocks.getToken.mockReset();
    authMocks.isUnsafeHttpMethod.mockImplementation((method?: string) => !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method ?? 'GET').toUpperCase()));
    replace.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('when HttpOnly cookie mode, sends credentials include on requests', async () => {
    authMocks.getBrowserCredentials.mockReturnValue('include');
    authMocks.getToken.mockReturnValue(null);
    vi.mocked(globalThis.fetch).mockResolvedValue(jsonResponse(200, { ok: true }));

    await fetchApi('/api/v1/overview');

    expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('when CSRF token is available, sends it on unsafe cookie-mode requests', async () => {
    authMocks.getBrowserCredentials.mockReturnValue('include');
    authMocks.getCsrfToken.mockReturnValue('csrf-token');
    vi.mocked(globalThis.fetch).mockResolvedValue(jsonResponse(200, { ok: true }));

    await fetchApi('/api/v1/admin/auth/logout', { method: 'POST' });

    expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }),
      }),
    );
  });

  it('on 401 with requireAuth (default) and no bearerToken, clears session and redirects to login even without a token', async () => {
    authMocks.getToken.mockReturnValue(null);
    vi.mocked(globalThis.fetch).mockResolvedValue(jsonResponse(401, { detail: 'Unauthorized' }));

    await expect(fetchApi('/api/v1/overview')).rejects.toMatchObject({
      status: 401,
      message: 'Session expired. Please log in again.',
    });

    expect(lifecycleMocks.clearLocalAuthOnSessionInvalidation).toHaveBeenCalledOnce();
    expect(replace).toHaveBeenCalledWith('/login');
  });

  it('on 401 with requireAuth false, does not clear session or redirect', async () => {
    authMocks.getToken.mockReturnValue(null);
    vi.mocked(globalThis.fetch).mockResolvedValue(
      jsonResponse(401, { title: 'Invalid credentials', detail: 'Check password' }),
    );

    await expect(fetchApi('/api/v1/admin/auth/login', { method: 'POST', requireAuth: false })).rejects.toThrow(
      ApiError,
    );

    expect(lifecycleMocks.clearLocalAuthOnSessionInvalidation).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
  });

  it('on 401 with bearerToken, does not clear session or redirect', async () => {
    authMocks.getToken.mockReturnValue(null);
    vi.mocked(globalThis.fetch).mockResolvedValue(jsonResponse(401, { detail: 'Invalid token' }));

    await expect(
      fetchApi('/api/v1/admin/enrollments/reset', {
        method: 'POST',
        body: '{}',
        requireAuth: false,
        bearerToken: 'recovery-token',
      }),
    ).rejects.toMatchObject({ status: 401 });

    expect(lifecycleMocks.clearLocalAuthOnSessionInvalidation).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
  });
});

describe('fetchBlobUrl', () => {
  beforeEach(() => {
    vi.stubGlobal('window', { location: { replace } });
    vi.stubGlobal('fetch', vi.fn());
    lifecycleMocks.clearLocalAuthOnSessionInvalidation.mockReset();
    authMocks.getBrowserCredentials.mockReset();
    authMocks.getBrowserCredentials.mockReturnValue(undefined);
    authMocks.getCsrfHeaderName.mockReset();
    authMocks.getCsrfHeaderName.mockReturnValue('X-CSRF-TOKEN');
    authMocks.getCsrfToken.mockReset();
    authMocks.getCsrfToken.mockReturnValue(null);
    authMocks.getToken.mockReset();
    replace.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('on 401, clears session and redirects to login', async () => {
    authMocks.getToken.mockReturnValue('jwt');
    vi.mocked(globalThis.fetch).mockResolvedValue({
      ok: false,
      status: 401,
      headers: new Headers(),
    } as Response);

    await expect(fetchBlobUrl('/api/v1/qr')).rejects.toMatchObject({
      status: 401,
      message: 'Session expired. Please log in again.',
    });

    expect(lifecycleMocks.clearLocalAuthOnSessionInvalidation).toHaveBeenCalledOnce();
    expect(replace).toHaveBeenCalledWith('/login');
  });
});
