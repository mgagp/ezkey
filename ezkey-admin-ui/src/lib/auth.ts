/** Auth session stored in sessionStorage — cleared when the tab is closed. */
export interface AuthSession {
  token: string;
  username: string;
  adminType: string;
  /** ISO-8601 — token expiration from the Admin API. */
  expiresAt: string;
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

/** Convenience accessor for the bearer token. */
export function getToken(): string | null {
  return getSession()?.token ?? null;
}
