/**
 * Integrity cut 3 — micro-dashboard view model and D1 freshness helpers.
 *
 * Pure functions only: page open (24 h rule), job-backed glance tone, scope
 * mismatch (#696). No React / i18n side effects.
 *
 * @since 2026
 */

import type {
  IntegrityAsyncJobResponse,
  IntegrityAsyncJobTrigger,
  IntegrityChainStatus,
} from '@/lib/integrity-async-jobs';

/** Stale / fresh threshold (nightly + #676 default). Age = time since finished. */
export const INTEGRITY_VERDICT_FRESH_MS = 24 * 60 * 60 * 1000;

/** Auto-run Instant window when D1 fires (OQ3 — last 24 h, not legacy 7-day UI default). */
export const INTEGRITY_AUTO_VERIFY_WINDOW_MS = 24 * 60 * 60 * 1000;

/** Glance tone for the verdict tile (never "succeeded" / "réussi"). */
export type IntegrityMicroDashboardTone =
  | 'intact'
  | 'gaps'
  | 'violation'
  | 'stale'
  | 'running'
  | 'nightly-running'
  | 'did-not-complete'
  | 'idle';

export type IntegrityMicroDashboardSource =
  | 'auto'
  | 'manual'
  | 'nightly'
  | 'unknown';

/**
 * Instant bounds for a D1 auto-run (last 24 h ending at {@code now}).
 *
 * @param now reference instant (defaults to current time)
 * @returns ISO Instant `{ from, to }` for `POST …/jobs`
 */
export function defaultAutoVerifyInstantRange(
  now: Date = new Date(),
): { from: string; to: string } {
  const toMs = now.getTime();
  const fromMs = toMs - INTEGRITY_AUTO_VERIFY_WINDOW_MS;
  return {
    from: new Date(fromMs).toISOString(),
    to: new Date(toMs).toISOString(),
  };
}

/**
 * True when a SUCCEEDED job finished less than 24 h ago (OQ2 — age since finished).
 * No scope-coverage arithmetic.
 *
 * @param job last verdict or current SUCCEEDED job
 * @param nowMs reference epoch ms
 * @returns whether the verdict is fresh for D1
 */
export function isFreshIntegrityVerdict(
  job: IntegrityAsyncJobResponse | null | undefined,
  nowMs: number = Date.now(),
): boolean {
  if (job == null || job.abandonedAt != null) {
    return false;
  }
  if (job.status !== 'SUCCEEDED' || job.finishedAt == null) {
    return false;
  }
  const finishedMs = new Date(job.finishedAt).getTime();
  if (Number.isNaN(finishedMs)) {
    return false;
  }
  return nowMs - finishedMs < INTEGRITY_VERDICT_FRESH_MS;
}

/**
 * True when the job carries structured D3 counters / chainStatus for a job-backed glance.
 *
 * @param job async job or last verdict
 * @returns whether the UI can skip `GET /chain-integrity` for the glance
 */
export function hasJobBackedChainGlance(
  job: IntegrityAsyncJobResponse | null | undefined,
): boolean {
  return job != null && job.chainStatus != null;
}

/**
 * Map trigger metadata to a micro-dashboard source label key fragment.
 *
 * @param trigger job trigger
 * @returns source discriminator
 */
export function integrityMicroDashboardSource(
  trigger: IntegrityAsyncJobTrigger | undefined,
): IntegrityMicroDashboardSource {
  switch (trigger) {
    case 'AUTO':
      return 'auto';
    case 'MANUAL':
      return 'manual';
    case 'NIGHTLY':
      return 'nightly';
    default:
      return 'unknown';
  }
}

/**
 * True when display YYYY-MM-DD bounds differ from the verdict Instant scope
 * (converted with the same exclusive-end helper the picker uses).
 *
 * @param pickerRange current Vérifier picker
 * @param verdictDisplayRange job scope as display dates
 * @returns whether #696 mismatch copy should show
 */
