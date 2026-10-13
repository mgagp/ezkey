/**
 * After an Integrity async VERIFY_* job SUCCEEDS, hydrate the detailed report
 * (undeclared gap list, entry violations) from read-only verify endpoints when
 * needed. Integrity cut 3: the micro-dashboard glance uses DTO counters — skip
 * the sync GET on open when {@link hasJobBackedChainCounters} is true unless the
 * operator forces a reload or started a verify in this session.
 *
 * @since 2026
 */

import type {
  ChainVerificationReport,
  IntegrityReport,
} from '@/generated/admin-api/model';
import { ApiError } from '@/lib/api-client';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';
import { hasJobBackedChainCounters } from '@/lib/integrity-micro-dashboard';

/** RFC 9457 type for Integrity async slot / heavy-crypto gate busy (HTTP 409). */
export const INTEGRITY_ASYNC_JOB_BUSY_TYPE =
  'https://ezkey.io/problems/domain/integrity-async-job-busy';

/** RFC 9457 type for Integrity window over the shared hour cap (HTTP 400). */
export const INTEGRITY_WINDOW_OVER_CAP_TYPE =
  'https://ezkey.io/problems/domain/integrity-window-over-cap';

/**
 * Mirrors server {@code ezkey.audit.integrity.max-sync-window-days} (default 8):
 * Instant span = days×24h+1h for DST fall-back. Soft client check before sync
 * report GETs; async VERIFY uses the separate {@code max-window-days} (default 92).
 */
export const INTEGRITY_REPORT_MAX_SYNC_WINDOW_DAYS = 8;

/** Effective hours for {@link INTEGRITY_REPORT_MAX_SYNC_WINDOW_DAYS} (8×24 + 1). */
export const INTEGRITY_REPORT_MAX_WINDOW_HOURS =
  INTEGRITY_REPORT_MAX_SYNC_WINDOW_DAYS * 24 + 1;

/** Sentinel when hydration skips the report GET because the job scope is over the cap. */
export class IntegrityReportHydrationOverCapError extends Error {
  constructor() {
    super('Integrity report scope exceeds verify-report max window');
    this.name = 'IntegrityReportHydrationOverCapError';
  }
}

/**
 * True when Instant bounds exceed the synchronous report / VERIFY window cap.
 *
 * @param fromIso inclusive Instant (ISO-8601)
 * @param toIso exclusive Instant (ISO-8601)
 * @param maxHours cap in hours (default shared limit)
 * @returns whether the window is over the cap
 */
export function isIntegrityReportScopeOverCap(
  fromIso: string,
  toIso: string,
  maxHours: number = INTEGRITY_REPORT_MAX_WINDOW_HOURS,
): boolean {
  const fromMs = new Date(fromIso).getTime();
  const toMs = new Date(toIso).getTime();
  if (Number.isNaN(fromMs) || Number.isNaN(toMs) || toMs <= fromMs) {
    return false;
  }
  return (toMs - fromMs) / 3_600_000 > maxHours;
}

/**
 * True when a report GET failed because the heavy-crypto gate / async slot is busy.
 * Hydration must not retry-storm; the operator reloads once the gate is free.
 *
 * @param error unknown error from the report GET
 * @returns whether this is the shared Integrity busy contract
 */
export function isIntegrityReportHydrationBusyError(error: unknown): boolean {
  if (!(error instanceof ApiError) || error.status !== 409) {
    return false;
  }
  const type = error.problemDetail?.type;
  return type === INTEGRITY_ASYNC_JOB_BUSY_TYPE;
}

/**
 * True when the API refused a window that exceeds the shared Integrity hour cap (HTTP 400).
 * Matches {@link INTEGRITY_WINDOW_OVER_CAP_TYPE} only (not free-text detail).
 *
 * @param error unknown error from start or report GET
 * @returns whether this is an over-cap refusal
 */
export function isIntegrityWindowOverCapError(error: unknown): boolean {
  if (!(error instanceof ApiError) || error.status !== 400) {
    return false;
  }
  return error.problemDetail?.type === INTEGRITY_WINDOW_OVER_CAP_TYPE;
}

export type IntegrityReportHydrationKind = 'chain' | 'entry';

