/** @vitest-environment jsdom */

import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type {
  ChainVerificationReport,
  IntegrityReport,
} from '@/generated/admin-api/model';
import { ApiError } from '@/lib/api-client';
import { INTEGRITY_ASYNC_JOB_BUSY_TYPE } from '@/lib/integrity-async-job-report-hydration';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';
import { useIntegrityAsyncJobReportHydration } from './use-integrity-async-job-report-hydration';

function job(
  overrides: Partial<IntegrityAsyncJobResponse> &
    Pick<IntegrityAsyncJobResponse, 'jobId' | 'type' | 'status'>,
): IntegrityAsyncJobResponse {
  return {
    scopeFrom: '2026-10-01T04:00:00.000Z',
    scopeTo: '2026-10-08T04:00:00.000Z',
    ...overrides,
  };
}

describe('useIntegrityAsyncJobReportHydration', () => {
  it('does not fetch while RUNNING, then fetches once on SUCCEEDED for the same jobId', async () => {
    const fetchChainReport = vi.fn(
      async () => ({ intact: true, undeclaredGaps: [] }) as ChainVerificationReport,
    );
    const onChainReport = vi.fn();

    const base = {
      onChainReport,
      onEntryReport: vi.fn(),
      onChainHydratingChange: vi.fn(),
      toDisplayRange: () => ({ from: '2026-10-01', to: '2026-10-07' }),
      fetchChainReport,
      fetchEntryReport: vi.fn(async () => ({ intact: true }) as IntegrityReport),
    };

    const { rerender } = renderHook(
      ({ job: current }: { job: IntegrityAsyncJobResponse | null }) =>
        useIntegrityAsyncJobReportHydration({ ...base, job: current }),
      {
        initialProps: {
          job: job({
            jobId: 'j-transition',
            type: 'VERIFY_CHAIN_RANGE',
            status: 'RUNNING',
          }),
        },
      },
    );

    await act(async () => {
      await Promise.resolve();
    });
    expect(fetchChainReport).not.toHaveBeenCalled();

    rerender({
      job: job({
        jobId: 'j-transition',
        type: 'VERIFY_CHAIN_RANGE',
        status: 'SUCCEEDED',
      }),
    });

    await waitFor(() => expect(fetchChainReport).toHaveBeenCalledTimes(1));
    expect(onChainReport).toHaveBeenCalledTimes(1);
  });

  it('fetches exactly once for two successive distinct objects of the same job', async () => {
    const fetchChainReport = vi.fn(
      async () => ({ intact: true, undeclaredGaps: [] }) as ChainVerificationReport,
    );
    const onChainReport = vi.fn();
    const onChainHydratingChange = vi.fn();

    const base = {
      onChainReport,
      onEntryReport: vi.fn(),
      onChainHydratingChange,
      toDisplayRange: () => ({ from: '2026-10-01', to: '2026-10-07' }),
      fetchChainReport,
      fetchEntryReport: vi.fn(async () => ({ intact: true }) as IntegrityReport),
    };

    const { rerender } = renderHook(
      ({ job: current }: { job: IntegrityAsyncJobResponse | null }) =>
        useIntegrityAsyncJobReportHydration({ ...base, job: current }),
      {
        initialProps: {
          job: job({
            jobId: 'j-same',
            type: 'VERIFY_CHAIN_RANGE',
            status: 'SUCCEEDED',
            resultSummary: 'first object',
          }),
        },
      },
    );

    await waitFor(() => expect(fetchChainReport).toHaveBeenCalledTimes(1));

    rerender({
      job: job({
        jobId: 'j-same',
        type: 'VERIFY_CHAIN_RANGE',
        status: 'SUCCEEDED',
        resultSummary: 'second object — banner vs auto-run',
      }),
    });

    // Allow any accidental second effect tick to surface.
    await act(async () => {
      await Promise.resolve();
    });
    expect(fetchChainReport).toHaveBeenCalledTimes(1);
    expect(onChainReport).toHaveBeenCalledTimes(1);
  });

  it('resets chainLoading when cancelled by a new entry job mid-hydration', async () => {
    let resolveChain!: (value: ChainVerificationReport) => void;
    const chainPromise = new Promise<ChainVerificationReport>((resolve) => {
      resolveChain = resolve;
    });
    const fetchChainReport = vi.fn(async () => chainPromise);
    const onChainHydratingChange = vi.fn();
    const onEntryHydratingChange = vi.fn();

    const base = {
      onChainReport: vi.fn(),
      onEntryReport: vi.fn(),
      onChainHydratingChange,
      onEntryHydratingChange,
      toDisplayRange: () => ({ from: '2026-10-01', to: '2026-10-07' }),
      fetchChainReport,
      fetchEntryReport: vi.fn(async () => ({ intact: true }) as IntegrityReport),
    };

    const { rerender } = renderHook(
      ({ job: current }: { job: IntegrityAsyncJobResponse | null }) =>
        useIntegrityAsyncJobReportHydration({ ...base, job: current }),
      {
        initialProps: {
          job: job({
            jobId: 'j-chain',
            type: 'VERIFY_CHAIN_RANGE',
            status: 'SUCCEEDED',
          }),
        },
      },
    );

    await waitFor(() => expect(onChainHydratingChange).toHaveBeenCalledWith(true));

    // Operator starts Verify entry — slot shows RUNNING entry job.
    rerender({
      job: job({
        jobId: 'j-entry-run',
        type: 'VERIFY_ENTRY_HMAC_RANGE',
        status: 'RUNNING',
      }),
    });

    expect(onChainHydratingChange).toHaveBeenCalledWith(false);

    // Late resolution of the cancelled chain fetch must not re-stick loading.
    await act(async () => {
      resolveChain({ intact: true, undeclaredGaps: [] } as ChainVerificationReport);
      await Promise.resolve();
    });
    const chainLoadingCalls = onChainHydratingChange.mock.calls.map((c) => c[0]);
    expect(chainLoadingCalls[chainLoadingCalls.length - 1]).toBe(false);
  });

  it('surfaces busy 409 once without retry storm and allows reload', async () => {
    const busy = new ApiError(
      409,
      {
        type: INTEGRITY_ASYNC_JOB_BUSY_TYPE,
        title: 'Integrity async slot busy',
        status: 409,
        detail: 'Integrity crypto path busy (scheduled or in-process heavy work)',
      },
      'Integrity crypto path busy (scheduled or in-process heavy work)',
    );
    const successReport = {
      intact: true,
      undeclaredGaps: [{ gapStart: 'a', gapEnd: 'b' }],
    } as ChainVerificationReport;
    let shouldFail = true;
    const fetchChainReport = vi.fn(
      async (_from: string, _to: string): Promise<ChainVerificationReport> => {
        if (shouldFail) {
          throw busy;
        }
        return successReport;
      },
    );
    const onError = vi.fn();
    const onChainReport = vi.fn();
    const onChainHydratingChange = vi.fn();

    const base = {
      onChainReport,
      onEntryReport: vi.fn(),
      onChainHydratingChange,
      onError,
      toDisplayRange: () => ({ from: '2026-10-01', to: '2026-10-07' }),
      fetchChainReport,
      fetchEntryReport: vi.fn(async () => ({ intact: true }) as IntegrityReport),
    };

    const { rerender } = renderHook(
      ({
        job: current,
        reloadNonce,
      }: {
        job: IntegrityAsyncJobResponse | null;
        reloadNonce: number;
      }) =>
        useIntegrityAsyncJobReportHydration({
          ...base,
          job: current,
          reloadNonce,
        }),
      {
        initialProps: {
          job: job({
            jobId: 'j-busy',
            type: 'VERIFY_CHAIN_RANGE',
            status: 'SUCCEEDED',
          }),
          reloadNonce: 0,
        },
      },
    );

    await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(fetchChainReport).toHaveBeenCalledTimes(1);
    expect(onChainHydratingChange).toHaveBeenCalledWith(false);

    // Banner poll with a new object of the same SUCCEEDED job must not retry.
    rerender({
      job: job({
        jobId: 'j-busy',
        type: 'VERIFY_CHAIN_RANGE',
        status: 'SUCCEEDED',
        resultSummary: 'poll tick',
      }),
      reloadNonce: 0,
    });
    await act(async () => {
      await Promise.resolve();
    });
    expect(fetchChainReport).toHaveBeenCalledTimes(1);

    // Operator Reload report bumps nonce — one more attempt, still no storm.
    shouldFail = false;
    rerender({
      job: job({
        jobId: 'j-busy',
        type: 'VERIFY_CHAIN_RANGE',
        status: 'SUCCEEDED',
      }),
      reloadNonce: 1,
    });
    await waitFor(() => expect(fetchChainReport).toHaveBeenCalledTimes(2));
    expect(onChainReport).toHaveBeenCalledTimes(1);
  });

  it('resets integrityLoading when cancelled by a new chain job mid-hydration', async () => {
    let resolveEntry!: (value: IntegrityReport) => void;
    const entryPromise = new Promise<IntegrityReport>((resolve) => {
      resolveEntry = resolve;
    });
    const fetchEntryReport = vi.fn(async () => entryPromise);
    const onEntryHydratingChange = vi.fn();
    const onChainHydratingChange = vi.fn();

    const base = {
      onChainReport: vi.fn(),
      onEntryReport: vi.fn(),
      onChainHydratingChange,
      onEntryHydratingChange,
      toDisplayRange: () => ({ from: '2026-10-01', to: '2026-10-07' }),
      fetchChainReport: vi.fn(async () => ({ intact: true }) as ChainVerificationReport),
      fetchEntryReport,
    };

    const { rerender } = renderHook(
      ({ job: current }: { job: IntegrityAsyncJobResponse | null }) =>
        useIntegrityAsyncJobReportHydration({ ...base, job: current }),
      {
        initialProps: {
          job: job({
            jobId: 'j-entry',
            type: 'VERIFY_ENTRY_HMAC_RANGE',
            status: 'SUCCEEDED',
          }),
        },
      },
    );

    await waitFor(() => expect(onEntryHydratingChange).toHaveBeenCalledWith(true));

    // Page-load auto-run (or operator Verify chain) starts a new chain job.
    rerender({
      job: job({
        jobId: 'j-chain-run',
        type: 'VERIFY_CHAIN_RANGE',
        status: 'RUNNING',
      }),
    });

    expect(onEntryHydratingChange).toHaveBeenCalledWith(false);

    await act(async () => {
      resolveEntry({ intact: true } as IntegrityReport);
      await Promise.resolve();
    });
    const entryLoadingCalls = onEntryHydratingChange.mock.calls.map((c) => c[0]);
    expect(entryLoadingCalls[entryLoadingCalls.length - 1]).toBe(false);
  });
});
