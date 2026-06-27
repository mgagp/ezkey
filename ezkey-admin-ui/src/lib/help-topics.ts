/**
 * Stable identifiers for contextual help topics. Each maps to copy under the `help` i18n namespace
 * (`help:topics.<id>.*`). Adding backend-served help later should preserve these IDs as the contract.
 */
export type HelpTopicId =
  | 'default'
  | 'login'
  | 'dashboard'
  | 'tenants'
  | 'tenant-detail'
  | 'integrations'
  | 'integration-detail'
  | 'enrollments'
  | 'enrollment-detail'
  | 'api-keys'
  | 'api-key-detail'
  | 'admins'
  | 'encryption-keys'
  | 'audit-logs';

/**
 * Resolves the active help topic from the current pathname. Extend as new screens receive authored help.
 */
export function resolveHelpTopicId(pathname: string): HelpTopicId {
  const path = pathname.split('?')[0] ?? pathname;
  if (path === '/login') return 'login';
  if (path === '/dashboard' || path === '/') return 'dashboard';
  if (path === '/tenants') return 'tenants';
  if (path.startsWith('/tenants/')) return 'tenant-detail';
  if (path === '/integrations') return 'integrations';
  if (path.startsWith('/integrations/')) return 'integration-detail';
  if (path === '/enrollments') return 'enrollments';
  if (path.startsWith('/enrollments/')) return 'enrollment-detail';
  if (path === '/api-keys') return 'api-keys';
  if (path.startsWith('/api-keys/')) return 'api-key-detail';
  if (path === '/admins') return 'admins';
  if (path === '/encryption-keys') return 'encryption-keys';
  if (path === '/audit-logs') return 'audit-logs';
  return 'default';
}
