import type {
  ChainIntegrityViolation,
  EntryIntegrityViolation,
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
    return parsed;
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
  };
}

export type EntryHmacDisplayState = 'unsigned' | 'signed' | 'verified' | 'violation';

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
  if (session.violatedEntryIds.includes(auditLogId)) {
    return 'violation';
  }
  return 'verified';
}
