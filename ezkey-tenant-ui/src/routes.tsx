import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/auth-context';
import { AppShell } from '@/components/layout/app-shell';
import type { ReactNode } from 'react';

const LoginPage = lazy(() => import('@/pages/login'));
const DashboardPage = lazy(() => import('@/pages/dashboard'));
const IntegrationsPage = lazy(() => import('@/pages/integrations'));
const IntegrationDetailPage = lazy(() => import('@/pages/integration-detail'));
const EnrollmentsPage = lazy(() => import('@/pages/enrollments'));
const EnrollmentDetailPage = lazy(() => import('@/pages/enrollment-detail'));
const NotFoundPage = lazy(() => import('@/pages/not-found'));

// ── Guards ───────────────────────────────────────────────────────────────────

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

// ── Loading states ───────────────────────────────────────────────────────────

function PageLoader() {
  return (
    <div className="flex items-center justify-center h-64">
      <span className="size-6 border-2 border-fg border-t-transparent rounded-full animate-spin" />
    </div>
  );
}

function SuspensePage({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

// ── Placeholder pages — replaced in Phase 3 ──────────────────────────────────

function PlaceholderPage({ title }: { title: string }) {
  return (
    <AppShell title={title}>
      <div className="flex items-center justify-center h-64 text-center">
        <div>
          <p className="font-black text-fg/10" style={{ fontSize: '4rem', lineHeight: 1 }}>
            {title.toUpperCase()}
          </p>
          <p className="text-fg-muted mt-4 text-sm">This screen will be implemented in Phase 3.</p>
        </div>
      </div>
    </AppShell>
  );
}

// ── Router ────────────────────────────────────────────────────────────────────

export const router = createBrowserRouter([
  // Public
  {
    path: '/login',
    element: (
      <SuspensePage>
        <LoginPage />
      </SuspensePage>
    ),
  },

  // Protected
  {
    path: '/dashboard',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <DashboardPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/integrations',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <IntegrationsPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/integrations/:id',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <IntegrationDetailPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/enrollments',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <EnrollmentsPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/enrollments/:id',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <EnrollmentDetailPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/auth-attempts',
    element: (
      <ProtectedRoute>
        <PlaceholderPage title="Auth Attempts" />
      </ProtectedRoute>
    ),
  },
  {
    path: '/audit-logs',
    element: (
      <ProtectedRoute>
        <PlaceholderPage title="Audit Logs" />
      </ProtectedRoute>
    ),
  },
  {
    path: '/admins',
    element: (
      <ProtectedRoute>
        <PlaceholderPage title="Admins" />
      </ProtectedRoute>
    ),
  },
  {
    path: '/api-keys',
    element: (
      <ProtectedRoute>
        <PlaceholderPage title="API Keys" />
      </ProtectedRoute>
    ),
  },

  // Redirects
  { path: '/', element: <Navigate to="/dashboard" replace /> },
  {
    path: '*',
    element: (
      <SuspensePage>
        <NotFoundPage />
      </SuspensePage>
    ),
  },
]);
