/**
 * Shared audit event type constants and label helper for i18n.
 * Used by the audit logs page and the dashboard (recent activity, alerts).
 */

/** Known event types (from EventType.java enum). Used for filters and translated labels. */
export const EVENT_TYPE_KEYS = [
  'ADMIN_LOGIN',
  'ADMIN_LOGOUT',
  'ADMIN_PASSWORD_CHANGE',
  'ADMIN_RECOVERY_USE',
  'ADMIN_RECOVERY_ENROLLMENT_RESET',
  'ADMIN_CREATED',
  'ADMIN_PROFILE_UPDATED',
  'ADMIN_DEACTIVATED',
  'ADMIN_ACTIVATED',
  'ENROLLMENT_CREATED',
  'ENROLLMENT_UPDATED',
  'ENROLLMENT_DELETED',
  'ENROLLMENT_BIND',
  'ENROLLMENT_VERIFY',
  'ENROLLMENT_REVOKED',
  'ENROLLMENT_DEACTIVATED',
  'ENROLLMENT_REACTIVATED',
  'ENROLLMENT_AUTH_ATTEMPT_BLOCKED',
  'ENROLLMENT_EXPIRED',
  'AUTH_ATTEMPT_CREATED',
  'AUTH_ATTEMPT_PENDING',
  'AUTH_ATTEMPT_RESPOND',
  'AUTH_ATTEMPT_CANCELLED',
  'AUTH_ATTEMPT_EXPIRED',
  'API_KEY_CREATED',
  'API_KEY_UPDATED',
  'API_KEY_REVOKED',
  'API_KEY_EXPIRED',
  'API_KEY_AUTH_SUCCESS',
  'API_KEY_AUTH_FAILED',
  'API_KEY_IP_BLOCKED',
  'SYSTEM_ERROR',
  'KEY_INTRODUCED',
  'KEY_PROMOTED_PRIMARY',
  'KEY_DEMOTED',
  'KEY_DISABLED',
  'KEYSET_BACKUP_CREATED',
  'REENCRYPTION_STARTED',
  'REENCRYPTION_BATCH_PROGRESS',
  'REENCRYPTION_COMPLETED',
  'REENCRYPTION_FAILED',
  'REENCRYPTION_RESUMED',
  'REENCRYPTION_PAUSED',
  'INTEGRATION_CREATED',
  'INTEGRATION_UPDATED',
  'INTEGRATION_DELETED',
  'TENANT_CREATED',
  'TENANT_UPDATED',
  'TENANT_DEACTIVATED',
  'TENANT_ACTIVATED',
  'AUDIT_CHAIN_ARCHIVE_SEALED',
  'AUDIT_CHAIN_GAP_DECLARED',
  'AUDIT_CHAIN_GAP_PENDING',
] as const;

export type AuditEventTypeKey = (typeof EVENT_TYPE_KEYS)[number];

/** Translation function (e.g. from useTranslation). Accepts full key like 'audit-logs:eventType.ADMIN_LOGIN'. */
export type AuditEventTypeTranslate = (key: string) => string;

/**
 * Returns the localized label for an audit event type.
 * Uses audit-logs namespace keys when the type is known; otherwise falls back to title-case of the raw value.
 *
 * @param eventType - Raw event type from API (e.g. ADMIN_LOGIN, ENROLLMENT_CREATED)
 * @param t - i18n translate function (must resolve 'audit-logs:eventType.*' keys)
 * @returns Translated or title-cased label
 */
export function getAuditEventTypeLabel(
  eventType: string | undefined,
  t: AuditEventTypeTranslate,
): string {
  if (eventType == null || eventType === '') return '';
  const known = EVENT_TYPE_KEYS.includes(eventType as AuditEventTypeKey);
  if (known) return t(`audit-logs:eventType.${eventType}`);
  return eventType
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');
}

/**
 * Converts snake_case to Title Case for use as a fallback when no translation exists.
 */
function snakeToTitleCase(value: string): string {
  return value
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
}

/**
 * Returns a human-readable label for an audit event action (e.g. admin_deactivation_failed).
 * Uses audit-logs:eventAction.* when the key exists; otherwise falls back to Title Case of the raw value.
 *
 * @param eventAction - Raw action from API (e.g. admin_deactivated, login_success)
 * @param t - i18n translate function (must resolve 'audit-logs:eventAction.*' keys)
 * @returns Translated or title-cased label
 */
export function getAuditEventActionLabel(
  eventAction: string | undefined,
  t: AuditEventTypeTranslate,
): string {
  if (eventAction == null || eventAction === '') return '';
  const key = `audit-logs:eventAction.${eventAction}`;
  const translated = t(key);
  if (translated !== key) return translated;
  return snakeToTitleCase(eventAction);
}
