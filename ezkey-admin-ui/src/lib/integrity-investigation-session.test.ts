import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { EntryIntegrityViolation, IntegrityReport } from '@/generated/admin-api/model';
import {
  entryViolationDisplayState,
  mergeSingleEntryVerificationIntoSession,
  resolveIntegrityReportEntryDisplayState,
} from '@/lib/integrity-investigation-session';

beforeEach(() => {
  const store = new Map<string, string>();
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => {
      store.clear();
    },
  });
});

describe('resolveIntegrityReportEntryDisplayState', () => {
  it('returns violationExplained when conciliation is ACKNOWLEDGED', () => {
    const report: IntegrityReport = {
      totalEntries: 1,
      invalidEntries: 1,
      intact: false,
      status: 'INTEGRITY_VIOLATION_DETECTED',
      entryViolations: {
        items: [
          {
            auditLogId: 42,
            conciliationStatus: 'ACKNOWLEDGED',
            reason: 'HMAC_MISMATCH',
          } as EntryIntegrityViolation,
        ],
      },
    };
    expect(resolveIntegrityReportEntryDisplayState(true, report)).toBe('violationExplained');
    expect(entryViolationDisplayState(report.entryViolations!.items![0])).toBe('violationExplained');
  });
});

describe('mergeSingleEntryVerificationIntoSession', () => {
  it('creates a manual session when reconciled cache was cleared', () => {
    const violation = {
      auditLogId: 1104,
      conciliationStatus: 'ACKNOWLEDGED',
      reason: 'HMAC_MISMATCH',
    } as EntryIntegrityViolation;

    const session = mergeSingleEntryVerificationIntoSession(
      null,
      1104,
      violation,
      '2026-07-03T11:56:27Z',
    );

    expect(session?.entryViolations).toHaveLength(1);
    expect(session?.conciliationByAuditLogId[1104]).toBe('ACKNOWLEDGED');
    expect(session?.source).toBe('manual');
  });
});
