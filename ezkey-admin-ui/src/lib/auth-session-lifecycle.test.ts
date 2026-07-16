import { beforeEach, describe, expect, it, vi } from 'vitest';

const clearSession = vi.fn();
const clearRecoverySession = vi.fn();
const clearIntegrityInvestigationSession = vi.fn();

vi.mock('@/lib/auth', () => ({
  clearSession: () => clearSession(),
}));

vi.mock('@/lib/recovery-session', () => ({
  clearRecoverySession: () => clearRecoverySession(),
}));

vi.mock('@/lib/integrity-investigation-session', () => ({
  clearIntegrityInvestigationSession: () => clearIntegrityInvestigationSession(),
}));

import {
  clearLocalAuthOnLogout,
  clearLocalAuthOnSessionInvalidation,
  clearRecoveryOnSuccessfulLogin,
  mayCompleteLogoutLocallyAfterApiFailure,
} from '@/lib/auth-session-lifecycle';

describe('auth-session-lifecycle (SEC-026 / SEC-027)', () => {
  beforeEach(() => {
    clearSession.mockReset();
    clearRecoverySession.mockReset();
    clearIntegrityInvestigationSession.mockReset();
  });

  it('clears recovery on successful normal login', () => {
    clearRecoveryOnSuccessfulLogin();
    expect(clearRecoverySession).toHaveBeenCalledOnce();
    expect(clearSession).not.toHaveBeenCalled();
  });

  it('clears session, recovery, and integrity investigation on logout', () => {
    clearLocalAuthOnLogout();
    expect(clearSession).toHaveBeenCalledOnce();
    expect(clearRecoverySession).toHaveBeenCalledOnce();
    expect(clearIntegrityInvestigationSession).toHaveBeenCalledOnce();
  });

  it('clears session and recovery on session invalidation', () => {
    clearLocalAuthOnSessionInvalidation();
    expect(clearSession).toHaveBeenCalledOnce();
    expect(clearRecoverySession).toHaveBeenCalledOnce();
    expect(clearIntegrityInvestigationSession).not.toHaveBeenCalled();
  });

  it('blocks local logout completion after API failure in cookie mode (SEC-027)', () => {
    expect(mayCompleteLogoutLocallyAfterApiFailure(true)).toBe(false);
    expect(mayCompleteLogoutLocallyAfterApiFailure(false)).toBe(true);
  });
});
