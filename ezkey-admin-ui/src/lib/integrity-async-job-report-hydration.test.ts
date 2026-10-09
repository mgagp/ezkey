import { describe, expect, it, vi } from 'vitest';
import type {
  ChainVerificationReport,
  IntegrityReport,
} from '@/generated/admin-api/model';
import {
  integrityExclusiveApiParamsToDisplayRange,
  integrityExclusiveDateRangeToApiParams,
} from '@/lib/date-range-presets';
import { ApiError } from '@/lib/api-client';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';
import {
  executeIntegrityAsyncJobReportHydration,
  INTEGRITY_ASYNC_JOB_BUSY_TYPE,
  INTEGRITY_REPORT_MAX_WINDOW_HOURS,
  isIntegrityReportHydrationBusyError,
  isIntegrityReportScopeOverCap,
  isIntegrityWindowOverCapError,
  isSucceededVerifyJobForReportHydration,
  resolveIntegrityAsyncJobReportHydration,
  type IntegrityReportHydrationRequest,
} from './integrity-async-job-report-hydration';

function job(
  overrides: Partial<IntegrityAsyncJobResponse> &
    Pick<IntegrityAsyncJobResponse, 'jobId' | 'type' | 'status'>,
): IntegrityAsyncJobResponse {
  return {
    scopeFrom: '2026-10-01T00:00:00Z',
    scopeTo: '2026-10-08T00:00:00Z',
    ...overrides,
  };
}

describe('isSucceededVerifyJobForReportHydration', () => {
  it('is true for succeeded chain and entry verify jobs', () => {
    expect(
      isSucceededVerifyJobForReportHydration(
        job({ jobId: 'a', type: 'VERIFY_CHAIN_RANGE', status: 'SUCCEEDED' }),
      ),
    ).toBe(true);
    expect(
      isSucceededVerifyJobForReportHydration(
        job({ jobId: 'b', type: 'VERIFY_ENTRY_HMAC_RANGE', status: 'SUCCEEDED' }),
      ),
    ).toBe(true);
  });

  it('is false for running, failed, abandoned, or validation jobs', () => {
    expect(
      isSucceededVerifyJobForReportHydration(
        job({ jobId: 'a', type: 'VERIFY_CHAIN_RANGE', status: 'RUNNING' }),
      ),
    ).toBe(false);
    expect(
      isSucceededVerifyJobForReportHydration(
        job({ jobId: 'a', type: 'VERIFY_CHAIN_RANGE', status: 'FAILED' }),
      ),
    ).toBe(false);
    expect(
      isSucceededVerifyJobForReportHydration(
        job({
          jobId: 'a',
          type: 'VERIFY_CHAIN_RANGE',
          status: 'SUCCEEDED',
          abandonedAt: '2026-10-07T12:00:00Z',
        }),
      ),
    ).toBe(false);
    expect(
      isSucceededVerifyJobForReportHydration(
        job({ jobId: 'a', type: 'RUN_VALIDATION', status: 'SUCCEEDED' }),
      ),
    ).toBe(false);
  });
});

describe('isIntegrityReportScopeOverCap', () => {
  it('accepts the America/Toronto fall-back default lookback Instant span (193h)', () => {
    expect(
      isIntegrityReportScopeOverCap(
        '2026-10-27T04:00:00.000Z',
        '2026-11-04T05:00:00.000Z',
      ),
    ).toBe(false);
    expect(INTEGRITY_REPORT_MAX_WINDOW_HOURS).toBe(193);
  });

  it('rejects windows longer than 193 hours', () => {
    expect(
      isIntegrityReportScopeOverCap(
        '2026-10-27T04:00:00.000Z',
        '2026-11-04T06:00:00.000Z',
      ),
    ).toBe(true);
  });
});

