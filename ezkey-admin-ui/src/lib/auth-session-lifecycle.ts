/**
 * Client-side auth residue cleanup at session boundaries (SEC-026).
 *
 * Recovery funnel state must not survive a normal admin login, explicit logout, or
 * session invalidation — otherwise a later user on the same tab can resume recovery.
 */

import { clearSession } from '@/lib/auth';
import { clearIntegrityInvestigationSession } from '@/lib/integrity-investigation-session';
import { clearRecoverySession } from '@/lib/recovery-session';

/** Drop recovery state when a normal admin session starts. */
export function clearRecoveryOnSuccessfulLogin(): void {
  clearRecoverySession();
}

/**
 * Clear all local auth residue after an explicit logout whose server step already succeeded
 * (or Mode A best-effort local wipe).
 */
export function clearLocalAuthOnLogout(): void {
  clearSession();
  clearRecoverySession();
  clearIntegrityInvestigationSession();
}

/** Clear local auth + recovery when the server rejects the session (401). */
export function clearLocalAuthOnSessionInvalidation(): void {
  clearSession();
  clearRecoverySession();
}

/**
 * Whether logout may finish locally after the logout API failed.
 *
 * Cookie mode cannot delete the HttpOnly session cookie from JavaScript — failing closed
 * keeps the operator on an authenticated shell so they can retry (SEC-027).
 * Mode A stores the bearer in sessionStorage, so local wipe still ends the usable session.
 */
export function mayCompleteLogoutLocallyAfterApiFailure(cookieMode: boolean): boolean {
  return !cookieMode;
}
