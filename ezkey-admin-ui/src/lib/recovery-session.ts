/**
 * Recovery flow state (separate from {@link ./auth} full admin session).
 *
 * Product contract: recovery is a limited funnel, not a full admin login. The recovery token is
 * only for POST /api/v1/admin/enrollments/reset; normal login uses the passwordless bearer token.
 */

export interface RecoverySession {
  recoveryToken: string;
  expiresAt: string;
  username: string;
  codesRemaining: number;
  /** Enrollment ID for POST /api/v1/admin/enrollments/reset */
  enrollmentId: number;
}

const RECOVERY_KEY = 'ezkey_admin_recovery';

export function saveRecoverySession(session: RecoverySession): void {
  sessionStorage.setItem(RECOVERY_KEY, JSON.stringify(session));
}

export function getRecoverySession(): RecoverySession | null {
  try {
    const raw = sessionStorage.getItem(RECOVERY_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as RecoverySession;
    if (
      typeof session.recoveryToken !== 'string' ||
      typeof session.expiresAt !== 'string' ||
      typeof session.username !== 'string' ||
      typeof session.enrollmentId !== 'number'
    ) {
      clearRecoverySession();
      return null;
    }
    if (new Date(session.expiresAt) <= new Date()) {
      clearRecoverySession();
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function clearRecoverySession(): void {
  sessionStorage.removeItem(RECOVERY_KEY);
}
