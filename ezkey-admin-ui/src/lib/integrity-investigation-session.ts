import type {
  ChainIntegrityViolation,
  EntryIntegrityViolation,
  EntryIntegrityViolationConciliationStatus,
  IntegrityReport,
} from '@/generated/admin-api/model';

const STORAGE_KEY = 'ezkey_integrity_investigation_session';

export type IntegrityInvestigationSource = 'integrity-alert' | 'manual';

export interface IntegrityInvestigationSession {
  source: IntegrityInvestigationSource;
  windowFrom: string;
  windowTo: string;
  verifiedAt: string;
  violatedEntryIds: number[];
  entryViolations: EntryIntegrityViolation[];
  chainViolations: ChainIntegrityViolation[];
  highlightAuditLogIds: number[];
  focusCheckpointId?: number;
  /** Conciliation posture keyed by audit log id from the latest verify response. */
  conciliationByAuditLogId: Record<number, EntryIntegrityViolationConciliationStatus>;
}

export interface CappedViolationListPayload<T> {
  items?: T[];
  totalCount?: number;
  returnedCount?: number;
  truncated?: boolean;
}

export function parseHighlightAuditLogIds(raw: string | null): number[] {
  if (!raw?.trim()) {
    return [];
  }
  return raw
    .split(',')
    .map((part) => Number.parseInt(part.trim(), 10))
    .filter((id) => Number.isFinite(id) && id > 0);
}

export function formatHighlightAuditLogIds(ids: number[]): string {
  return [...new Set(ids)].sort((a, b) => a - b).join(',');
}

export function loadIntegrityInvestigationSession(): IntegrityInvestigationSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as IntegrityInvestigationSession;
    if (!parsed?.windowFrom || !parsed?.windowTo) {
      return null;
    }
    return {
      ...parsed,
      conciliationByAuditLogId: parsed.conciliationByAuditLogId ?? {},
    };
  } catch {
    return null;
  }
}

export function saveIntegrityInvestigationSession(session: IntegrityInvestigationSession): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearIntegrityInvestigationSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

export function buildConciliationByAuditLogId(
  entryViolations: EntryIntegrityViolation[],
): Record<number, EntryIntegrityViolationConciliationStatus> {
  const map: Record<number, EntryIntegrityViolationConciliationStatus> = {};
  for (const violation of entryViolations) {
    if (violation.auditLogId != null && violation.conciliationStatus != null) {
      map[violation.auditLogId] = violation.conciliationStatus;
    }
  }
  return map;
}

export function buildInvestigationSession(params: {
  source: IntegrityInvestigationSource;
  windowFrom: string;
  windowTo: string;
  entryViolations: EntryIntegrityViolation[];
  chainViolations: ChainIntegrityViolation[];
  highlightAuditLogIds?: number[];
  focusCheckpointId?: number;
}): IntegrityInvestigationSession {
  const violatedEntryIds = params.entryViolations
    .map((v) => v.auditLogId)
    .filter((id): id is number => id != null && id > 0);
  const highlightAuditLogIds =
    params.highlightAuditLogIds && params.highlightAuditLogIds.length > 0
      ? params.highlightAuditLogIds
      : violatedEntryIds;

  return {
    source: params.source,
    windowFrom: params.windowFrom,
    windowTo: params.windowTo,
    verifiedAt: new Date().toISOString(),
    violatedEntryIds,
    entryViolations: params.entryViolations,
    chainViolations: params.chainViolations,
    highlightAuditLogIds,
    focusCheckpointId: params.focusCheckpointId,
    conciliationByAuditLogId: buildConciliationByAuditLogId(params.entryViolations),
  };
}

export type EntryHmacDisplayState =
  | 'unsigned'
  | 'signed'
  | 'verified'
  | 'violation'
  | 'violationExplained'
  | 'violationRetamper';

export function entryViolationDisplayState(
  violation: EntryIntegrityViolation,
): EntryHmacDisplayState {
  switch (violation.conciliationStatus) {
    case 'ACKNOWLEDGED':
      return 'violationExplained';
    case 'RE_TAMPER_SUSPECTED':
      return 'violationRetamper';
    default:
      return 'violation';
  }
}

/** True when reconcile must include this entry in acknowledgedAuditLogIds. */
export function isEntryReconcileAckRequired(violation: EntryIntegrityViolation): boolean {
  return violation.conciliationStatus !== 'ACKNOWLEDGED';
}

