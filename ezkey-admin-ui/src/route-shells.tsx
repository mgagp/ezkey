import { Suspense } from 'react';
import type { ReactNode } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { HelpProvider } from '@/context/help-context';
import { useAuth } from '@/context/use-auth';
import { isEvaluatorTempSession } from '@/lib/auth';

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isSessionChecking } = useAuth();
  if (isSessionChecking) return <PageLoader />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

/**
 * GLOBAL-only routes (Integrité, Tenants, Alerts, Encryption keys). Soft-redirects
 * tenant / temporary evaluator sessions away from deny-listed deep links.
 */
export function GlobalAdminRoute({ children }: { children: ReactNode }) {
  const { session, isAuthenticated, isSessionChecking } = useAuth();
  if (isSessionChecking) return <PageLoader />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (session?.adminType !== 'GLOBAL_ADMIN' || isEvaluatorTempSession(session)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

export function PageLoader() {
  return (
    <div className="flex items-center justify-center h-64">
      <span className="size-6 border-2 border-fg border-t-transparent rounded-full animate-spin" />
    </div>
  );
}

export function SuspensePage({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

export function RootLayout() {
  return (
    <HelpProvider>
      <Outlet />
    </HelpProvider>
  );
}
