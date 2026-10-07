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
  /**
   * Bump to clear the in-session hydrated mark and re-fetch for the current
   * SUCCEEDED job (operator “Reload report”).
   */
  reloadNonce?: number;
  /** Test seams — default to generated Admin API clients. */
  fetchChainReport?: (from: string, to: string) => Promise<ChainVerificationReport>;
  fetchEntryReport?: (from: string, to: string) => Promise<IntegrityReport>;
}

/**
 * When {@code job} is SUCCEEDED VERIFY_CHAIN_RANGE / VERIFY_ENTRY_HMAC_RANGE,
 * fetch the full report once per jobId and push it into page state.
 *
 * Effect deps are {@code jobId} + {@code status} (not the job object) so banner
 * and auto-run setting distinct object copies do not double-fetch.
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
    reloadNonce = 0,
    fetchChainReport,
    fetchEntryReport,
  } = options;

  const jobId = job?.jobId;
  const jobStatus = job?.status;

  const lastHydratedJobIdRef = useRef<string | null>(null);
  const completedJobIdRef = useRef<string | null>(null);
  // Keep latest callbacks / job snapshot without re-firing on identity churn.
  const callbacksRef = useRef({
    job,
    onChainReport,
    onEntryReport,
    onChainHydratingChange,
    onEntryHydratingChange,
    onError,
    toDisplayRange,
    fetchChainReport,
    fetchEntryReport,
  });
  callbacksRef.current = {
    job,
    onChainReport,
    onEntryReport,
    onChainHydratingChange,
    onEntryHydratingChange,
    onError,
    toDisplayRange,
    fetchChainReport,
    fetchEntryReport,
  };

  useEffect(() => {
    if (reloadNonce > 0) {
      lastHydratedJobIdRef.current = null;
      completedJobIdRef.current = null;
    }

    const request = resolveIntegrityAsyncJobReportHydration(
      callbacksRef.current.job,
      lastHydratedJobIdRef.current,
    );
    if (request == null) {
      return;
    }

    // Claim before the async fetch so a concurrent resolve (or Strict Mode) cannot
    // start a second GET for the same jobId.
    lastHydratedJobIdRef.current = request.jobId;

    let cancelled = false;
    const setLoading =
      request.kind === 'chain'
        ? callbacksRef.current.onChainHydratingChange
        : callbacksRef.current.onEntryHydratingChange;
    setLoading?.(true);

    const fetchChain =
      callbacksRef.current.fetchChainReport
      ?? (async (from: string, to: string) =>
        (await checkChainIntegrity({ from, to })) as unknown as ChainVerificationReport);
    const fetchEntry =
      callbacksRef.current.fetchEntryReport
      ?? (async (from: string, to: string) =>
        (await checkIntegrity({ from, to })) as unknown as IntegrityReport);

    void (async () => {
      try {
        await executeIntegrityAsyncJobReportHydration(request, {
          fetchChainReport: fetchChain,
          fetchEntryReport: fetchEntry,
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
          completedJobIdRef.current = request.jobId;
        }
      } catch (error) {
        if (!cancelled) {
          // Release claim so Reload / retry can fetch again.
          if (lastHydratedJobIdRef.current === request.jobId) {
            lastHydratedJobIdRef.current = null;
          }
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
      // Cancelled mid-flight (new job / unmount): clear loading so Verify buttons
      // are not stuck disabled. Do not rely on the async finally — it skips when cancelled.
      setLoading?.(false);
      // Dev Strict Mode remounts this effect: release an incomplete in-flight claim
      // so the remount can start its own fetch (two GETs in development Strict Mode).
      // Production sees a single mount / single GET for a given jobId+status.
      if (
        lastHydratedJobIdRef.current === request.jobId
        && completedJobIdRef.current !== request.jobId
      ) {
        lastHydratedJobIdRef.current = null;
      }
    };
  }, [jobId, jobStatus, reloadNonce]);
}
