/**
 * Evaluator BOOTSTRAP session handoff (V-2026-09-26).
 *
 * Cross-origin signup should not put the session token in a URL query string. Callers store a
 * short-lived handoff payload in sessionStorage (same tab) or rely on Mode B HttpOnly cookies,
 * then navigate to `/evaluator-bootstrap`.
 */

import {
  isBrowserSessionCookieBuild,
  saveSession,
  type AuthSession,
} from '@/lib/auth';

const HANDOFF_KEY = 'ezkey_evaluator_bootstrap_handoff';
const EXPIRED_FLAG_KEY = 'ezkey_bootstrap_session_expired';

export interface EvaluatorBootstrapHandoff {
  /** Opaque BOOTSTRAP token (Mode A only). */
  sessionToken?: string;
  sessionExpiresAt: string;
  username: string;
  /** Optional activation code for onboarding prefill — not the session secret. */
  activationCode?: string;
  /** Admin id for fetching QR via authenticated onboarding after resume. */
  adminId?: number;
  adminType?: string;
}

/** Persist handoff before navigating to `/evaluator-bootstrap` (never put token in the URL). */
export function storeEvaluatorBootstrapHandoff(handoff: EvaluatorBootstrapHandoff): void {
  sessionStorage.setItem(HANDOFF_KEY, JSON.stringify(handoff));
}

/** Read and clear handoff (one-shot). */
export function takeEvaluatorBootstrapHandoff(): EvaluatorBootstrapHandoff | null {
  try {
    const raw = sessionStorage.getItem(HANDOFF_KEY);
    if (!raw) return null;
    sessionStorage.removeItem(HANDOFF_KEY);
    return JSON.parse(raw) as EvaluatorBootstrapHandoff;
  } catch {
    sessionStorage.removeItem(HANDOFF_KEY);
    return null;
  }
}

/** Mark that a BOOTSTRAP session ended so `/login` can show one clear sentence. */
export function markBootstrapSessionExpired(): void {
  sessionStorage.setItem(EXPIRED_FLAG_KEY, '1');
}

/** Consume the expiry message flag (one-shot). */
export function takeBootstrapSessionExpiredFlag(): boolean {
  const present = sessionStorage.getItem(EXPIRED_FLAG_KEY) === '1';
  if (present) {
    sessionStorage.removeItem(EXPIRED_FLAG_KEY);
  }
  return present;
}

/**
 * Establish a Mode A AuthSession from signup JSON fields.
 * Mode B builds omit the token (cookie holds the secret).
 */
export function establishBootstrapAuthSession(input: {
  sessionToken?: string | null;
  sessionExpiresAt: string;
  username: string;
  adminType?: string;
  adminId?: number;
  tenantId?: number | null;
  tenantName?: string | null;
  csrfToken?: string;
  lifecycleStatus?: string;
}): AuthSession {
  const session: AuthSession = {
    username: input.username,
    adminType: input.adminType ?? 'TENANT_ADMIN',
    expiresAt: input.sessionExpiresAt,
    adminId: input.adminId,
    tenantId: input.tenantId,
    tenantName: input.tenantName,
    csrfToken: input.csrfToken,
    tokenPurpose: 'BOOTSTRAP',
    lifecycleStatus: input.lifecycleStatus ?? 'PENDING_ACTIVATION',
  };
  if (!isBrowserSessionCookieBuild() && input.sessionToken) {
    session.token = input.sessionToken;
  }
  saveSession(session);
  return session;
}

export function isBootstrapSession(session: AuthSession | null | undefined): boolean {
  return session?.tokenPurpose === 'BOOTSTRAP';
}

/**
 * BOOTSTRAP sessions must stay on the narrow onboarding surface — full console routes would
 * 403 against the Admin API allowlist.
 */
export function isBootstrapConsoleRestricted(
  session: AuthSession | null | undefined,
): boolean {
  return isBootstrapSession(session);
}

/** Paths allowed while a BOOTSTRAP session is active (relative to the SPA). */
export const BOOTSTRAP_ALLOWED_PATHS = ['/evaluator-onboarding'] as const;

export function isBootstrapAllowedPath(pathname: string): boolean {
  return BOOTSTRAP_ALLOWED_PATHS.some(
    (path) => pathname === path || pathname.startsWith(`${path}/`),
  );
}
