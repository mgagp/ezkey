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
  'ENROLLMENT_CREATED',
  'ENROLLMENT_DELETED',
  'ENROLLMENT_BIND',
  'ENROLLMENT_VERIFY',
  'AUTH_ATTEMPT_CREATED',
  'AUTH_ATTEMPT_PENDING',
  'AUTH_ATTEMPT_RESPOND',
  'AUTH_ATTEMPT_CANCELLED',
  'API_KEY_CREATED',
  'API_KEY_REVOKED',
  'API_KEY_EXPIRED',
  'API_KEY_AUTH_SUCCESS',
  'API_KEY_AUTH_FAILED',
  'API_KEY_IP_BLOCKED',
  'SYSTEM_ERROR',
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
