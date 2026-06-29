/**
 * Event families for audit log filtering — aligned with `EventTypeFamily` in the Admin API / core
 * backend. Each family groups related audit event types for UI optgroups and the `eventTypeFamily`
 * query parameter.
 */

import type { AuditEventTypeKey } from '@/lib/audit-event-type';
import type {
  GetAuditLogsEventType,
  GetAuditLogsEventTypeFamily,
} from '@/generated/admin-api/model';

/** Wire values for GET /api/v1/audit-logs?eventTypeFamily= */
export const EVENT_TYPE_FAMILY_KEYS = [
  'ADMIN',
  'ENROLLMENT',
  'AUTH_ATTEMPT',
  'API_KEY',
  'SYSTEM',
  'ENCRYPTION_KEY',
  'REENCRYPTION',
  'INTEGRATION',
  'TENANT',
  'AUDIT_CHAIN',
] as const;

export type AuditEventTypeFamilyKey = (typeof EVENT_TYPE_FAMILY_KEYS)[number];

export type AuditEventTypeGroup = {
  family: AuditEventTypeFamilyKey;
  /** Event types in this family, in display order */
  memberKeys: readonly AuditEventTypeKey[];
};

/**
 * Declarative grouping for the audit log event filter (optgroups). Must stay aligned with
 * `EventTypeFamily` in ezkey-core.
 */
export const AUDIT_EVENT_TYPE_GROUPS: readonly AuditEventTypeGroup[] = [
  {
    family: 'ADMIN',
    memberKeys: [
      'ADMIN_LOGIN',
      'ADMIN_LOGOUT',
      'ADMIN_PASSWORD_CHANGE',
      'ADMIN_RECOVERY_USE',
      'ADMIN_RECOVERY_CODES_REGENERATED',
      'ADMIN_RECOVERY_ENROLLMENT_RESET',
      'ADMIN_CREATED',
      'ADMIN_PROFILE_UPDATED',
      'ADMIN_DEACTIVATED',
      'ADMIN_ACTIVATED',
    ],
  },
  {
    family: 'ENROLLMENT',
    memberKeys: [
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
    ],
  },
  {
    family: 'AUTH_ATTEMPT',
    memberKeys: [
      'AUTH_ATTEMPT_CREATED',
      'AUTH_ATTEMPT_PENDING',
      'AUTH_ATTEMPT_RESPOND',
      'AUTH_ATTEMPT_CANCELLED',
      'AUTH_ATTEMPT_EXPIRED',
    ],
  },
  {
    family: 'API_KEY',
    memberKeys: [
      'API_KEY_CREATED',
      'API_KEY_UPDATED',
      'API_KEY_REVOKED',
      'API_KEY_EXPIRED',
      'API_KEY_AUTH_SUCCESS',
      'API_KEY_AUTH_FAILED',
      'API_KEY_IP_BLOCKED',
    ],
  },
  {
    family: 'SYSTEM',
    memberKeys: ['SYSTEM_ERROR'],
  },
  {
    family: 'ENCRYPTION_KEY',
    memberKeys: [
      'KEY_INTRODUCED',
      'KEY_PROMOTED_PRIMARY',
      'KEY_DEMOTED',
      'KEY_DISABLED',
      'KEYSET_BACKUP_CREATED',
    ],
  },
  {
    family: 'REENCRYPTION',
    memberKeys: [
      'REENCRYPTION_STARTED',
      'REENCRYPTION_BATCH_PROGRESS',
      'REENCRYPTION_COMPLETED',
      'REENCRYPTION_FAILED',
      'REENCRYPTION_RESUMED',
      'REENCRYPTION_PAUSED',
    ],
  },
  {
    family: 'INTEGRATION',
    memberKeys: ['INTEGRATION_CREATED', 'INTEGRATION_UPDATED', 'INTEGRATION_RETIRED', 'INTEGRATION_DELETED'],
  },
  {
    family: 'TENANT',
    memberKeys: ['TENANT_CREATED', 'TENANT_UPDATED', 'TENANT_DEACTIVATED', 'TENANT_ACTIVATED'],
  },
  {
    family: 'AUDIT_CHAIN',
    memberKeys: ['AUDIT_CHAIN_ARCHIVE_SEALED', 'AUDIT_CHAIN_GAP_DECLARED', 'AUDIT_CHAIN_GAP_PENDING', 'NIGHTLY_INTEGRITY_VALIDATION_COMPLETED'],
  },
] as const;

const FAMILY_KEY_SET = new Set<string>(EVENT_TYPE_FAMILY_KEYS);

/**
 * Maps a single filter value to API params. Family keys match `eventTypeFamily`; other values are
 * concrete `eventType` enum names.
 */
export function auditEventFilterToApiParams(selected: string): {
  eventType?: GetAuditLogsEventType;
  eventTypeFamily?: GetAuditLogsEventTypeFamily;
} {
  if (selected === '') {
    return {};
  }
  if (FAMILY_KEY_SET.has(selected)) {
    return { eventTypeFamily: selected as GetAuditLogsEventTypeFamily };
  }
  return { eventType: selected as GetAuditLogsEventType };
}
