import { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { RouteErrorBoundary } from '@/components/route-error-boundary';
import { GlobalAdminRoute, ProtectedRoute, RootLayout, SuspensePage } from '@/route-shells';

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
const IntegrityPage = lazy(() => import('@/pages/integrity'));
const AlertsPage = lazy(() => import('@/pages/alerts'));
const AlertDetailPage = lazy(() => import('@/pages/alert-detail'));
const ApiKeysPage = lazy(() => import('@/pages/api-keys'));
const ApiKeyDetailPage = lazy(() => import('@/pages/api-key-detail'));
const EncryptionKeysPage = lazy(() => import('@/pages/encryption-keys'));
const NotFoundPage = lazy(() => import('@/pages/not-found'));

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
      <GlobalAdminRoute>
        <SuspensePage>
          <TenantsPage />
        </SuspensePage>
      </GlobalAdminRoute>
    ),
  },
  {
    path: '/tenants/:id',
    element: (
      <GlobalAdminRoute>
        <SuspensePage>
          <TenantDetailPage />
        </SuspensePage>
      </GlobalAdminRoute>
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
    path: '/integrity',
    element: (
      <GlobalAdminRoute>
        <SuspensePage>
          <IntegrityPage />
        </SuspensePage>
      </GlobalAdminRoute>
    ),
  },
  {
    path: '/alerts',
    element: (
      <GlobalAdminRoute>
        <SuspensePage>
          <AlertsPage />
        </SuspensePage>
      </GlobalAdminRoute>
    ),
  },
  {
    path: '/alerts/:alertId',
    element: (
      <GlobalAdminRoute>
        <SuspensePage>
          <AlertDetailPage />
        </SuspensePage>
      </GlobalAdminRoute>
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
      <GlobalAdminRoute>
        <SuspensePage>
          <EncryptionKeysPage />
        </SuspensePage>
      </GlobalAdminRoute>
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
