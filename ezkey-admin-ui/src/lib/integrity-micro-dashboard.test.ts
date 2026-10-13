import { describe, expect, it } from 'vitest';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';
import {
  defaultAutoVerifyInstantRange,
  hasJobBackedChainGlance,
  INTEGRITY_AUTO_VERIFY_WINDOW_MS,
  INTEGRITY_VERDICT_FRESH_MS,
  integrityMicroDashboardSource,
  integrityWindowDaysToHours,
  isFreshIntegrityVerdict,
  isIntegrityChainStatusNonGreen,
  isIntegrityVerdictScopeMismatch,
  resolveIntegrityMicroDashboardTone,
} from './integrity-micro-dashboard';

function job(
  overrides: Partial<IntegrityAsyncJobResponse> = {},
): IntegrityAsyncJobResponse {
  return {
    jobId: '11111111-1111-1111-1111-111111111111',
    type: 'VERIFY_CHAIN_RANGE',
    status: 'SUCCEEDED',
    ...overrides,
  };
}

describe('isFreshIntegrityVerdict', () => {
  const now = Date.parse('2026-10-13T12:00:00.000Z');

  it('is true when SUCCEEDED finished within 24 h', () => {
    expect(
      isFreshIntegrityVerdict(
        job({ finishedAt: new Date(now - 3 * 60 * 60 * 1000).toISOString() }),
        now,
      ),
    ).toBe(true);
  });

  it('is false when finished at or beyond 24 h', () => {
    expect(
      isFreshIntegrityVerdict(
        job({ finishedAt: new Date(now - INTEGRITY_VERDICT_FRESH_MS).toISOString() }),
        now,
      ),
    ).toBe(false);
  });

  it('is false for RUNNING / FAILED / missing finishedAt', () => {
    expect(isFreshIntegrityVerdict(job({ status: 'RUNNING', finishedAt: undefined }), now)).toBe(
      false,
    );
    expect(
      isFreshIntegrityVerdict(
        job({
          status: 'FAILED',
          finishedAt: new Date(now - 1000).toISOString(),
        }),
        now,
      ),
    ).toBe(false);
    expect(isFreshIntegrityVerdict(job({ finishedAt: undefined }), now)).toBe(false);
  });
});

describe('defaultAutoVerifyInstantRange', () => {
  it('returns a 24 h Instant window ending at now', () => {
    const now = new Date('2026-10-13T12:00:00.000Z');
    const range = defaultAutoVerifyInstantRange(now);
    expect(range.to).toBe(now.toISOString());
    expect(Date.parse(range.to) - Date.parse(range.from)).toBe(INTEGRITY_AUTO_VERIFY_WINDOW_MS);
  });
});

describe('resolveIntegrityMicroDashboardTone', () => {
  const now = Date.parse('2026-10-13T12:00:00.000Z');

  it('maps RUNNING + nightly busyHolder', () => {
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          status: 'RUNNING',
          busyHolder: 'NIGHTLY',
          trigger: 'NIGHTLY',
          finishedAt: undefined,
        }),
        now,
      ),
    ).toBe('nightly-running');
  });

  it('maps SUCCEEDED chainStatus without using resultSummary vocabulary', () => {
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          chainStatus: 'OK',
          finishedAt: new Date(now - 1000).toISOString(),
          resultSummary: 'succeeded with gaps',
        }),
        now,
      ),
    ).toBe('intact');
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          chainStatus: 'UNDECLARED_GAP_DETECTED',
          undeclaredGaps: 2,
          finishedAt: new Date(now - 1000).toISOString(),
        }),
        now,
      ),
    ).toBe('gaps');
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          chainStatus: 'CHAIN_INTEGRITY_VIOLATION_DETECTED',
          invalidCheckpoints: 1,
          finishedAt: new Date(now - 1000).toISOString(),
        }),
        now,
      ),
    ).toBe('violation');
  });

  it('marks stale when SUCCEEDED older than 24 h', () => {
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          chainStatus: 'OK',
          finishedAt: new Date(now - INTEGRITY_VERDICT_FRESH_MS - 1).toISOString(),
        }),
        now,
      ),
    ).toBe('stale');
  });

  it('maps FAILED to did-not-complete', () => {
    expect(
      resolveIntegrityMicroDashboardTone(
        job({ status: 'FAILED', finishedAt: new Date(now - 1000).toISOString() }),
        now,
      ),
    ).toBe('did-not-complete');
  });
});

describe('scope + helpers', () => {
  it('detects picker vs verdict display mismatch (#696)', () => {
    expect(
      isIntegrityVerdictScopeMismatch(
        { from: '2026-10-01', to: '2026-10-07' },
        { from: '2026-10-01', to: '2026-10-08' },
      ),
    ).toBe(true);
    expect(
      isIntegrityVerdictScopeMismatch(
        { from: '2026-10-01', to: '2026-10-07' },
        { from: '2026-10-01', to: '2026-10-07' },
      ),
    ).toBe(false);
  });

  it('maps trigger sources and window hours', () => {
    expect(integrityMicroDashboardSource('AUTO')).toBe('auto');
    expect(integrityMicroDashboardSource('MANUAL')).toBe('manual');
    expect(integrityMicroDashboardSource('NIGHTLY')).toBe('nightly');
    expect(integrityWindowDaysToHours(8)).toBe(193);
    expect(integrityWindowDaysToHours(92)).toBe(2209);
  });

  it('detects job-backed glance and non-green status', () => {
    expect(hasJobBackedChainGlance(job({ chainStatus: 'OK' }))).toBe(true);
    expect(hasJobBackedChainGlance(job({ chainStatus: null }))).toBe(false);
    expect(isIntegrityChainStatusNonGreen('UNDECLARED_GAP_DETECTED')).toBe(true);
    expect(isIntegrityChainStatusNonGreen('OK')).toBe(false);
  });
});
