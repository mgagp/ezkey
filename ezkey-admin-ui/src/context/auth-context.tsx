import { createContext, useCallback, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import {
  type AuthSession,
  clearSession,
  getSession,
  isBrowserSessionCookieBuild,
  saveSession,
} from '@/lib/auth';
import { queryClient } from '@/lib/query-client';

interface AuthContextValue {
  session: AuthSession | null;
  isAuthenticated: boolean;
  login: (session: AuthSession) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Provides auth state to the component tree. Wrap at the app root. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => getSession());

  const login = useCallback((newSession: AuthSession) => {
    saveSession(newSession);
    setSession(newSession);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    queryClient.clear();
    setSession(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        session,
        isAuthenticated:
          session !== null &&
          (isBrowserSessionCookieBuild() || Boolean(session.token && session.token.length > 0)),
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/** Access auth state and actions. Must be used inside AuthProvider. */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
