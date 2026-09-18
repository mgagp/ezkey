import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { EntryIntegrityViolation, IntegrityReport } from '@/generated/admin-api/model';
import {
  buildIntegrityDeepLink,
  buildIntegrityReconcileDeepLink,
  entryViolationDisplayState,
  mergeSingleEntryVerificationIntoSession,
  resolveEntryIntegrityReportSummaryState,
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

describe('resolveEntryIntegrityReportSummaryState', () => {
  it('returns intact when report.intact is true', () => {
    expect(
      resolveEntryIntegrityReportSummaryState({
        intact: true,
        totalEntries: 10,
      } as IntegrityReport),
    ).toBe('intact');
  });

  it('returns violation when any listed violation is not ACKNOWLEDGED', () => {
    expect(
      resolveEntryIntegrityReportSummaryState({
        intact: false,
        invalidEntries: 2,
        entryViolations: {
          items: [
            { auditLogId: 1, conciliationStatus: 'ACKNOWLEDGED' } as EntryIntegrityViolation,
            { auditLogId: 2, conciliationStatus: 'NONE' } as EntryIntegrityViolation,
          ],
        },
      } as IntegrityReport),
    ).toBe('violation');
  });

  it('returns allExplained when every listed violation is ACKNOWLEDGED', () => {
    expect(
      resolveEntryIntegrityReportSummaryState({
        intact: false,
        invalidEntries: 2,
        entryViolations: {
          items: [
            { auditLogId: 1, conciliationStatus: 'ACKNOWLEDGED' } as EntryIntegrityViolation,
            { auditLogId: 2, conciliationStatus: 'ACKNOWLEDGED' } as EntryIntegrityViolation,
          ],
        },
      } as IntegrityReport),
    ).toBe('allExplained');
  });

  it('returns violation for RE_TAMPER_SUSPECTED even when mixed with ACKNOWLEDGED', () => {
    expect(
      resolveEntryIntegrityReportSummaryState({
        intact: false,
        invalidEntries: 2,
        entryViolations: {
          items: [
            { auditLogId: 1, conciliationStatus: 'ACKNOWLEDGED' } as EntryIntegrityViolation,
            { auditLogId: 2, conciliationStatus: 'RE_TAMPER_SUSPECTED' } as EntryIntegrityViolation,
          ],
        },
      } as IntegrityReport),
    ).toBe('violation');
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

describe('buildIntegrityDeepLink', () => {
  it('builds investigate deep-link with alert id and window', () => {
    const href = buildIntegrityDeepLink({
      failBoundary: '2026-09-01T00:00:00Z',
      resumeBoundary: '2026-09-02T00:00:00Z',
      alertId: 42,
      highlightAuditLogIds: [7, 3],
    });
    expect(href).toContain('/integrity?');
    expect(href).toContain('source=integrity-alert');
    expect(href).toContain('alertId=42');
    expect(href).toContain('createdAfter=2026-09-01T00%3A00%3A00Z');
    expect(href).toContain('createdBefore=2026-09-02T00%3A00%3A00Z');
    expect(href).toContain('highlightAuditLogIds=3%2C7');
    expect(href).not.toContain('action=reconcile');
  });
});

describe('buildIntegrityReconcileDeepLink', () => {
  it('uses frozen Julie contract action + alertId only', () => {
    const href = buildIntegrityReconcileDeepLink({ alertId: 9 });
    expect(href).toBe('/integrity?action=reconcile&alertId=9');
  });

  it('forwards ruptureId only when provided', () => {
    expect(buildIntegrityReconcileDeepLink({ alertId: 9, ruptureId: null })).toBe(
      '/integrity?action=reconcile&alertId=9',
    );
    expect(buildIntegrityReconcileDeepLink({ alertId: 9, ruptureId: 'abc' })).toBe(
      '/integrity?action=reconcile&alertId=9&ruptureId=abc',
    );
  });
});
