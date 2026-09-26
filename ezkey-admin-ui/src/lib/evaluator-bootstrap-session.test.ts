import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  establishBootstrapAuthSession,
  isBootstrapSession,
  markBootstrapSessionExpired,
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
});