describe('isIntegrityWindowOverCapError', () => {
  it('is true for HTTP 400 with exceeds-maximum detail', () => {
    expect(
      isIntegrityWindowOverCapError(
        new ApiError(
          400,
          {
            type: 'https://ezkey.io/problems/invalid-argument',
            status: 400,
            detail:
              'Verification window exceeds maximum of 193 hours (8 calendar days, DST transition included). Narrow the range.',
          },
          'exceeds maximum',
        ),
      ),
    ).toBe(true);
  });

  it('is false for other 400s', () => {
    expect(
      isIntegrityWindowOverCapError(
        new ApiError(
          400,
          { type: 'https://ezkey.io/problems/invalid-argument', status: 400, detail: 'bad' },
          'bad',
        ),
      ),
    ).toBe(false);
  });
});

describe('isIntegrityReportHydrationBusyError', () => {
  it('is true for HTTP 409 integrity-async-job-busy', () => {
    const error = new ApiError(
      409,
      {
        type: INTEGRITY_ASYNC_JOB_BUSY_TYPE,
        title: 'Integrity async slot busy',
        status: 409,
        detail: 'Integrity crypto path busy (scheduled or in-process heavy work)',
      },
      'Integrity crypto path busy (scheduled or in-process heavy work)',
    );
    expect(isIntegrityReportHydrationBusyError(error)).toBe(true);
  });

  it('is false for other 409s, 400s, or non-ApiError', () => {
    expect(
      isIntegrityReportHydrationBusyError(
        new ApiError(
          409,
          { type: 'https://ezkey.io/problems/domain/other', status: 409 },
          'other',
        ),
      ),
    ).toBe(false);
    expect(
      isIntegrityReportHydrationBusyError(
        new ApiError(400, { type: 'https://ezkey.io/problems/admin/invalid-argument' }, 'cap'),
      ),
    ).toBe(false);
    expect(isIntegrityReportHydrationBusyError(new Error('boom'))).toBe(false);
  });
});

describe('resolveIntegrityAsyncJobReportHydration', () => {
  it('returns null while RUNNING', () => {
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-run',
          type: 'VERIFY_CHAIN_RANGE',
          status: 'RUNNING',
        }),
        null,
      ),
    ).toBeNull();
  });

  it('returns a chain request when VERIFY_CHAIN_RANGE SUCCEEDED', () => {
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-chain',
          type: 'VERIFY_CHAIN_RANGE',
          status: 'SUCCEEDED',
        }),
        null,
      ),
    ).toEqual({
      kind: 'chain',
      jobId: 'j-chain',
      from: '2026-10-01T00:00:00Z',
      to: '2026-10-08T00:00:00Z',
    } satisfies IntegrityReportHydrationRequest);
  });

  it('returns an entry request when VERIFY_ENTRY_HMAC_RANGE SUCCEEDED', () => {
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-entry',
          type: 'VERIFY_ENTRY_HMAC_RANGE',
          status: 'SUCCEEDED',
        }),
        null,
      ),
    ).toEqual({
      kind: 'entry',
      jobId: 'j-entry',
      from: '2026-10-01T00:00:00Z',
      to: '2026-10-08T00:00:00Z',
    });
  });

  it('does not hydrate FAILED, CANCELLED, or other job types', () => {
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-fail',
          type: 'VERIFY_CHAIN_RANGE',
          status: 'FAILED',
        }),
        null,
      ),
    ).toBeNull();
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-cancel',
          type: 'VERIFY_CHAIN_RANGE',
          status: 'CANCELLED',
        }),
        null,
      ),
    ).toBeNull();
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-val',
          type: 'RUN_VALIDATION',
          status: 'SUCCEEDED',
        }),
        null,
      ),
    ).toBeNull();
  });

  it('dedupes by jobId (already hydrated / in-flight)', () => {
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-once',
          type: 'VERIFY_CHAIN_RANGE',
          status: 'SUCCEEDED',
        }),
        'j-once',
      ),
    ).toBeNull();
  });

  it('hydrates an already-SUCCEEDED job on first sight (page load)', () => {
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-load',
          type: 'VERIFY_CHAIN_RANGE',
          status: 'SUCCEEDED',
        }),
        null,
      )?.jobId,
    ).toBe('j-load');
  });

  it('requires scope bounds', () => {
    expect(
      resolveIntegrityAsyncJobReportHydration(
        job({
          jobId: 'j-noscope',
          type: 'VERIFY_CHAIN_RANGE',
          status: 'SUCCEEDED',
          scopeFrom: undefined,
          scopeTo: undefined,
        }),
        null,
      ),
    ).toBeNull();
  });
});

