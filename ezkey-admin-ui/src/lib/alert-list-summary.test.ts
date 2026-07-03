import { describe, expect, it } from 'vitest';
import { resolveAlertListSummary } from './alert-list-summary';

describe('resolveAlertListSummary', () => {
  it('summarizes AUDIT_CHAIN_GAP_PENDING from payload', () => {
    const result = resolveAlertListSummary(
      'AUDIT_CHAIN_GAP_PENDING',
      JSON.stringify({
        anchorCheckpointId: 42,
        estimatedGapMinutes: 90,
      }),
    );
    expect(result).toEqual({
      templateKey: 'list.summary.gapPending',
      params: { anchorCheckpointId: 42, gapMinutes: 90 },
    });
  });

  it('summarizes AUDIT_CHAIN_HEARTBEAT_STALE with phase and anchor', () => {
    const result = resolveAlertListSummary(
      'AUDIT_CHAIN_HEARTBEAT_STALE',
      JSON.stringify({
        phase: 'DEGRADED_SERVICE',
        anchorCheckpointId: 7,
        latestWindowEnd: '2026-07-03T10:00:00Z',
      }),
    );
    expect(result).toEqual({
      templateKey: 'list.summary.heartbeatStale',
      params: { phase: 'DEGRADED_SERVICE', anchorCheckpointId: 7 },
    });
  });

  it('summarizes AUDIT_INTEGRITY_RUPTURE using structured list totals', () => {
    const result = resolveAlertListSummary(
      'AUDIT_INTEGRITY_RUPTURE',
      JSON.stringify({
        violationCount: 2,
        entryHmacViolationCount: 3,
        entryViolations: { totalCount: 5, returnedCount: 5, truncated: false },
        chainViolations: { totalCount: 4, returnedCount: 4, truncated: false },
      }),
    );
    expect(result).toEqual({
      templateKey: 'list.summary.integrityRupture',
      params: { entryCount: 5, chainCount: 4 },
    });
  });

  it('falls back to scalar counts when list totals are absent', () => {
    const result = resolveAlertListSummary(
      'AUDIT_INTEGRITY_RUPTURE',
      JSON.stringify({
        violationCount: 2,
        entryHmacViolationCount: 3,
      }),
    );
    expect(result).toEqual({
      templateKey: 'list.summary.integrityRupture',
      params: { entryCount: 3, chainCount: 2 },
    });
  });

  it('returns unavailable when payload JSON is invalid', () => {
    expect(
      resolveAlertListSummary('AUDIT_CHAIN_GAP_PENDING', '{not-json'),
    ).toEqual({
      templateKey: 'list.summary.unavailable',
      params: {},
    });
  });
});
