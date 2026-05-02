import { createContext } from 'react';
import type { AuthSession } from '@/lib/auth';

export interface AuthContextValue {
  session: AuthSession | null;
  isAuthenticated: boolean;
  isSessionChecking: boolean;
  login: (session: AuthSession) => void;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
