import { useContext } from 'react';
import { AuthContext } from '@/context/auth-context-value';
import type { AuthContextValue } from '@/context/auth-context-value';

/** Access auth state and actions. Must be used inside AuthProvider. */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
