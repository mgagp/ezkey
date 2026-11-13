import type { AlertResponseDtoAlertType } from '@/generated/admin-api/model';

export type AlertListSummaryTemplateKey =
  | 'list.summary.gapPending'
  | 'list.summary.heartbeatStale'
  | 'list.summary.integrityRupture'
  | 'list.summary.partitionCreationFailed'
  | 'list.summary.unavailable';

export interface AlertListSummary {
  templateKey: AlertListSummaryTemplateKey;
  params: Record<string, string | number>;
}

interface AuditChainGapPayload {
  anchorCheckpointId?: number;
  estimatedGapMinutes?: number;
}

interface AuditChainHeartbeatPayload {
  phase?: string;
  anchorCheckpointId?: number | null;
}

interface CappedListPayload {
  totalCount?: number;
}

interface AuditIntegrityRupturePayload {
  violationCount?: number;
  entryHmacViolationCount?: number;
  undeclaredGapCount?: number;
  entryViolations?: CappedListPayload;
  chainViolations?: CappedListPayload;
}

function parsePayload(payload: string | undefined): unknown | null {
  if (!payload) {
    return null;
  }
  try {
    return JSON.parse(payload) as unknown;
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function readNumber(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function readOptionalCheckpointId(value: unknown): number | undefined {
  if (value == null) {
    return undefined;
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  return undefined;
}

function asGapPayload(value: unknown): AuditChainGapPayload | null {
  if (!isRecord(value)) {
    return null;
  }
  return {
    anchorCheckpointId: readOptionalCheckpointId(value.anchorCheckpointId),
    estimatedGapMinutes: readNumber(value.estimatedGapMinutes),
  };
}

function asHeartbeatPayload(value: unknown): AuditChainHeartbeatPayload | null {
  if (!isRecord(value)) {
    return null;
  }
  return {
    phase: typeof value.phase === 'string' ? value.phase : undefined,
    anchorCheckpointId: readOptionalCheckpointId(value.anchorCheckpointId),
  };
}

function asIntegrityRupturePayload(value: unknown): AuditIntegrityRupturePayload | null {
  if (!isRecord(value)) {
    return null;
  }
  const entryViolations = isRecord(value.entryViolations)
    ? { totalCount: readNumber(value.entryViolations.totalCount) }
    : undefined;
  const chainViolations = isRecord(value.chainViolations)
    ? { totalCount: readNumber(value.chainViolations.totalCount) }
    : undefined;
  return {
    violationCount: readNumber(value.violationCount),
    entryHmacViolationCount: readNumber(value.entryHmacViolationCount),
    undeclaredGapCount: readNumber(value.undeclaredGapCount),
    entryViolations,
    chainViolations,
  };
}

function resolveEntryViolationCount(payload: AuditIntegrityRupturePayload): number {
  return (
    payload.entryViolations?.totalCount
    ?? payload.entryHmacViolationCount
    ?? 0
  );
}

function resolveChainViolationCount(payload: AuditIntegrityRupturePayload): number {
  return payload.chainViolations?.totalCount ?? payload.violationCount ?? 0;
}

interface PartitionCreationPayload {
  from?: string;
  to?: string;
  failedTargets?: number;
}

function asPartitionPayload(value: unknown): PartitionCreationPayload | null {
  if (!isRecord(value)) {
    return null;
  }
  return {
    from: typeof value.from === 'string' ? value.from : undefined,
    to: typeof value.to === 'string' ? value.to : undefined,
    failedTargets: readNumber(value.failedTargets),
  };
}

/**
 * Derives a type-specific one-line summary from alert payload JSON for the list view.
 */
export function resolveAlertListSummary(
  alertType: AlertResponseDtoAlertType | undefined,
  payload: string | undefined,
): AlertListSummary {
  const parsed = parsePayload(payload);

  if (alertType === 'AUDIT_CHAIN_GAP_PENDING') {
    const gap = asGapPayload(parsed);
    if (
      gap?.anchorCheckpointId != null
      && gap.estimatedGapMinutes != null
    ) {
      return {
        templateKey: 'list.summary.gapPending',
        params: {
          anchorCheckpointId: gap.anchorCheckpointId,
          gapMinutes: gap.estimatedGapMinutes,
        },
      };
    }
    return { templateKey: 'list.summary.unavailable', params: {} };
  }

  if (alertType === 'AUDIT_CHAIN_HEARTBEAT_STALE') {
    const heartbeat = asHeartbeatPayload(parsed);
    if (heartbeat?.phase) {
      return {
        templateKey: 'list.summary.heartbeatStale',
        params: {
          phase: heartbeat.phase,
          anchorCheckpointId:
            heartbeat.anchorCheckpointId != null
              ? heartbeat.anchorCheckpointId
              : '—',
        },
      };
    }
    return { templateKey: 'list.summary.unavailable', params: {} };
  }

  if (alertType === 'AUDIT_INTEGRITY_RUPTURE') {
    const rupture = asIntegrityRupturePayload(parsed);
    if (rupture) {
      return {
        templateKey: 'list.summary.integrityRupture',
        params: {
          entryCount: resolveEntryViolationCount(rupture),
          chainCount: resolveChainViolationCount(rupture),
          gapCount: rupture.undeclaredGapCount ?? 0,
        },
      };
    }
    return { templateKey: 'list.summary.unavailable', params: {} };
  }

  if (alertType === 'PARTITION_CREATION_FAILED') {
    const partition = asPartitionPayload(parsed);
    if (partition?.from && partition.to && partition.failedTargets != null) {
      return {
        templateKey: 'list.summary.partitionCreationFailed',
        params: {
          from: partition.from,
          to: partition.to,
          failedTargets: partition.failedTargets,
        },
      };
    }
    return { templateKey: 'list.summary.unavailable', params: {} };
  }

  return { templateKey: 'list.summary.unavailable', params: {} };
}
