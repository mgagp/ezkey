/**
 * When true (split HTTPS UI/API build), the API stores the opaque token in an HttpOnly cookie;
 * this app keeps only non-secret session metadata in sessionStorage.
 */
export function isBrowserSessionCookieBuild(): boolean {
  const v = import.meta.env.VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE;
  if (v == null || v === '') return false;
  return String(v).toLowerCase() === 'true';
}

/** Auth session stored in sessionStorage — cleared when the tab is closed. */
export interface AuthSession {
  /**
   * Opaque bearer token; omitted when {@link isBrowserSessionCookieBuild} is true (cookie holds
   * the secret).
   */
  token?: string;
  username: string;
  adminType: string;
  /** ISO-8601 — token expiration from the Admin API. */
  expiresAt: string;
  /** Administrator ID from login response (for tenant-scoped display preferences). */
  adminId?: number;
  /**
   * Tenant scope when the administrator is tenant- or integration-scoped; omitted for global
   * administrators and legacy sessions saved before this field existed.
   */
  tenantId?: number | null;
  /**
   * Tenant display name when scoped; omitted for global administrators and legacy sessions.
   */
  tenantName?: string | null;
  /** Non-secret CSRF token used only by cookie-authenticated browser sessions. */
  csrfToken?: string;
  /**
   * Session purpose from the Admin API. {@code EVALUATOR_TEMP} is a one-shot temporary evaluator
   * console foothold; omit or {@code SESSION} for normal passwordless login.
   */
  tokenPurpose?: string;
}

/**
 * Whether the session is a one-shot temporary evaluator console ({@code EVALUATOR_TEMP}).
 *
 * @param session current auth session, or null/undefined when logged out
 */
export function isEvaluatorTempSession(
  session: Pick<AuthSession, 'tokenPurpose'> | null | undefined,
): boolean {
  return session?.tokenPurpose === 'EVALUATOR_TEMP';
}

const AUTH_KEY = 'ezkey_admin_auth';
const CSRF_COOKIE_NAME =
  (import.meta.env.VITE_ADMIN_AUTH_CSRF_COOKIE_NAME as string | undefined) ?? 'EZKEY_ADMIN_CSRF';
const CSRF_HEADER_NAME =
  (import.meta.env.VITE_ADMIN_AUTH_CSRF_HEADER_NAME as string | undefined) ?? 'X-CSRF-TOKEN';

/** HTTP methods that can change server state and therefore need CSRF in cookie mode. */
export function isUnsafeHttpMethod(method: string | undefined): boolean {
  const normalized = (method ?? 'GET').toUpperCase();
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(normalized);
}

/** Browser credential mode for the current build. */
export function getBrowserCredentials(): RequestCredentials | undefined {
  return isBrowserSessionCookieBuild() ? 'include' : undefined;
}

/** Header name expected by the Admin API for cookie-mode CSRF validation. */
export function getCsrfHeaderName(): string {
  return CSRF_HEADER_NAME;
}

/** Retrieve the current session, validating expiration. Returns null if absent or expired. */
export function getSession(): AuthSession | null {
  try {
    const raw = sessionStorage.getItem(AUTH_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as AuthSession;
    if (new Date(session.expiresAt) <= new Date()) {
      clearSession();
      return null;
    }
    if (!isBrowserSessionCookieBuild() && (!session.token || session.token === '')) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

/** Persist the session after a successful login. */
export function saveSession(session: AuthSession): void {
  sessionStorage.setItem(AUTH_KEY, JSON.stringify(session));
}

/** Clear the session on logout or 401. */
export function clearSession(): void {
  sessionStorage.removeItem(AUTH_KEY);
}

/** Convenience accessor for the bearer token (empty in cookie-build mode). */
export function getToken(): string | null {
  return getSession()?.token ?? null;
}

/** Returns the non-secret CSRF token from session metadata or the readable CSRF cookie. */
export function getCsrfToken(): string | null {
  const sessionToken = getSession()?.csrfToken;
  if (sessionToken) {
    return sessionToken;
  }
  if (typeof document === 'undefined') {
    return null;
  }
  const prefix = `${CSRF_COOKIE_NAME}=`;
  const cookie = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}