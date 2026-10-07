import { describe, expect, it, vi } from 'vitest';
import type {
  ChainVerificationReport,
  IntegrityReport,
} from '@/generated/admin-api/model';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';
import {
  executeIntegrityAsyncJobReportHydration,
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

  it('dedupes by jobId (already hydrated)', () => {
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

describe('executeIntegrityAsyncJobReportHydration + RUNNING→SUCCEEDED once', () => {
  it('fetches chain once with job scope and delivers undeclared gaps', async () => {
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

    const deps = {
      fetchChainReport: vi.fn(async (from: string, to: string) => {
        fetchCalls.push({ from, to });
        return gapReport;
      }),
      fetchEntryReport: vi.fn(async () => {
        throw new Error('entry fetch must not run for chain hydration');
      }),
      toDisplayRange: (fromIso: string, toIso: string) => ({
        from: fromIso.slice(0, 10),
        to: toIso.slice(0, 10),
      }),
      onChainReport: vi.fn(),
      onEntryReport: vi.fn(),
    };

    let lastHydrated: string | null = null;
    const running = job({
      jobId: 'j-transition',
      type: 'VERIFY_CHAIN_RANGE',
      status: 'RUNNING',
    });
    expect(resolveIntegrityAsyncJobReportHydration(running, lastHydrated)).toBeNull();

    const succeeded = job({
      jobId: 'j-transition',
      type: 'VERIFY_CHAIN_RANGE',
      status: 'SUCCEEDED',
    });
    const request = resolveIntegrityAsyncJobReportHydration(succeeded, lastHydrated);
    expect(request).not.toBeNull();

    lastHydrated = await executeIntegrityAsyncJobReportHydration(request!, deps);

    expect(fetchCalls).toEqual([
      { from: '2026-10-01T00:00:00Z', to: '2026-10-08T00:00:00Z' },
    ]);
    expect(deps.onChainReport).toHaveBeenCalledTimes(1);
    expect(deps.onChainReport).toHaveBeenCalledWith(gapReport, {
      from: '2026-10-01',
      to: '2026-10-08',
    });
    expect(
      (deps.onChainReport.mock.calls[0][0] as ChainVerificationReport & {
        undeclaredGaps: unknown[];
      }).undeclaredGaps,
    ).toHaveLength(1);
    expect(deps.onEntryReport).not.toHaveBeenCalled();

    // Same SUCCEEDED job again — no second fetch
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
      toDisplayRange: () => ({ from: '2026-10-01', to: '2026-10-08' }),
      onChainReport: vi.fn(),
      onEntryReport: vi.fn(),
    };

    const request = resolveIntegrityAsyncJobReportHydration(
      job({
        jobId: 'j-hmac',
        type: 'VERIFY_ENTRY_HMAC_RANGE',
        status: 'SUCCEEDED',
      }),
      null,
    );
    await executeIntegrityAsyncJobReportHydration(request!, deps);

    expect(deps.fetchEntryReport).toHaveBeenCalledTimes(1);
    expect(deps.fetchEntryReport).toHaveBeenCalledWith(
      '2026-10-01T00:00:00Z',
      '2026-10-08T00:00:00Z',
    );
    expect(deps.onEntryReport).toHaveBeenCalledWith(entryReport, {
      from: '2026-10-01',
      to: '2026-10-08',
    });
    expect(deps.onChainReport).not.toHaveBeenCalled();
  });

  it('already-SUCCEEDED-on-load hydrates once', async () => {
    const fetchChainReport = vi.fn(async () => ({ intact: true }) as ChainVerificationReport);
    const deps = {
      fetchChainReport,
      fetchEntryReport: vi.fn(async () => ({ intact: true }) as IntegrityReport),
      toDisplayRange: () => ({ from: '2026-10-01', to: '2026-10-08' }),
      onChainReport: vi.fn(),
      onEntryReport: vi.fn(),
    };

    const succeededOnLoad = job({
      jobId: 'j-onload',
      type: 'VERIFY_CHAIN_RANGE',
      status: 'SUCCEEDED',
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
