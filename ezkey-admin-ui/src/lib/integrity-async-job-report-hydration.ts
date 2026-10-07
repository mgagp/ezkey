/**
 * After an Integrity async VERIFY_* job SUCCEEDS, the job DTO only carries a
 * resultSummary — not the full report the Remediate UI needs (undeclared gaps,
 * entry violations). Hydrate from the existing read-only verify endpoints using
 * the job's scope, once per jobId.
 *
 * @since 2026
 */

import type {
  ChainVerificationReport,
  IntegrityReport,
} from '@/generated/admin-api/model';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';

export type IntegrityReportHydrationKind = 'chain' | 'entry';

export interface IntegrityReportHydrationRequest {
  kind: IntegrityReportHydrationKind;
  jobId: string;
  /** API Instant bounds (same as job.scopeFrom / job.scopeTo). */
  from: string;
  to: string;
}

/**
 * Decide whether the current async job should trigger a one-shot report fetch.
 *
 * @param job current Integrity async job (from banner poll or page load)
 * @param lastHydratedJobId jobId already hydrated in this session (dedupe)
 * @returns request to fetch, or null when no hydration is needed
 */
export function resolveIntegrityAsyncJobReportHydration(
  job: IntegrityAsyncJobResponse | null | undefined,
  lastHydratedJobId: string | null,
): IntegrityReportHydrationRequest | null {
  if (job == null || job.abandonedAt != null) {
    return null;
  }
  if (job.status !== 'SUCCEEDED') {
    return null;
  }
  if (job.jobId === lastHydratedJobId) {
    return null;
  }
  if (!job.scopeFrom || !job.scopeTo) {
    return null;
  }
  if (job.type === 'VERIFY_CHAIN_RANGE') {
    return {
      kind: 'chain',
      jobId: job.jobId,
      from: job.scopeFrom,
      to: job.scopeTo,
    };
  }
  if (job.type === 'VERIFY_ENTRY_HMAC_RANGE') {
    return {
      kind: 'entry',
      jobId: job.jobId,
      from: job.scopeFrom,
      to: job.scopeTo,
    };
  }
  return null;
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
