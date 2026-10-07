/**
 * Hydrates chain / entry integrity reports when an async VERIFY_* job SUCCEEDS.
 *
 * @since 2026
 */

import { useEffect, useRef } from 'react';
import {
  checkChainIntegrity,
  checkIntegrity,
} from '@/generated/admin-api/audit-logs/audit-logs';
import type {
  ChainVerificationReport,
  IntegrityReport,
} from '@/generated/admin-api/model';
import {
  executeIntegrityAsyncJobReportHydration,
  resolveIntegrityAsyncJobReportHydration,
} from '@/lib/integrity-async-job-report-hydration';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';

export interface UseIntegrityAsyncJobReportHydrationOptions {
  job: IntegrityAsyncJobResponse | null;
  onChainReport: (
    report: ChainVerificationReport,
    range: { from: string; to: string },
  ) => void;
  onEntryReport: (
    report: IntegrityReport,
    range: { from: string; to: string },
  ) => void;
  /** Optional loading flags while the follow-up report fetch is in flight. */
  onChainHydratingChange?: (loading: boolean) => void;
  onEntryHydratingChange?: (loading: boolean) => void;
  onError?: (kind: 'chain' | 'entry', error: unknown) => void;
  /** Convert job Instant scope to UI date-range display values (YYYY-MM-DD). */
  toDisplayRange: (fromIso: string, toIso: string) => { from: string; to: string };
}

/**
 * When {@code job} is SUCCEEDED VERIFY_CHAIN_RANGE / VERIFY_ENTRY_HMAC_RANGE,
 * fetch the full report once per jobId and push it into page state.
 *
 * @param options job + setters / error handlers
 */
export function useIntegrityAsyncJobReportHydration(
  options: UseIntegrityAsyncJobReportHydrationOptions,
): void {
  const {
    job,
    onChainReport,
    onEntryReport,
    onChainHydratingChange,
    onEntryHydratingChange,
    onError,
    toDisplayRange,
  } = options;

  const lastHydratedJobIdRef = useRef<string | null>(null);
  // Keep latest callbacks without re-firing hydration on every render identity change.
  const callbacksRef = useRef({
    onChainReport,
    onEntryReport,
    onChainHydratingChange,
    onEntryHydratingChange,
    onError,
    toDisplayRange,
  });
  callbacksRef.current = {
    onChainReport,
    onEntryReport,
    onChainHydratingChange,
    onEntryHydratingChange,
    onError,
    toDisplayRange,
  };

  useEffect(() => {
    const request = resolveIntegrityAsyncJobReportHydration(
      job,
      lastHydratedJobIdRef.current,
    );
    if (request == null) {
      return;
    }

    let cancelled = false;
    const setLoading =
      request.kind === 'chain'
        ? callbacksRef.current.onChainHydratingChange
        : callbacksRef.current.onEntryHydratingChange;
    setLoading?.(true);

    void (async () => {
      try {
        const hydratedJobId = await executeIntegrityAsyncJobReportHydration(request, {
          fetchChainReport: async (from, to) =>
            (await checkChainIntegrity({ from, to })) as unknown as ChainVerificationReport,
          fetchEntryReport: async (from, to) =>
            (await checkIntegrity({ from, to })) as unknown as IntegrityReport,
          toDisplayRange: callbacksRef.current.toDisplayRange,
          onChainReport: (report, range) => {
            if (!cancelled) {
              callbacksRef.current.onChainReport(report, range);
            }
          },
          onEntryReport: (report, range) => {
            if (!cancelled) {
              callbacksRef.current.onEntryReport(report, range);
            }
          },
        });
        if (!cancelled) {
          lastHydratedJobIdRef.current = hydratedJobId;
        }
      } catch (error) {
        if (!cancelled) {
          callbacksRef.current.onError?.(request.kind, error);
        }
      } finally {
        if (!cancelled) {
          setLoading?.(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [job]);
}
