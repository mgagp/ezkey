/**
 * Integrity cut 3 — micro-dashboard helpers (D1 freshness, DTO glance, scope mismatch).
 *
 * @since 2026
 */

import type {
  IntegrityAsyncJobResponse,
  IntegrityBusyHolder,
  IntegrityChainStatus,
} from '@/lib/integrity-async-jobs';
import { INTEGRITY_REPORT_MAX_SYNC_WINDOW_DAYS } from '@/lib/integrity-async-job-report-hydration';

/** Stale / auto-run freshness threshold (nightly + #676 default). */
export const INTEGRITY_VERDICT_FRESH_MS = 24 * 60 * 60 * 1000;

/** Synthetic nightly busy row uses the zero UUID (PR 1). */
export const INTEGRITY_SYNTHETIC_NIGHTLY_JOB_ID =
  '00000000-0000-0000-0000-000000000000';

/** Instant span hours for a days cap (days×24h + 1h DST fall-back). */
export function integrityWindowHoursFromDays(days: number): number {
  return days * 24 + 1;
}

/**
 * Resolve async VERIFY window hours from bootstrap days (default 92).
 *
 * @param maxWindowDays bootstrap maxWindowDays
 * @returns Instant-span hours
 */
export function resolveAsyncMaxWindowHours(
  maxWindowDays: number | null | undefined,
): number {
  const days =
    typeof maxWindowDays === 'number' && maxWindowDays >= 1
      ? maxWindowDays
      : 92;
  return integrityWindowHoursFromDays(days);
}

/**
 * Resolve sync report-GET window hours from bootstrap days (default 8).
 *
 * @param maxSyncWindowDays bootstrap maxSyncWindowDays
 * @returns Instant-span hours
 */
export function resolveSyncMaxWindowHours(
  maxSyncWindowDays: number | null | undefined,
): number {
  const days =
    typeof maxSyncWindowDays === 'number' && maxSyncWindowDays >= 1
      ? maxSyncWindowDays
      : INTEGRITY_REPORT_MAX_SYNC_WINDOW_DAYS;
  return integrityWindowHoursFromDays(days);
}

/**
 * Age reference for a verdict: finishedAt, else startedAt.
 *
 * @param job async job or last verdict
 * @returns ISO timestamp or null
 */
export function integrityVerdictFinishedAt(
  job: IntegrityAsyncJobResponse | null | undefined,
): string | null {
  if (job == null) {
    return null;
  }
  return job.finishedAt ?? job.startedAt ?? null;
}

/**
 * True when the verdict finished less than 24 h ago (OQ2 — age of finish, no scope arithmetic).
 *
 * @param job last verdict
 * @param nowMs optional clock
 * @returns whether the verdict is fresh
 */
export function isFreshIntegrityVerdict(
  job: IntegrityAsyncJobResponse | null | undefined,
  nowMs: number = Date.now(),
): boolean {
  const at = integrityVerdictFinishedAt(job);
  if (at == null) {
    return false;
  }
  const finishedMs = new Date(at).getTime();
  if (Number.isNaN(finishedMs)) {
    return false;
  }
  return nowMs - finishedMs < INTEGRITY_VERDICT_FRESH_MS;
}

/**
 * True when a terminal verdict is older than 24 h (stale glance — grey, never green).
 *
 * @param job last verdict
 * @param nowMs optional clock
 * @returns whether the verdict is stale
 */
export function isStaleIntegrityVerdict(
  job: IntegrityAsyncJobResponse | null | undefined,
  nowMs: number = Date.now(),
): boolean {
  if (job == null || job.status === 'RUNNING') {
    return false;
  }
  if (
    job.status !== 'SUCCEEDED'
    && job.status !== 'FAILED'
    && job.status !== 'EXPIRED'
    && job.status !== 'CANCELLED'
    && job.status !== 'INTERRUPTED'
  ) {
    return false;
  }
  const at = integrityVerdictFinishedAt(job);
  if (at == null) {
    return false;
  }
  const finishedMs = new Date(at).getTime();
  if (Number.isNaN(finishedMs)) {
    return false;
  }
  return nowMs - finishedMs >= INTEGRITY_VERDICT_FRESH_MS;
}

/**
 * True when GET /jobs/current is the synthetic nightly busy holder.
 *
 * @param job current job
 * @returns whether nightly holds the gate
 */
export function isSyntheticNightlyBusyJob(
  job: IntegrityAsyncJobResponse | null | undefined,
): boolean {
  if (job == null || job.status !== 'RUNNING') {
    return false;
  }
  if (job.busyHolder === 'NIGHTLY') {
    return true;
  }
  if (job.trigger === 'NIGHTLY') {
    return true;
  }
  return job.jobId === INTEGRITY_SYNTHETIC_NIGHTLY_JOB_ID;
}

/**
 * True when the job DTO carries structured chain counters (job-backed glance — skip report GET).
 *
 * @param job succeeded verify job
 * @returns whether micro-dashboard / open can hydrate without GET /chain-integrity
 */
export function hasJobBackedChainCounters(
  job: IntegrityAsyncJobResponse | null | undefined,
): boolean {
  if (job == null || job.type !== 'VERIFY_CHAIN_RANGE') {
    return false;
  }
  return job.chainStatus != null;
}