export function listReconcileRequiredEntryViolations(
  violations: EntryIntegrityViolation[],
): EntryIntegrityViolation[] {
  return violations.filter(isEntryReconcileAckRequired);
}

/**
 * Maps a single-entry integrity-check response to the shared badge display state.
 */
export function resolveIntegrityReportEntryDisplayState(
  hasEntryHmac: boolean,
  report: IntegrityReport | null | undefined,
): EntryHmacDisplayState {
  if (!hasEntryHmac) {
    return 'unsigned';
  }
  if (!report) {
    return 'signed';
  }
  const violation = report.entryViolations?.items?.[0];
  if (violation) {
    return entryViolationDisplayState(violation);
  }
  if ((report.invalidEntries ?? 0) > 0) {
    return 'violation';
  }
  if ((report.unsignedEntries ?? 0) > 0 || report.status === 'UNSIGNED') {
    return 'unsigned';
  }
  return 'verified';
}

function upsertEntryViolation(
  violations: EntryIntegrityViolation[],
  violation: EntryIntegrityViolation,
): EntryIntegrityViolation[] {
  const auditLogId = violation.auditLogId;
  if (auditLogId == null) {
    return violations;
  }
  return [...violations.filter((v) => v.auditLogId !== auditLogId), violation];
}

/**
 * Merges a detail-dialog single-entry verify result into the investigation session so list badges
 * stay aligned without forcing a full-range Verify pass.
 */
export function mergeSingleEntryVerificationIntoSession(
  existing: IntegrityInvestigationSession | null,
  auditLogId: number,
  violation: EntryIntegrityViolation | null | undefined,
  entryCreatedAt?: string | null,
): IntegrityInvestigationSession | null {
  const verifiedAt = new Date().toISOString();
  const createdAnchor = entryCreatedAt ?? verifiedAt;

  if (!violation) {
    if (!existing) {
      return null;
    }
    const entryViolations = existing.entryViolations.filter((v) => v.auditLogId !== auditLogId);
    const violatedEntryIds = entryViolations
      .map((v) => v.auditLogId)
      .filter((id): id is number => id != null && id > 0);
    const highlightAuditLogIds = existing.highlightAuditLogIds.filter((id) => id !== auditLogId);
    if (entryViolations.length === 0 && highlightAuditLogIds.length === 0) {
      clearIntegrityInvestigationSession();
      return null;
    }
    const updated: IntegrityInvestigationSession = {
      ...existing,
      verifiedAt,
      entryViolations,
      violatedEntryIds,
      highlightAuditLogIds,
      conciliationByAuditLogId: buildConciliationByAuditLogId(entryViolations),
    };
    saveIntegrityInvestigationSession(updated);
    return updated;
  }

  const base: IntegrityInvestigationSession =
    existing ?? {
      source: 'manual',
      windowFrom: createdAnchor,
      windowTo: verifiedAt,
      verifiedAt,
      violatedEntryIds: [],
      entryViolations: [],
      chainViolations: [],
      highlightAuditLogIds: [auditLogId],
      conciliationByAuditLogId: {},
    };

  const entryViolations = upsertEntryViolation(
    base.entryViolations,
    violation.auditLogId === auditLogId ? violation : { ...violation, auditLogId },
  );
  const violatedEntryIds = [...new Set([...base.violatedEntryIds, auditLogId])];
  const highlightAuditLogIds = [...new Set([...base.highlightAuditLogIds, auditLogId])];
  const updated: IntegrityInvestigationSession = {
    ...base,
    verifiedAt,
    entryViolations,
    violatedEntryIds,
    highlightAuditLogIds,
    conciliationByAuditLogId: buildConciliationByAuditLogId(entryViolations),
  };
  saveIntegrityInvestigationSession(updated);
  return updated;
}

export function resolveEntryHmacDisplayState(
  auditLogId: number | undefined,
  hasEntryHmac: boolean,
  session: IntegrityInvestigationSession | null,
): EntryHmacDisplayState {
  if (!hasEntryHmac) {
    return 'unsigned';
  }
  if (!session || auditLogId == null) {
    return 'signed';
  }
  const violation = session.entryViolations.find((v) => v.auditLogId === auditLogId);
  if (violation) {
    return entryViolationDisplayState(violation);
  }
  if (session.violatedEntryIds.includes(auditLogId)) {
    return 'violation';
  }
  if (session.violatedEntryIds.length > 0 || session.highlightAuditLogIds.length > 0) {
    return 'verified';
  }
  return 'signed';
}
