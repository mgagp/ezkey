import { clearSession, getToken, isBrowserSessionCookieBuild } from './auth';

/**
 * In development, BASE_URL is empty and Vite proxies /api/v1 → localhost:9080.
 * In production, set VITE_API_BASE_URL to the Admin API base URL.
 */
const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

/**
 * RFC 9457 Problem Details for HTTP APIs.
 * Used by the Admin API (and others) for error responses.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  path?: string;
  /**
   * RFC 9457 extension member: scalar values for client-side localization (e.g. i18next
   * interpolation). Omitted when the problem has no dynamic fragments.
   */
  parameters?: Record<string, string | number | boolean>;
  [key: string]: unknown;
}

/** Typed error thrown for non-2xx responses. */
export class ApiError extends Error {
  public readonly status: number;
  public readonly body: unknown;
  /** Parsed RFC 9457 problem when the response body conforms to it. */
  public readonly problemDetail: ProblemDetail | null;

  constructor(status: number, body: unknown, message: string, problemDetail: ProblemDetail | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
    this.problemDetail = problemDetail ?? (isProblemDetail(body) ? (body as ProblemDetail) : null);
  }
}

function isProblemDetail(v: unknown): boolean {
  if (v == null || typeof v !== 'object') return false;
  const o = v as Record<string, unknown>;
  return (
    (typeof o.detail === 'string' || typeof o.title === 'string') &&
    (o.status == null || typeof o.status === 'number')
  );
}

/**
 * Builds a user-facing message from an RFC 9457 problem or generic error body.
 * Prefers detail (specific message), then title, then message, then HTTP status.
 */
function messageFromProblemBody(status: number, body: unknown): { message: string; problem: ProblemDetail | null } {
  const problem = isProblemDetail(body) ? (body as ProblemDetail) : null;
  const detail = problem?.detail;
  const title = problem?.title;
  const msg = (body as Record<string, unknown> | null)?.message;
  const message =
    (typeof detail === 'string' && detail) ||
    (typeof title === 'string' && title) ||
    (typeof msg === 'string' && msg) ||
    `HTTP ${status}`;
  return { message, problem };
}

/** Options for {@link fetchApi}; extends {@link RequestInit} with auth flags. */
export interface FetchOptions extends RequestInit {
  /** When false, the Authorization header is omitted (used for login calls). */
  requireAuth?: boolean;
  /**
   * When set, sends this value as Bearer token (e.g. recovery token for enrollment reset).
   * Takes precedence over session token when {@link requireAuth} is true.
   */
  bearerToken?: string;
}

export async function fetchApi<T>(path: string, options: FetchOptions = {}): Promise<T> {
  const { requireAuth = true, bearerToken, headers: extraHeaders, ...init } = options;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(extraHeaders as Record<string, string> | undefined),
  };

  if (bearerToken) {
    headers['Authorization'] = `Bearer ${bearerToken}`;
  } else if (requireAuth) {
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const credentials: RequestCredentials | undefined = isBrowserSessionCookieBuild()
    ? 'include'
    : undefined;

  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: init.credentials ?? credentials,
  });

  const contentType = response.headers.get('content-type') ?? '';
  const isJson =
    contentType.includes('application/json') || contentType.includes('application/problem+json');

  if (!response.ok) {
    let body: unknown = null;
    if (isJson) {
      body = await response.json().catch(() => null);
    } else {
      // Some proxies or gateways strip Content-Type; try to parse as JSON for error payloads.
      const text = await response.text();
      if (text) {
        try {
          body = JSON.parse(text) as unknown;
        } catch {
          body = null;
        }
      }
    }

    if (response.status === 401) {
      if (bearerToken) {
        const { message, problem } = messageFromProblemBody(401, body);
        throw new ApiError(401, body, message, problem);
      }
      /** Session-scoped request (not login/public, not recovery bearer) — 401 always sends user to login. */
      if (requireAuth) {
        clearSession();
        window.location.replace('/login');
        throw new ApiError(
          401,
          body,
          'Session expired. Please log in again.',
          isProblemDetail(body) ? (body as ProblemDetail) : null,
        );
      }
    }

    const { message, problem } = messageFromProblemBody(response.status, body);
    throw new ApiError(response.status, body, message, problem);
  }

  if (response.status === 204 || !isJson) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

/**
 * Fetches a binary resource (e.g. QR code PNG) that requires authentication and
 * returns a temporary object URL for use with <img src={...}>.
 * Always call URL.revokeObjectURL() in a useEffect cleanup to avoid memory leaks.
 */
export async function fetchBlobUrl(path: string): Promise<string> {
  const token = getToken();
  const credentials: RequestCredentials | undefined = isBrowserSessionCookieBuild()
    ? 'include'
    : undefined;
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    credentials,
  });
  if (response.status === 401) {
    clearSession();
    window.location.replace('/login');
    throw new ApiError(401, null, 'Session expired. Please log in again.', null);
  }
  if (!response.ok) throw new ApiError(response.status, null, `HTTP ${response.status}`);
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

/** Typed API methods — always prefer these over raw fetch in components. */
export const api = {
  get: <T>(path: string): Promise<T> =>
    fetchApi<T>(path, { method: 'GET' }),

  /** GET without Bearer (public endpoints, e.g. instance info on login page). */
  getPublic: <T>(path: string): Promise<T> =>
    fetchApi<T>(path, { method: 'GET', requireAuth: false }),

  post: <T>(path: string, body: unknown, requireAuth = true): Promise<T> =>
    fetchApi<T>(path, { method: 'POST', body: JSON.stringify(body), requireAuth }),

  put: <T>(path: string, body: unknown): Promise<T> =>
    fetchApi<T>(path, { method: 'PUT', body: JSON.stringify(body) }),

  patch: <T>(path: string, body: unknown): Promise<T> =>
    fetchApi<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),

  delete: <T = void>(path: string): Promise<T> =>
    fetchApi<T>(path, { method: 'DELETE' }),
};

/**
 * Returns a user-facing error message for API failures.
 * Use when displaying mutation/query errors so that RFC 9457 detail/title is shown instead of "HTTP 403".
 *
 * @param error - Caught error (e.g. mutation.error)
 * @param fallback - Message when error is not an ApiError
 * @returns Best available message (problem detail, then title, then fallback)
 */
export function getApiErrorMessage(error: unknown, fallback = 'An error occurred.'): string {
  if (error instanceof ApiError) {
    if (error.problemDetail?.detail) return error.problemDetail.detail;
    if (error.problemDetail?.title) return error.problemDetail.title;
    return error.message;
  }
  return fallback;
}
