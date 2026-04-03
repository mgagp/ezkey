/**
 * Stable identifiers for contextual help topics. Each maps to copy under the `help` i18n namespace
 * (`help:topics.<id>.*`). Adding backend-served help later should preserve these IDs as the contract.
 */
export type HelpTopicId =
  | 'default'
  | 'login'
  | 'dashboard'
  | 'integrations'
  | 'integration-detail'
  | 'encryption-keys'
  | 'audit-logs';

/**
 * Reusable cross-screen help patterns. These are not route-derived; they can be
 * opened from shared UI affordances wherever the pattern appears.
 */
export type HelpPatternId = 'fkRelatedDetails';

/**
 * Resolves the active help topic from the current pathname. Extend as new screens receive authored help.
 */
export function resolveHelpTopicId(pathname: string): HelpTopicId {
  const path = pathname.split('?')[0] ?? pathname;
  if (path === '/login') return 'login';
  if (path === '/dashboard' || path === '/') return 'dashboard';
  if (path === '/integrations') return 'integrations';
  if (path.startsWith('/integrations/')) return 'integration-detail';
  if (path === '/encryption-keys') return 'encryption-keys';
  if (path === '/audit-logs') return 'audit-logs';
  return 'default';
}
