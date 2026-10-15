/**
 * Integrity cut 3 — micro-dashboard view model and D1 freshness helpers.
 *
 * Pure functions only: page open (24 h rule), job-backed glance tone, scope
 * mismatch (#696), RUNNING poll scheduling. No React / i18n side effects.
 *
 * @since 2026
 */

import { integrityExclusiveApiParamsToDisplayRange } from '@/lib/date-range-presets';
import {
  isIntegrityAsyncEscapeStatus,
  type IntegrityAsyncJobAcceptedResponse,
  type IntegrityAsyncJobResponse,
  type IntegrityAsyncJobTrigger,
  type IntegrityAsyncJobType,
  type IntegrityChainStatus,
} from '@/lib/integrity-async-jobs';

/** Stale / fresh threshold (nightly + #676 default). Age = time since finished. */
export const INTEGRITY_VERDICT_FRESH_MS = 24 * 60 * 60 * 1000;

/** Auto-run Instant window when D1 fires (OQ3 — last 24 h, not legacy 7-day UI default). */
export const INTEGRITY_AUTO_VERIFY_WINDOW_MS = 24 * 60 * 60 * 1000;

/** Default backend max async window days when bootstrap omits the field (#788). */
export const INTEGRITY_DEFAULT_MAX_WINDOW_DAYS = 92;

/** Steady poll period while GET …/jobs/current stays RUNNING. */
export const INTEGRITY_RUNNING_POLL_INTERVAL_MS = 3000;

/**
 * Consecutive `/current` failures before the RUNNING poll halts and surfaces an
 * error (Patrick freeze path + Christophe m1).
 */
export const INTEGRITY_RUNNING_POLL_MAX_ERRORS = 5;

/** Cap for exponential backoff after poll errors. */
export const INTEGRITY_RUNNING_POLL_MAX_BACKOFF_MS = 60_000;

/** Glance tone for the verdict tile (never "succeeded" / "réussi"). */
export type IntegrityMicroDashboardTone =
  | 'intact'
  | 'gaps'
  | 'violation'
  | 'stale'
  | 'running'
  | 'nightly-running'
  | 'did-not-complete'
  /** SUCCEEDED with chainStatus NOT_VERIFIED (empty range / skipped) — neutral, not amber. */
  | 'nothing-to-verify'
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
 * True when a SUCCEEDED chain glance finished less than 24 h ago (OQ2 — age
 * since finished). Requires {@code chainStatus} so a bare VERIFY_ENTRY success
 * cannot suppress D1 auto-run (#788). Recent {@code NOT_VERIFIED} counts as
 * fresh when present.
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
  // Chain glance only — VERIFY_ENTRY without chainStatus must not block D1.
  if (job.chainStatus == null) {
    return false;
  }
  const finishedMs = new Date(job.finishedAt).getTime();
  if (Number.isNaN(finishedMs)) {
    return false;
  }
  return nowMs - finishedMs < INTEGRITY_VERDICT_FRESH_MS;
}

/**
 * Recency epoch for comparing `/current` vs `/last-verdict` (finished, else started).
 *
 * @param job async job row
 * @returns epoch ms, or 0 when unknown
 */
export function integrityJobRecencyMs(job: IntegrityAsyncJobResponse): number {
  const raw = job.finishedAt ?? job.startedAt;
  if (raw == null) {
    return 0;
  }
  const ms = new Date(raw).getTime();
  return Number.isNaN(ms) ? 0 : ms;
}

/**
 * Prefer the newer of two Integrity jobs (abandoned rows ignored).
 *
 * @param a first candidate (often `/current`)
 * @param b second candidate (often `/last-verdict`)
 * @returns newer non-abandoned job, or null
 */
export function pickNewerIntegrityJob(
  a: IntegrityAsyncJobResponse | null | undefined,
  b: IntegrityAsyncJobResponse | null | undefined,
): IntegrityAsyncJobResponse | null {
  const left = a != null && a.abandonedAt == null ? a : null;
  const right = b != null && b.abandonedAt == null ? b : null;
  if (left == null) {
    return right;
  }
  if (right == null) {
    return left;
  }
  return integrityJobRecencyMs(right) > integrityJobRecencyMs(left) ? right : left;
}

/** D1 page-open decision after `/current` (+ `/last-verdict` when needed). */
export type IntegrityOpenDecision =
  | {
      action: 'hold';
      displayJob: IntegrityAsyncJobResponse;
      reason: 'running' | 'escape' | 'fresh-verdict';
    }
  | {
      action: 'auto-run';
      displayJob: IntegrityAsyncJobResponse | null;
      reason: 'stale-or-absent';
    };

