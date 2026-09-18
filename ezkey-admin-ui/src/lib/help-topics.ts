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
  | 'auth-attempts'
  | 'api-keys'
  | 'api-key-detail'
  | 'admins'
  | 'encryption-keys'
  | 'audit-logs'
  | 'integrity'
  | 'alerts'
  | 'alert-detail';

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
  if (path === '/auth-attempts') return 'auth-attempts';
  if (path === '/api-keys') return 'api-keys';
  if (path.startsWith('/api-keys/')) return 'api-key-detail';
  if (path === '/admins') return 'admins';
  if (path === '/encryption-keys') return 'encryption-keys';
  if (path === '/audit-logs') return 'audit-logs';
  if (path === '/integrity') return 'integrity';
  if (path === '/alerts') return 'alerts';
  if (path.startsWith('/alerts/')) return 'alert-detail';
  return 'default';
}

/**
 * One extra help-drawer section beyond the standard `summary` / `body` pair, rendered as its own
 * divider block under `topics.<topicId>.<key>` in the `help` i18n namespace.
 */
export interface HelpExtraSection {
  /** i18n key under the topic (rendered as `topics.<topicId>.<key>`). */
  key: string;
  /** Restricts rendering to one admin type. Omit to show the section to both roles. */
  audience?: 'global' | 'tenant';
  /** Muted (secondary) text tone. Defaults to regular foreground text. */
  tone?: 'default' | 'muted';
  /** Smaller, denser text for asides such as developer-only notes. Defaults to the drawer's base size. */
  textSize?: 'xs';
}

/**
 * Declarative extra-section configuration per topic, replacing hardcoded per-topic conditionals in
 * `HelpDrawer`. Topics not listed here render only `summary` and `body`.
 */
export const HELP_EXTRA_SECTIONS: Partial<Record<HelpTopicId, HelpExtraSection[]>> = {
  dashboard: [
    { key: 'authHealth', tone: 'muted' },
    { key: 'globalContext', audience: 'global' },
    { key: 'tenantContext', audience: 'tenant' },
  ],
  integrations: [
    { key: 'globalScope', audience: 'global' },
    { key: 'tenantScope', audience: 'tenant' },
  ],
  enrollments: [
    { key: 'globalScope', audience: 'global' },
    { key: 'tenantScope', audience: 'tenant' },
  ],
  admins: [{ key: 'recoveryCodes' }],
  'encryption-keys': [
    { key: 'globalOps', audience: 'global' },
    { key: 'developerContext', tone: 'muted', textSize: 'xs' },
  ],
  'audit-logs': [
    { key: 'eventTypeVsStatus' },
    { key: 'statusLegend', tone: 'muted' },
    { key: 'mfaAndLogin' },
  ],
  integrity: [
    { key: 'globalOps', audience: 'global' },
  ],
};
