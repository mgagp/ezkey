import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/context/auth-context';
import { HelpProvider } from '@/context/help-context';
import { RouteErrorBoundary } from '@/components/route-error-boundary';
import type { ReactNode } from 'react';

const LoginPage = lazy(() => import('@/pages/login'));
const DashboardPage = lazy(() => import('@/pages/dashboard'));
const TenantsPage = lazy(() => import('@/pages/tenants'));
const TenantDetailPage = lazy(() => import('@/pages/tenant-detail'));
const IntegrationsPage = lazy(() => import('@/pages/integrations'));
const IntegrationDetailPage = lazy(() => import('@/pages/integration-detail'));
const EnrollmentsPage = lazy(() => import('@/pages/enrollments'));
const EnrollmentDetailPage = lazy(() => import('@/pages/enrollment-detail'));
const AdminsPage = lazy(() => import('@/pages/admins'));
const AuthAttemptsPage = lazy(() => import('@/pages/auth-attempts'));
const AuditLogsPage = lazy(() => import('@/pages/audit-logs'));
const AlertsPage = lazy(() => import('@/pages/alerts'));
const AlertDetailPage = lazy(() => import('@/pages/alert-detail'));
const ApiKeysPage = lazy(() => import('@/pages/api-keys'));
const ApiKeyDetailPage = lazy(() => import('@/pages/api-key-detail'));
const EncryptionKeysPage = lazy(() => import('@/pages/encryption-keys'));
const NotFoundPage = lazy(() => import('@/pages/not-found'));

// ── Guards ───────────────────────────────────────────────────────────────────

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isSessionChecking } = useAuth();
  if (isSessionChecking) return <PageLoader />;
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

function RootLayout() {
  return (
    <HelpProvider>
      <Outlet />
    </HelpProvider>
  );
}

// ── Router ────────────────────────────────────────────────────────────────────

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    errorElement: <RouteErrorBoundary />,
    children: [
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
    path: '/tenants',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <TenantsPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/tenants/:id',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <TenantDetailPage />
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
        <SuspensePage>
          <AuthAttemptsPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/audit-logs',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <AuditLogsPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/alerts',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <AlertsPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/alerts/:alertId',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <AlertDetailPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/admins',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <AdminsPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/api-keys',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <ApiKeysPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/api-keys/:id',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <ApiKeyDetailPage />
        </SuspensePage>
      </ProtectedRoute>
    ),
  },
  {
    path: '/encryption-keys',
    element: (
      <ProtectedRoute>
        <SuspensePage>
          <EncryptionKeysPage />
        </SuspensePage>
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
    ],
  },
]);