describe('integrityExclusiveApiParamsToDisplayRange', () => {
  it('round-trips inclusive calendar bounds through exclusive Instant scope (America/New_York)', () => {
    const { createdAfter, createdBefore } = integrityExclusiveDateRangeToApiParams(
      '2026-10-01',
      '2026-10-07',
      'America/New_York',
    );
    expect(createdAfter).toBe('2026-10-01T04:00:00.000Z');
    expect(createdBefore).toBe('2026-10-08T04:00:00.000Z');

    expect(
      integrityExclusiveApiParamsToDisplayRange(
        createdAfter,
        createdBefore,
        'America/New_York',
      ),
    ).toEqual({ from: '2026-10-01', to: '2026-10-07' });
  });

  it('does not show exclusive end as +1 day across a timezone edge', () => {
    // Exclusive end is start of 8 Oct in NY (= 04:00Z). Naive UTC date-of would be 08.
    const display = integrityExclusiveApiParamsToDisplayRange(
      '2026-10-01T04:00:00.000Z',
      '2026-10-08T04:00:00.000Z',
      'America/New_York',
    );
    expect(display).toEqual({ from: '2026-10-01', to: '2026-10-07' });
    expect(display.to).not.toBe('2026-10-08');
  });
});

describe('executeIntegrityAsyncJobReportHydration + RUNNING→SUCCEEDED once', () => {
  it('fetches chain once with job scope and delivers undeclared gaps with correct display range', async () => {
    const fetchCalls: Array<{ from: string; to: string }> = [];
    const gapReport = {
      intact: false,
      status: 'UNDECLARED_GAP_DETECTED',
      totalCheckpoints: 10,
      validCheckpoints: 10,
      invalidCheckpoints: 0,
      archivedCheckpoints: 0,
      undeclaredGaps: [
        {
          gapStart: '2026-10-03T10:00:00Z',
          gapEnd: '2026-10-03T12:00:00Z',
          gapMinutes: 120,
        },
      ],
    } as ChainVerificationReport;

    const scopeFrom = '2026-10-01T04:00:00.000Z';
    const scopeTo = '2026-10-08T04:00:00.000Z';
    const deps = {
      fetchChainReport: vi.fn(async (from: string, to: string) => {
        fetchCalls.push({ from, to });
        return gapReport;
      }),
      fetchEntryReport: vi.fn(async () => {
        throw new Error('entry fetch must not run for chain hydration');
      }),
      toDisplayRange: (fromIso: string, toIso: string) =>
        integrityExclusiveApiParamsToDisplayRange(fromIso, toIso, 'America/New_York'),
      onChainReport: vi.fn(),
      onEntryReport: vi.fn(),
    };

    let lastHydrated: string | null = null;
    const running = job({
      jobId: 'j-transition',
      type: 'VERIFY_CHAIN_RANGE',
      status: 'RUNNING',
      scopeFrom,
      scopeTo,
    });
    expect(resolveIntegrityAsyncJobReportHydration(running, lastHydrated)).toBeNull();

    const succeeded = job({
      jobId: 'j-transition',
      type: 'VERIFY_CHAIN_RANGE',
      status: 'SUCCEEDED',
      scopeFrom,
      scopeTo,
    });
    const request = resolveIntegrityAsyncJobReportHydration(succeeded, lastHydrated);
    expect(request).not.toBeNull();

    lastHydrated = await executeIntegrityAsyncJobReportHydration(request!, deps);

    expect(fetchCalls).toEqual([{ from: scopeFrom, to: scopeTo }]);
    expect(deps.onChainReport).toHaveBeenCalledTimes(1);
    expect(deps.onChainReport).toHaveBeenCalledWith(gapReport, {
      from: '2026-10-01',
      to: '2026-10-07',
    });
    expect(
      (deps.onChainReport.mock.calls[0][0] as ChainVerificationReport & {
        undeclaredGaps: unknown[];
      }).undeclaredGaps,
    ).toHaveLength(1);
    expect(deps.onEntryReport).not.toHaveBeenCalled();

    expect(resolveIntegrityAsyncJobReportHydration(succeeded, lastHydrated)).toBeNull();
    expect(fetchCalls).toHaveLength(1);
  });

  it('fetches entry report once for VERIFY_ENTRY_HMAC_RANGE SUCCEEDED', async () => {
    const entryReport = {
      intact: false,
      totalEntries: 5,
      validEntries: 4,
      invalidEntries: 1,
    } as IntegrityReport;

    const deps = {
      fetchChainReport: vi.fn(async () => {
        throw new Error('chain fetch must not run for entry hydration');
      }),
      fetchEntryReport: vi.fn(async () => entryReport),
      toDisplayRange: (fromIso: string, toIso: string) =>
        integrityExclusiveApiParamsToDisplayRange(fromIso, toIso, 'UTC'),
      onChainReport: vi.fn(),
      onEntryReport: vi.fn(),
    };

    const request = resolveIntegrityAsyncJobReportHydration(
      job({
        jobId: 'j-hmac',
        type: 'VERIFY_ENTRY_HMAC_RANGE',
        status: 'SUCCEEDED',
        scopeFrom: '2026-10-01T00:00:00.000Z',
        scopeTo: '2026-10-08T00:00:00.000Z',
      }),
      null,
    );
    await executeIntegrityAsyncJobReportHydration(request!, deps);

    expect(deps.fetchEntryReport).toHaveBeenCalledTimes(1);
    expect(deps.onEntryReport).toHaveBeenCalledWith(entryReport, {
      from: '2026-10-01',
      to: '2026-10-07',
    });
    expect(deps.onChainReport).not.toHaveBeenCalled();
  });

  it('already-SUCCEEDED-on-load hydrates once', async () => {
    const fetchChainReport = vi.fn(async () => ({ intact: true }) as ChainVerificationReport);
    const deps = {
      fetchChainReport,
      fetchEntryReport: vi.fn(async () => ({ intact: true }) as IntegrityReport),
      toDisplayRange: (fromIso: string, toIso: string) =>
        integrityExclusiveApiParamsToDisplayRange(fromIso, toIso, 'UTC'),
      onChainReport: vi.fn(),
      onEntryReport: vi.fn(),
    };

    const succeededOnLoad = job({
      jobId: 'j-onload',
      type: 'VERIFY_CHAIN_RANGE',
      status: 'SUCCEEDED',
      scopeFrom: '2026-10-01T00:00:00.000Z',
      scopeTo: '2026-10-08T00:00:00.000Z',
    });

    let lastHydrated: string | null = null;
    const first = resolveIntegrityAsyncJobReportHydration(succeededOnLoad, lastHydrated);
    expect(first).not.toBeNull();
    lastHydrated = await executeIntegrityAsyncJobReportHydration(first!, deps);
    expect(fetchChainReport).toHaveBeenCalledTimes(1);

    const second = resolveIntegrityAsyncJobReportHydration(succeededOnLoad, lastHydrated);
    expect(second).toBeNull();
    expect(fetchChainReport).toHaveBeenCalledTimes(1);
  });
});