/**
 * Pure D1 open decision: stop only for RUNNING, escape-sticky, or a fresh chain
 * verdict; otherwise show the newer of `/current` and `/last-verdict` and
 * auto-run when neither is fresh (#788 / Patrick B1).
 *
 * Caller fetches `/last-verdict` before calling unless {@code current} alone is
 * already RUNNING or escape-sticky (pass {@code lastVerdict=null} then).
 *
 * Error contract: treat a failed read as {@code null} (fail-open toward
 * auto-run when no fresh glance remains).
 *
 * @param current `GET …/jobs/current` body, or null on 204 / read error
 * @param lastVerdict `GET …/jobs/last-verdict` body, or null on 204 / read error
 * @param nowMs reference epoch ms
 * @returns hold vs auto-run with the job to display
 */
export function decideIntegrityOpenAction(
  current: IntegrityAsyncJobResponse | null,
  lastVerdict: IntegrityAsyncJobResponse | null,
  nowMs: number = Date.now(),
): IntegrityOpenDecision {
  if (current != null && current.abandonedAt == null && current.status === 'RUNNING') {
    return { action: 'hold', displayJob: current, reason: 'running' };
  }
  if (
    current != null
    && current.abandonedAt == null
    && isIntegrityAsyncEscapeStatus(current.status)
  ) {
    return { action: 'hold', displayJob: current, reason: 'escape' };
  }

  const displayJob = pickNewerIntegrityJob(current, lastVerdict);
  if (
    isFreshIntegrityVerdict(current, nowMs)
    || isFreshIntegrityVerdict(lastVerdict, nowMs)
  ) {
    const freshDisplay =
      displayJob != null && isFreshIntegrityVerdict(displayJob, nowMs)
        ? displayJob
        : isFreshIntegrityVerdict(lastVerdict, nowMs)
          ? lastVerdict
          : current;
    return {
      action: 'hold',
      displayJob: freshDisplay!,
      reason: 'fresh-verdict',
    };
  }

  return { action: 'auto-run', displayJob, reason: 'stale-or-absent' };
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
 * Optimistic RUNNING glance after POST …/jobs accepts a start (#788 F1).
 * Keeps the micro-dashboard mounted when the first `/current` poll fails so
 * backoff + inline poll errors can run (same as open-with-RUNNING).
 *
 * @param accepted 202 body with jobId
 * @param request type, Instant scope, and trigger used for the POST
 * @param nowIso optional startedAt (defaults to now)
 * @returns provisional RUNNING job for the strip
 */
export function buildProvisionalRunningIntegrityJob(
  accepted: IntegrityAsyncJobAcceptedResponse,
  request: {
    type: IntegrityAsyncJobType;
    from: string;
    to: string;
    trigger?: 'MANUAL' | 'AUTO';
  },
  nowIso: string = new Date().toISOString(),
): IntegrityAsyncJobResponse {
  return {
    jobId: accepted.jobId,
    type: request.type,
    status: 'RUNNING',
    trigger: request.trigger ?? 'MANUAL',
    startedAt: nowIso,
    scopeFrom: request.from,
    scopeTo: request.to,
    busyHolder: 'OPERATOR_JOB',
  };
}

/**
 * After POST accepts a job: install a provisional RUNNING glance, then try
 * `/current`. On read failure or idle 204, keep the provisional so the strip
 * stays up and the RUNNING poll session can backoff (#788 F1 Julie Walk).
 *
 * @param accepted POST …/jobs body
 * @param request start request fields
 * @param fetchCurrent GET …/jobs/current
 * @param setJob glance setter
 * @returns whether `/current` replaced the provisional
 */
export async function installIntegrityJobAfterStart(
  accepted: IntegrityAsyncJobAcceptedResponse,
  request: {
    type: IntegrityAsyncJobType;
    from: string;
    to: string;
    trigger?: 'MANUAL' | 'AUTO';
  },
  fetchCurrent: () => Promise<IntegrityAsyncJobResponse | null>,
  setJob: (job: IntegrityAsyncJobResponse | null) => void,
): Promise<'current' | 'provisional'> {
  const provisional = buildProvisionalRunningIntegrityJob(accepted, request);
  setJob(provisional);
  try {
    const current = await fetchCurrent();
    if (current != null) {
      setJob(current);
      return 'current';
    }
    return 'provisional';
  } catch {
    // Keep provisional RUNNING; poll session surfaces inline errors (#788 F1).
    return 'provisional';
  }
}

/**
 * Converts a finished job's exclusive Instant scope to inclusive Vérifier
 * picker dates in the effective timezone (#696). Returns null while RUNNING
 * or when scope / status cannot drive the picker.
 *
 * @param job current glance job (or null)
 * @param timeZoneId effective display timezone id (optional until resolver ready)
 * @returns inclusive YYYY-MM-DD range, or null when the picker must not sync
 */
export function integrityFinishedJobScopeToDisplayRange(
  job: IntegrityAsyncJobResponse | null | undefined,
  timeZoneId: string | undefined,
): { from: string; to: string } | null {
  if (job?.scopeFrom == null || job.scopeTo == null || job.status === 'RUNNING') {
    return null;
  }
  if (job.status !== 'SUCCEEDED' && !isIntegrityAsyncEscapeStatus(job.status)) {
    return null;
  }
  return integrityExclusiveApiParamsToDisplayRange(
    job.scopeFrom,
    job.scopeTo,
    timeZoneId,
  );
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
  if (status === 'NOT_VERIFIED') {
    // Julie Walk m2: empty/skipped verify is not "non aboutie" amber — neutral glance.
    return fresh ? 'nothing-to-verify' : 'stale';
  }
  if (status === 'OK') {
    return fresh ? 'intact' : 'stale';
  }
  // Null chainStatus (legacy / VERIFY_ENTRY): fall back to intact boolean.
  if (job.intact === false) {
    return fresh ? 'gaps' : 'stale';
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
 * Whether Revérifier must stay disabled (Christophe m2 / #788).
 * Includes {@code chainLoading} so the control stays off while POST …/jobs
 * is in flight — not only while the slot shows RUNNING.
 *
 * @param args slot + POST-in-flight + picker bounds
 * @returns true when the micro-dashboard Re-verify button is disabled
 */
export function integrityReverifyDisabled(args: {
  asyncStartsBlocked: boolean;
  chainLoading: boolean;
  rangeFrom?: string;
  rangeTo?: string;
}): boolean {
  return (
    args.asyncStartsBlocked
    || args.chainLoading
    || !args.rangeFrom
    || !args.rangeTo
  );
}

/**
 * Whether Vérifier la chaîne / entry / validation actions stay disabled.
 * Includes range-over-cap so the control cannot fire a duplicate over-cap
 * toast when the inline warning is already shown (Julie C1 / #743).
 *
 * @param args loading + slot + picker + over-cap
 * @returns true when the verify action button is disabled
 */
export function integrityVerifyActionDisabled(args: {
  loading: boolean;
  asyncStartsBlocked: boolean;
  rangeFrom?: string;
  rangeTo?: string;
  rangeOverCap: boolean;
}): boolean {
  return (
    args.loading
    || args.asyncStartsBlocked
    || !args.rangeFrom
    || !args.rangeTo
    || args.rangeOverCap
  );
}

/**
 * Label kind for the micro-dashboard time cell (Julie m1).
 * RUNNING → started; SUCCEEDED → checked; terminal non-success → finished.
 *
 * @param status async job status
 * @returns which i18n label to use for the time cell
 */
export function integrityMicroDashboardCheckedKind(
  status: IntegrityAsyncJobResponse['status'] | undefined,
): 'started' | 'finished' | 'checked' {
  if (status === 'RUNNING') {
    return 'started';
  }
  if (status === 'SUCCEEDED') {
    return 'checked';
  }
  return 'finished';
}

/**
 * Whether the verdict tile should show a spinning Loader2 (Julie m6).
 * After a `/current` poll halt, keep RUNNING copy but stop the spinner.
 *
 * @param tone glance tone
 * @param pollHalted true when the RUNNING poll exhausted its error budget
 * @returns whether animate-spin applies
 */
export function integrityMicroDashboardSpinnerActive(
  tone: IntegrityMicroDashboardTone,
  pollHalted: boolean,
): boolean {
  return (tone === 'running' || tone === 'nightly-running') && !pollHalted;
}

/**
 * Display name for {@code startedByUsername} (Julie m4 — translate {@code system}).
 *
 * @param username raw username from the job DTO
 * @param systemLabel localized word for the system actor
 * @returns display string
 */
export function integrityMicroDashboardActorLabel(
  username: string | null | undefined,
  systemLabel: string,
): string {
  if (username == null || username === '') {
    return '—';
  }
  if (username === 'system') {
    return systemLabel;
  }
  return username;
}

/**
 * Glance to keep while idle {@code GET …/jobs/current} (204) awaits
 * {@code /last-verdict}. Must not retain escape-sticky rows — abandon → 204
 * would otherwise leave Abandon + blocked Revérifier forever (Patrick B2).
 *
 * @param prev currently displayed job
 * @returns retained glance, or null when the strip must clear
 */
export function retainIntegrityGlanceOnIdle204(
  prev: IntegrityAsyncJobResponse | null,
): IntegrityAsyncJobResponse | null {
  if (prev == null || prev.abandonedAt != null) {
    return null;
  }
  if (isIntegrityAsyncEscapeStatus(prev.status)) {
    return null;
  }
  return prev;
}

/**
 * Apply {@code /last-verdict} after an idle 204. Prefer the verdict; otherwise
 * clear RUNNING / escape; keep a finished non-escape glance.
 *
 * @param prev glance retained while the verdict loads
 * @param lastVerdict `GET …/jobs/last-verdict` body, or null on 204 / error
 * @returns next job for the micro-dashboard
 */
export function applyIntegrityLastVerdictAfterIdle204(
  prev: IntegrityAsyncJobResponse | null,
  lastVerdict: IntegrityAsyncJobResponse | null,
): IntegrityAsyncJobResponse | null {
  if (lastVerdict != null) {
    return lastVerdict;
  }
  if (prev == null || prev.abandonedAt != null) {
    return null;
  }
  if (prev.status === 'RUNNING' || isIntegrityAsyncEscapeStatus(prev.status)) {
    return null;
  }
  return prev;
}

/**
 * Apply a `/jobs/current` (or abandon) update, including idle-204 retain +
 * `/last-verdict` reload. Used by the Integrity page and component tests
 * (Patrick nit — do not copy the handler).
 *
 * @param next next job, or null on idle 204
 * @param sink state setter + last-verdict loader
 */
export function applyIntegrityAsyncJobChange(
  next: IntegrityAsyncJobResponse | null,
  sink: {
    setJob: (
      value:
        | IntegrityAsyncJobResponse
        | null
        | ((
            prev: IntegrityAsyncJobResponse | null,
          ) => IntegrityAsyncJobResponse | null),
    ) => void;
    loadLastVerdict: () => Promise<IntegrityAsyncJobResponse | null>;
  },
): void {
  if (next != null) {
    sink.setJob(next);
    return;
  }
  sink.setJob((prev) => retainIntegrityGlanceOnIdle204(prev));
  void (async () => {
    try {
      const verdict = await sink.loadLastVerdict();
      sink.setJob((prev) => applyIntegrityLastVerdictAfterIdle204(prev, verdict));
    } catch {
      sink.setJob((prev) => applyIntegrityLastVerdictAfterIdle204(prev, null));
    }
  })();
}

/**
 * Delay before the next RUNNING poll tick (exponential backoff on errors).
 *
 * @param consecutiveErrors failures since last success
 * @param baseMs steady interval
 * @returns delay in ms
 */
export function integrityRunningPollDelayMs(
  consecutiveErrors: number,
  baseMs: number = INTEGRITY_RUNNING_POLL_INTERVAL_MS,
): number {
  if (consecutiveErrors <= 0) {
    return baseMs;
  }
  const exp = Math.min(consecutiveErrors, 4);
  return Math.min(baseMs * 2 ** exp, INTEGRITY_RUNNING_POLL_MAX_BACKOFF_MS);
}

/**
 * Whether the RUNNING poll should halt after repeated `/current` failures.
 *
 * @param consecutiveErrors failures since last success
 * @param maxErrors halt threshold
 * @returns true when the UI must stop polling and show an error
 */
export function integrityRunningPollShouldHalt(
  consecutiveErrors: number,
  maxErrors: number = INTEGRITY_RUNNING_POLL_MAX_ERRORS,
): boolean {
  return consecutiveErrors >= maxErrors;
}

/**
 * Poll cadence while an Integrity async job is RUNNING.
 * {@link setInterval} alone delays the first tick by {@code intervalMs}; call
 * {@code poll} immediately, then on the interval (UX latency, not a freeze).
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
  intervalMs: number = INTEGRITY_RUNNING_POLL_INTERVAL_MS,
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

/** Injectable clock/DOM for {@link startIntegrityRunningPollSession}. */
export type IntegrityRunningPollSessionEnv = {
  setTimeout: (handler: () => void, timeout: number) => unknown;
  clearTimeout: (id: unknown) => void;
  isDocumentHidden: () => boolean;
  addVisibilityListener: (listener: () => void) => () => void;
};

export type IntegrityRunningPollSessionOptions = {
  /** Fetches `GET …/jobs/current` (null = 204). */
  fetchCurrent: () => Promise<IntegrityAsyncJobResponse | null>;
  /** Apply a response that won the generation race and arrived before stop. */
  onJob: (job: IntegrityAsyncJobResponse | null) => void;
  /**
   * Poll error surface. {@code halted} means backoff exhausted — stop looping.
   * Transient errors keep the session alive with backoff.
   */
  onPollError?: (kind: 'transient' | 'halted', consecutiveErrors: number) => void;
  /** Cleared after a successful poll. */
  onPollRecovered?: () => void;
  intervalMs?: number;
  maxErrors?: number;
  env?: Partial<IntegrityRunningPollSessionEnv>;
};

/**
 * RUNNING poll with immediate first tick, visibility pause (Christophe m1),
 * exponential backoff, halt after N errors, and generation guards so
 * out-of-order / post-unmount responses never call {@code onJob} (#788).
 *
 * @param options fetch + callbacks + injectable env
 * @returns disposer (invalidates in-flight generation)
 */
export function startIntegrityRunningPollSession(
  options: IntegrityRunningPollSessionOptions,
): () => void {
  const intervalMs = options.intervalMs ?? INTEGRITY_RUNNING_POLL_INTERVAL_MS;
  const maxErrors = options.maxErrors ?? INTEGRITY_RUNNING_POLL_MAX_ERRORS;
  const env: IntegrityRunningPollSessionEnv = {
    setTimeout: (handler, timeout) => globalThis.setTimeout(handler, timeout),
    clearTimeout: (id) => {
      globalThis.clearTimeout(id as ReturnType<typeof globalThis.setTimeout>);
    },
    isDocumentHidden: () =>
      typeof document !== 'undefined' && document.visibilityState === 'hidden',
    addVisibilityListener: (listener) => {
      if (typeof document === 'undefined') {
        return () => undefined;
      }
      document.addEventListener('visibilitychange', listener);
      return () => document.removeEventListener('visibilitychange', listener);
    },
    ...options.env,
  };

  let stopped = false;
  let generation = 0;
  let consecutiveErrors = 0;
  let timerId: unknown = null;
  let inFlight = false;

  const clearTimer = () => {
    if (timerId != null) {
      env.clearTimeout(timerId);
      timerId = null;
    }
  };

  const schedule = (delayMs: number) => {
    clearTimer();
    if (stopped) {
      return;
    }
    timerId = env.setTimeout(() => {
      void tick();
    }, delayMs);
  };

  const tick = async () => {
    if (stopped) {
      return;
    }
    if (env.isDocumentHidden()) {
      // Pause while the tab is hidden; visibilitychange resumes.
      return;
    }
    if (inFlight) {
      return;
    }
    const gen = ++generation;
    inFlight = true;
    try {
      const next = await options.fetchCurrent();
      if (stopped || gen !== generation) {
        return;
      }
      consecutiveErrors = 0;
      options.onPollRecovered?.();
      options.onJob(next);
      // Parent clears RUNNING on terminal; if still RUNNING, keep polling.
      schedule(intervalMs);
    } catch {
      if (stopped || gen !== generation) {
        return;
      }
      consecutiveErrors += 1;
      if (integrityRunningPollShouldHalt(consecutiveErrors, maxErrors)) {
        options.onPollError?.('halted', consecutiveErrors);
        return;
      }
      options.onPollError?.('transient', consecutiveErrors);
      schedule(integrityRunningPollDelayMs(consecutiveErrors, intervalMs));
    } finally {
      inFlight = false;
    }
  };

  const onVisibility = () => {
    if (stopped) {
      return;
    }
    if (!env.isDocumentHidden()) {
      // Returning to the tab after a halt resets the error budget (#788).
      consecutiveErrors = 0;
      void tick();
    } else {
      clearTimer();
    }
  };

  const removeVisibility = env.addVisibilityListener(onVisibility);
  void tick();

  return () => {
    stopped = true;
    generation += 1;
    clearTimer();
    removeVisibility();
  };
}
