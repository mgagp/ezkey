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
}

const AUTH_KEY = 'ezkey_admin_auth';

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
