/**
 * Optional "remember username" preference for the login screen.
 * Stored in localStorage — identifier only; bearer tokens stay in sessionStorage (see auth.ts).
 */
const STORAGE_KEY = 'ezkey_admin_username_pref';

export interface UsernamePref {
  rememberUsername: boolean;
  username: string;
}

function isValidPref(raw: unknown): raw is UsernamePref {
  if (raw === null || typeof raw !== 'object') return false;
  const o = raw as Record<string, unknown>;
  return (
    o.rememberUsername === true &&
    typeof o.username === 'string' &&
    o.username.length >= 3 &&
    o.username.length <= 50
  );
}

/** Read saved preference; returns null if absent or invalid. */
export function readUsernamePref(): UsernamePref | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!isValidPref(parsed)) {
      window.localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

/**
 * Persist username for next visit when the user opts in; removes storage when opted out.
 * Call after login request succeeds (transition to waiting), not before.
 */
export function persistUsernamePref(username: string, rememberUsername: boolean): void {
  const trimmed = username.trim();
  if (!rememberUsername) {
    window.localStorage.removeItem(STORAGE_KEY);
    return;
  }
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ rememberUsername: true, username: trimmed } satisfies UsernamePref),
  );
}