export interface IntegrityReportHydrationRequest {
  kind: IntegrityReportHydrationKind;
  jobId: string;
  /** API Instant bounds (same as job.scopeFrom / job.scopeTo). */
  from: string;
  to: string;
}

/**
 * True when the current job is a finished verify that should hydrate a full report
 * (and should not trigger Integrity page auto-run of a new chain job).
 *
 * @param job current Integrity async job
 * @returns whether the job is a succeeded chain/entry verify still owning the slot
 */
export function isSucceededVerifyJobForReportHydration(
  job: IntegrityAsyncJobResponse | null | undefined,
): boolean {
  if (job == null || job.abandonedAt != null) {
    return false;
  }
  if (job.status !== 'SUCCEEDED') {
    return false;
  }
  return (
    job.type === 'VERIFY_CHAIN_RANGE' || job.type === 'VERIFY_ENTRY_HMAC_RANGE'
  );
}

/** Options for {@link resolveIntegrityAsyncJobReportHydration}. */
export type ResolveIntegrityAsyncJobReportHydrationOptions = {
  /**
   * When true, fetch the detailed report even if the DTO already has counters
   * (Reload report, or a verify started in this browser session).
   */
  forceReportGet?: boolean;
};

/**
 * Decide whether the current async job should trigger a one-shot report fetch.
 *
 * @param job current Integrity async job (from banner poll or page load)
 * @param lastHydratedJobId jobId already hydrated (or claimed in-flight) in this session
 * @param options force report GET when reloading or after an in-session start
 * @returns request to fetch, or null when no hydration is needed
 */
export function resolveIntegrityAsyncJobReportHydration(
  job: IntegrityAsyncJobResponse | null | undefined,
  lastHydratedJobId: string | null,
  options: ResolveIntegrityAsyncJobReportHydrationOptions = {},
): IntegrityReportHydrationRequest | null {
  if (!isSucceededVerifyJobForReportHydration(job)) {
    return null;
  }
  // Narrowed by the predicate above.
  const succeeded = job as IntegrityAsyncJobResponse;
  if (succeeded.jobId === lastHydratedJobId) {
    return null;
  }
  if (!succeeded.scopeFrom || !succeeded.scopeTo) {
    return null;
  }
  // Cut 3 job-backed glance: skip sync GET on open when counters are on the DTO.
  if (
    !options.forceReportGet
    && hasJobBackedChainCounters(succeeded)
  ) {
    return null;
  }
  if (succeeded.type === 'VERIFY_CHAIN_RANGE') {
    return {
      kind: 'chain',
      jobId: succeeded.jobId,
      from: succeeded.scopeFrom,
      to: succeeded.scopeTo,
    };
  }
  return {
    kind: 'entry',
    jobId: succeeded.jobId,
    from: succeeded.scopeFrom,
    to: succeeded.scopeTo,
  };
}

export interface IntegrityReportHydrationDeps {
  fetchChainReport: (from: string, to: string) => Promise<ChainVerificationReport>;
  fetchEntryReport: (from: string, to: string) => Promise<IntegrityReport>;
  /** Convert job Instant scope to UI date-range display values (YYYY-MM-DD). */
  toDisplayRange: (fromIso: string, toIso: string) => { from: string; to: string };
  onChainReport: (
    report: ChainVerificationReport,
    range: { from: string; to: string },
  ) => void;
  onEntryReport: (
    report: IntegrityReport,
    range: { from: string; to: string },
  ) => void;
}

/**
 * Execute a resolved hydration request. Returns the jobId on success so callers
 * can mark it hydrated even if a later poll repeats the same SUCCEEDED job.
 *
 * @param request hydration request from {@link resolveIntegrityAsyncJobReportHydration}
 * @param deps fetchers and report setters
 * @returns hydrated jobId
 */
export async function executeIntegrityAsyncJobReportHydration(
  request: IntegrityReportHydrationRequest,
  deps: IntegrityReportHydrationDeps,
): Promise<string> {
  const range = deps.toDisplayRange(request.from, request.to);
  if (request.kind === 'chain') {
    const report = await deps.fetchChainReport(request.from, request.to);
    deps.onChainReport(report, range);
  } else {
    const report = await deps.fetchEntryReport(request.from, request.to);
    deps.onEntryReport(report, range);
  }
  return request.jobId;
}