export function isIntegrityVerdictScopeMismatch(
  pickerRange: { from: string; to: string } | null | undefined,
  verdictDisplayRange: { from: string; to: string } | null | undefined,
): boolean {
  if (
    pickerRange == null
    || verdictDisplayRange == null
    || !pickerRange.from
    || !pickerRange.to
    || !verdictDisplayRange.from
    || !verdictDisplayRange.to
  ) {
    return false;
  }
  return (
    pickerRange.from !== verdictDisplayRange.from
    || pickerRange.to !== verdictDisplayRange.to
  );
}

/**
 * Non-green chain glance from structured {@code chainStatus} (never parse resultSummary).
 *
 * @param chainStatus D3 field
 * @returns whether Remédier is warranted
 */
export function isIntegrityChainStatusNonGreen(
  chainStatus: IntegrityChainStatus | null | undefined,
): boolean {
  return (
    chainStatus === 'UNDECLARED_GAP_DETECTED'
    || chainStatus === 'CHAIN_INTEGRITY_VIOLATION_DETECTED'
  );
}

/**
 * Resolve the glance tone for the micro-dashboard.
 *
 * @param job current slot job and/or last verdict shown
 * @param nowMs reference epoch ms (1 s clock)
 * @returns tone for colour + i18n key selection
 */
export function resolveIntegrityMicroDashboardTone(
  job: IntegrityAsyncJobResponse | null | undefined,
  nowMs: number = Date.now(),
): IntegrityMicroDashboardTone {
  if (job == null || job.abandonedAt != null) {
    return 'idle';
  }
  if (job.status === 'RUNNING') {
    if (job.busyHolder === 'NIGHTLY' || job.trigger === 'NIGHTLY') {
      return 'nightly-running';
    }
    return 'running';
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

  const fresh = isFreshIntegrityVerdict(job, nowMs);
  const status = job.chainStatus;
  if (status === 'CHAIN_INTEGRITY_VIOLATION_DETECTED') {
    return fresh ? 'violation' : 'stale';
  }
  if (status === 'UNDECLARED_GAP_DETECTED') {
    return fresh ? 'gaps' : 'stale';
  }
  if (status === 'OK' || status === 'NOT_VERIFIED' || status == null) {
    // NOT_VERIFIED / null SUCCEEDED: still a finished job — treat as intact only when OK;
    // null legacy: fall back to intact boolean when present.
    if (status === 'OK') {
      return fresh ? 'intact' : 'stale';
    }
    if (status === 'NOT_VERIFIED') {
      return fresh ? 'did-not-complete' : 'stale';
    }
    if (job.intact === false) {
      return fresh ? 'gaps' : 'stale';
    }
    return fresh ? 'intact' : 'stale';
  }
  return fresh ? 'intact' : 'stale';
}

/**
 * Effective hours for a calendar-days window cap (days×24 + 1 for DST fall-back).
 *
 * @param days configured max window days
 * @returns hours used by soft client checks
 */
export function integrityWindowDaysToHours(days: number): number {
  return days * 24 + 1;
}

/**
 * Poll cadence while an Integrity async job is RUNNING.
 * {@link setInterval} alone delays the first tick by {@code intervalMs}, so a
 * sub-second SUCCEEDED job looks frozen on "Check running…" until that tick.
 * Call {@code poll} immediately, then on the interval.
 *
 * @param poll function that refreshes {@code GET …/jobs/current}
 * @param intervalMs steady poll period (default 3 s)
 * @param timers injectable timers for tests
 * @returns disposer that clears the interval
 */
export type IntegrityRunningPollTimers = {
  setInterval: (handler: () => void, timeout: number) => unknown;
  clearInterval: (id: unknown) => void;
};

export function startIntegrityRunningPoll(
  poll: () => void,
  intervalMs: number = 3000,
  timers: IntegrityRunningPollTimers = {
    setInterval: (handler, timeout) => globalThis.setInterval(handler, timeout),
    clearInterval: (id) => {
      globalThis.clearInterval(id as ReturnType<typeof globalThis.setInterval>);
    },
  },
): () => void {
  poll();
  const id = timers.setInterval(poll, intervalMs);
  return () => {
    timers.clearInterval(id);
  };
}
