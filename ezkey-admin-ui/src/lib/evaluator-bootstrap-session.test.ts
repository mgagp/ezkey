import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  establishBootstrapAuthSession,
  isBootstrapAllowedPath,
  isBootstrapConsoleRestricted,
  isBootstrapSession,
  markBootstrapSessionExpired,
  releaseBootstrapSessionAfterBind,
  storeEvaluatorBootstrapHandoff,
  takeBootstrapSessionExpiredFlag,
  takeEvaluatorBootstrapHandoff,
} from '@/lib/evaluator-bootstrap-session';
import { clearSession, getSession } from '@/lib/auth';

function installMemorySessionStorage(): void {
  const store = new Map<string, string>();
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => {
      store.clear();
    },
    key: () => null,
    get length() {
      return store.size;
    },
  });
}

describe('evaluator-bootstrap-session', () => {
  beforeEach(() => {
    installMemorySessionStorage();
    clearSession();
  });

  it('stores and consumes handoff once', () => {
    storeEvaluatorBootstrapHandoff({
      sessionToken: 'ezkey_bootstrap_x',
      sessionExpiresAt: new Date(Date.now() + 3600_000).toISOString(),
      username: 'eval-admin-x',
      activationCode: 'ezkey_activation_abc',
    });
    const first = takeEvaluatorBootstrapHandoff();
    expect(first?.username).toBe('eval-admin-x');
    expect(takeEvaluatorBootstrapHandoff()).toBeNull();
  });

  it('establishes bootstrap AuthSession and marks expiry flag', () => {
    const expiresAt = new Date(Date.now() + 8 * 3600_000).toISOString();
    const session = establishBootstrapAuthSession({
      sessionToken: 'ezkey_bootstrap_plain',
      sessionExpiresAt: expiresAt,
      username: 'eval-admin-y',
    });
    expect(isBootstrapSession(session)).toBe(true);
    expect(getSession()?.tokenPurpose).toBe('BOOTSTRAP');
    markBootstrapSessionExpired();
    expect(takeBootstrapSessionExpiredFlag()).toBe(true);
    expect(takeBootstrapSessionExpiredFlag()).toBe(false);
  });

  it('restricts console paths to onboarding while BOOTSTRAP', () => {
    expect(
      isBootstrapConsoleRestricted({
        username: 'u',
        adminType: 'TENANT_ADMIN',
        expiresAt: new Date().toISOString(),
        tokenPurpose: 'BOOTSTRAP',
      }),
    ).toBe(true);
    expect(
      isBootstrapConsoleRestricted({
        username: 'u',
        adminType: 'TENANT_ADMIN',
        expiresAt: new Date().toISOString(),
        tokenPurpose: 'SESSION',
      }),
    ).toBe(false);
    expect(isBootstrapAllowedPath('/evaluator-onboarding')).toBe(true);
    expect(isBootstrapAllowedPath('/dashboard')).toBe(false);
    expect(isBootstrapAllowedPath('/tenants')).toBe(false);
  });

  it('releaseBootstrapSessionAfterBind clears local session without expiry flag', async () => {
    const clearLocalSession = vi.fn();
    const ok = await releaseBootstrapSessionAfterBind({
      callLogout: async () => undefined,
      clearLocalSession,
      mayCompleteLocallyAfterApiFailure: false,
    });
    expect(ok).toBe(true);
    expect(clearLocalSession).toHaveBeenCalledOnce();
    // Successful bind handoff must not arm the "session expired / resume" login hint.
    expect(takeBootstrapSessionExpiredFlag()).toBe(false);
  });

  it('releaseBootstrapSessionAfterBind fails closed in cookie mode when logout API fails', async () => {
    const clearLocalSession = vi.fn();
    const ok = await releaseBootstrapSessionAfterBind({
      callLogout: async () => {
        throw new Error('network');
      },
      clearLocalSession,
      mayCompleteLocallyAfterApiFailure: false,
    });
    expect(ok).toBe(false);
    expect(clearLocalSession).not.toHaveBeenCalled();
  });

  it('releaseBootstrapSessionAfterBind allows Mode A local wipe after logout API failure', async () => {
    const clearLocalSession = vi.fn();
    const ok = await releaseBootstrapSessionAfterBind({
      callLogout: async () => {
        throw new Error('network');
      },
      clearLocalSession,
      mayCompleteLocallyAfterApiFailure: true,
    });
    expect(ok).toBe(true);
    expect(clearLocalSession).toHaveBeenCalledOnce();
  });
});
