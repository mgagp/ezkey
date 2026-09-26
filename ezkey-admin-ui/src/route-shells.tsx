import { Suspense } from 'react';
import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { HelpProvider } from '@/context/help-context';
import { useAuth } from '@/context/use-auth';
import {
  isBootstrapAllowedPath,
  isBootstrapConsoleRestricted,
} from '@/lib/evaluator-bootstrap-session';

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isSessionChecking, session } = useAuth();
  const { pathname } = useLocation();
  if (isSessionChecking) return <PageLoader />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (isBootstrapConsoleRestricted(session) && !isBootstrapAllowedPath(pathname)) {
    return <Navigate to="/evaluator-onboarding" replace />;
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
