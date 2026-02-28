import { clearSession, getToken } from './auth';

/**
 * In development, BASE_URL is empty and Vite proxies /api/* → localhost:9080.
 * In production, set VITE_API_BASE_URL to the Admin API base URL.
 */
const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

/** Typed error thrown for non-2xx responses. */
export class ApiError extends Error {
  public readonly status: number;
  public readonly body: unknown;

  constructor(status: number, body: unknown, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

interface FetchOptions extends RequestInit {
  /** When false, the Authorization header is omitted (used for login calls). */
  requireAuth?: boolean;
}

async function fetchApi<T>(path: string, options: FetchOptions = {}): Promise<T> {
  const { requireAuth = true, headers: extraHeaders, ...init } = options;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(extraHeaders as Record<string, string> | undefined),
  };

  if (requireAuth) {
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, { ...init, headers });

  if (response.status === 401) {
    clearSession();
    window.location.replace('/login');
    throw new ApiError(401, null, 'Session expired. Please log in again.');
  }

  const isJson = response.headers.get('content-type')?.includes('application/json') ?? false;

  if (!response.ok) {
    const body = isJson ? await response.json().catch(() => null) : null;
    const message =
      (body as Record<string, unknown> | null)?.detail as string ??
      (body as Record<string, unknown> | null)?.message as string ??
      `HTTP ${response.status}`;
    throw new ApiError(response.status, body, message);
  }

  if (response.status === 204 || !isJson) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

/** Typed API methods — always prefer these over raw fetch in components. */
export const api = {
  get: <T>(path: string): Promise<T> =>
    fetchApi<T>(path, { method: 'GET' }),

  post: <T>(path: string, body: unknown, requireAuth = true): Promise<T> =>
    fetchApi<T>(path, { method: 'POST', body: JSON.stringify(body), requireAuth }),

  put: <T>(path: string, body: unknown): Promise<T> =>
    fetchApi<T>(path, { method: 'PUT', body: JSON.stringify(body) }),

  delete: <T = void>(path: string): Promise<T> =>
    fetchApi<T>(path, { method: 'DELETE' }),
};