/**
 * Non-green for atelier default-tab from job DTO (open-time Remédier without report GET).
 *
 * @param job last verdict
 * @returns whether chain status is non-green
 */
export function isIntegrityJobChainNonGreen(
  job: IntegrityAsyncJobResponse | null | undefined,
): boolean {
  if (job == null || job.status !== 'SUCCEEDED') {
    return false;
  }
  if (job.intact === false) {
    return true;
  }
  const status = job.chainStatus;
  if (
    status === 'UNDECLARED_GAP_DETECTED'
    || status === 'CHAIN_INTEGRITY_VIOLATION_DETECTED'
  ) {
    return true;
  }
  if ((job.undeclaredGaps ?? 0) > 0) {
    return true;
  }
  if ((job.invalidCheckpoints ?? 0) > 0) {
    return true;
  }
  if ((job.entryHmacViolations ?? 0) > 0) {
    return true;
  }
  return false;
}

/** Glance verdict tone for the micro-dashboard tile. */
export type IntegrityGlanceTone =
  | 'running'
  | 'nightly'
  | 'intact'
  | 'gaps'
  | 'violation'
  | 'did-not-complete'
  | 'stale'
  | 'idle';

/**
 * Resolve glance tone from job state + chainStatus (never "succeeded" as verdict).
 *
 * @param job display job
 * @param nowMs optional clock
 * @returns tone key
 */
export function resolveIntegrityGlanceTone(
  job: IntegrityAsyncJobResponse | null | undefined,
  nowMs: number = Date.now(),
): IntegrityGlanceTone {
  if (job == null) {
    return 'idle';
  }
  if (job.status === 'RUNNING') {
    return isSyntheticNightlyBusyJob(job) ? 'nightly' : 'running';
  }
  if (
    job.status === 'FAILED'
    || job.status === 'EXPIRED'
    || job.status === 'CANCELLED'
    || job.status === 'INTERRUPTED'
  ) {
    return 'did-not-complete';
  }
  if (job.status !== 'SUCCEEDED') {
    return 'idle';
  }
  if (isStaleIntegrityVerdict(job, nowMs)) {
    return 'stale';
  }
  const status: IntegrityChainStatus | null | undefined = job.chainStatus;
  if (status === 'CHAIN_INTEGRITY_VIOLATION_DETECTED') {
    return 'violation';
  }
  if (status === 'UNDECLARED_GAP_DETECTED') {
    return 'gaps';
  }
  if (status === 'OK' || job.intact === true) {
    return 'intact';
  }
  if (status === 'NOT_VERIFIED') {
    return 'did-not-complete';
  }
  if ((job.invalidCheckpoints ?? 0) > 0 || (job.entryHmacViolations ?? 0) > 0) {
    return 'violation';
  }
  if ((job.undeclaredGaps ?? 0) > 0) {
    return 'gaps';
  }
  if (job.intact === false) {
    return 'gaps';
  }
  return 'intact';
}

/**
 * True when the Vérifier filter display range differs from the verdict scope (#696).
 *
 * @param filter display YYYY-MM-DD range
 * @param verdictDisplay display YYYY-MM-DD from job scope
 * @returns whether mismatch copy should show
 */
export function isIntegrityVerdictScopeMismatch(
  filter: { from: string; to: string } | null | undefined,
  verdictDisplay: { from: string; to: string } | null | undefined,
): boolean {
  if (
    filter == null
    || verdictDisplay == null
    || !filter.from
    || !filter.to
    || !verdictDisplay.from
    || !verdictDisplay.to
  ) {
    return false;
  }
  return filter.from !== verdictDisplay.from || filter.to !== verdictDisplay.to;
}

/**
 * Source label key fragment for the scope line (auto / manual / nightly).
 *
 * @param job display job
 * @returns i18n suffix under integrity.microDashboard.source.*
 */
export function integrityVerdictSourceKey(
  job: IntegrityAsyncJobResponse | null | undefined,
): 'auto' | 'manual' | 'nightly' {
  if (job?.trigger === 'NIGHTLY' || isSyntheticNightlyBusyJob(job)) {
    return 'nightly';
  }
  if (job?.trigger === 'AUTO') {
    return 'auto';
  }
  return 'manual';
}

/**
 * Prefer a live RUNNING current job for the strip; otherwise the last verdict.
 *
 * @param currentJob GET /jobs/current
 * @param lastVerdict GET /jobs/last-verdict
 * @returns job to show on the micro-dashboard
 */
export function resolveIntegrityDisplayJob(
  currentJob: IntegrityAsyncJobResponse | null | undefined,
  lastVerdict: IntegrityAsyncJobResponse | null | undefined,
): IntegrityAsyncJobResponse | null {
  if (currentJob != null && currentJob.abandonedAt == null) {
    if (
      currentJob.status === 'RUNNING'
      || currentJob.status === 'EXPIRED'
      || currentJob.status === 'CANCELLED'
    ) {
      return currentJob;
    }
  }
  if (lastVerdict != null) {
    return lastVerdict;
  }
  if (currentJob != null && currentJob.abandonedAt == null) {
    return currentJob;
  }
  return null;
}

/** Re-export busy holder type for callers that only import this module. */
export type { IntegrityBusyHolder };
