import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  type AuthSession,
  clearSession,
  getSession,
  isBrowserSessionCookieBuild,
  saveSession,
} from '@/lib/auth';
import { fetchApi } from '@/lib/api-client';
import { queryClient } from '@/lib/query-client';
import { AuthContext } from '@/context/auth-context-value';

/** Provides auth state to the component tree. Wrap at the app root. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => getSession());
  const [isSessionChecking, setIsSessionChecking] = useState(() => isBrowserSessionCookieBuild());

  useEffect(() => {
    if (!isBrowserSessionCookieBuild()) {
      setIsSessionChecking(false);
      return;
    }

    let cancelled = false;
    const restoreSession = async () => {
      try {
        const restored = await fetchApi<AuthSession>('/api/v1/admin/auth/me', {
          method: 'GET',
          requireAuth: false,
        });
        if (cancelled) return;
        saveSession(restored);
        setSession(restored);
      } catch {
        if (cancelled) return;
        clearSession();
        queryClient.clear();
        setSession(null);
      } finally {
        if (!cancelled) {
          setIsSessionChecking(false);
        }
      }
    };

    void restoreSession();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback((newSession: AuthSession) => {
    saveSession(newSession);
    setSession(newSession);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    queryClient.clear();
    setSession(null);
  }, []);

  const value = useMemo(
    () => ({
      session,
      isAuthenticated:
        session !== null &&
        (isBrowserSessionCookieBuild() || Boolean(session.token && session.token.length > 0)),
      isSessionChecking,
      login,
      logout,
    }),
    [session, isSessionChecking, login, logout],
  );

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

