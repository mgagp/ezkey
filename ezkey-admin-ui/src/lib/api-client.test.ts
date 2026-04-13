import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const replace = vi.fn();
const authMocks = vi.hoisted(() => ({
  clearSession: vi.fn(),
  getToken: vi.fn(),
}));

vi.mock('./auth', () => ({
  clearSession: authMocks.clearSession,
  getToken: authMocks.getToken,
}));

import { ApiError, fetchApi, fetchBlobUrl } from './api-client';

function jsonResponse(status: number, body: unknown) {
  const raw = typeof body === 'string' ? body : JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : ''),
    },
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(raw),
  };
}

describe('fetchApi', () => {
  beforeEach(() => {
    vi.stubGlobal('window', { location: { replace } });
    vi.stubGlobal('fetch', vi.fn());
    authMocks.clearSession.mockReset();
    authMocks.getToken.mockReset();
    replace.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('on 401 with requireAuth (default) and no bearerToken, clears session and redirects to login even without a token', async () => {
    authMocks.getToken.mockReturnValue(null);
    vi.mocked(globalThis.fetch).mockResolvedValue(jsonResponse(401, { detail: 'Unauthorized' }));

    await expect(fetchApi('/api/v1/overview')).rejects.toMatchObject({
      status: 401,
      message: 'Session expired. Please log in again.',
    });

    expect(authMocks.clearSession).toHaveBeenCalledOnce();
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

    expect(authMocks.clearSession).not.toHaveBeenCalled();
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

    expect(authMocks.clearSession).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
  });
});

describe('fetchBlobUrl', () => {
  beforeEach(() => {
    vi.stubGlobal('window', { location: { replace } });
    vi.stubGlobal('fetch', vi.fn());
    authMocks.clearSession.mockReset();
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

    expect(authMocks.clearSession).toHaveBeenCalledOnce();
    expect(replace).toHaveBeenCalledWith('/login');
  });
});
