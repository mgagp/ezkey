import { describe, expect, it, vi } from 'vitest';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';
import {
  applyIntegrityLastVerdictAfterIdle204,
  decideIntegrityOpenAction,
  defaultAutoVerifyInstantRange,
  hasJobBackedChainGlance,
  INTEGRITY_AUTO_VERIFY_WINDOW_MS,
  INTEGRITY_RUNNING_POLL_MAX_ERRORS,
  INTEGRITY_VERDICT_FRESH_MS,
  integrityMicroDashboardSource,
  integrityReverifyDisabled,
  integrityRunningPollDelayMs,
  integrityRunningPollShouldHalt,
  integrityWindowDaysToHours,
  isFreshIntegrityVerdict,
  isIntegrityChainStatusNonGreen,
  isIntegrityVerdictScopeMismatch,
  pickNewerIntegrityJob,
  resolveIntegrityMicroDashboardTone,
  retainIntegrityGlanceOnIdle204,
  startIntegrityRunningPoll,
  startIntegrityRunningPollSession,
} from './integrity-micro-dashboard';

function job(
  overrides: Partial<IntegrityAsyncJobResponse> = {},
): IntegrityAsyncJobResponse {
  return {
    jobId: '11111111-1111-1111-1111-111111111111',
    type: 'VERIFY_CHAIN_RANGE',
    status: 'SUCCEEDED',
    chainStatus: 'OK',
    ...overrides,
  };
}

describe('isFreshIntegrityVerdict', () => {
  const now = Date.parse('2026-10-13T12:00:00.000Z');

  it('is true when SUCCEEDED finished within 24 h with chainStatus', () => {
    expect(
      isFreshIntegrityVerdict(
        job({ finishedAt: new Date(now - 3 * 60 * 60 * 1000).toISOString() }),
        now,
      ),
    ).toBe(true);
  });

  it('counts recent NOT_VERIFIED as fresh (#788)', () => {
    expect(
      isFreshIntegrityVerdict(
        job({
          chainStatus: 'NOT_VERIFIED',
          finishedAt: new Date(now - 60 * 60 * 1000).toISOString(),
        }),
        now,
      ),
    ).toBe(true);
  });

  it('is false when finished at or beyond 24 h', () => {
    expect(
      isFreshIntegrityVerdict(
        job({ finishedAt: new Date(now - INTEGRITY_VERDICT_FRESH_MS).toISOString() }),
        now,
      ),
    ).toBe(false);
  });

  it('is false for VERIFY_ENTRY without chainStatus (must not block D1)', () => {
    expect(
      isFreshIntegrityVerdict(
        job({
          type: 'VERIFY_ENTRY_HMAC_RANGE',
          chainStatus: null,
          finishedAt: new Date(now - 1000).toISOString(),
        }),
        now,
      ),
    ).toBe(false);
  });

  it('is false for RUNNING / FAILED / missing finishedAt', () => {
    expect(isFreshIntegrityVerdict(job({ status: 'RUNNING', finishedAt: undefined }), now)).toBe(
      false,
    );
    expect(
      isFreshIntegrityVerdict(
        job({
          status: 'FAILED',
          finishedAt: new Date(now - 1000).toISOString(),
        }),
        now,
      ),
    ).toBe(false);
    expect(isFreshIntegrityVerdict(job({ finishedAt: undefined }), now)).toBe(false);
  });
});

describe('decideIntegrityOpenAction (D1 / Patrick B1)', () => {
  const now = Date.parse('2026-10-13T12:00:00.000Z');

  it('holds on RUNNING (including busyHolder=NIGHTLY) without auto-run', () => {
    const current = job({
      status: 'RUNNING',
      busyHolder: 'NIGHTLY',
      trigger: 'NIGHTLY',
      finishedAt: undefined,
      chainStatus: null,
    });
    expect(decideIntegrityOpenAction(current, null, now)).toEqual({
      action: 'hold',
      displayJob: current,
      reason: 'running',
    });
  });

  it('holds on escape-sticky EXPIRED/CANCELLED', () => {
    const current = job({
      status: 'EXPIRED',
      finishedAt: new Date(now - 1000).toISOString(),
      chainStatus: null,
    });
    expect(decideIntegrityOpenAction(current, null, now)).toEqual({
      action: 'hold',
      displayJob: current,
      reason: 'escape',
    });
  });

  it('holds on a fresh /current verdict without needing last-verdict freshness', () => {
    const current = job({
      finishedAt: new Date(now - 2 * 60 * 60 * 1000).toISOString(),
      chainStatus: 'OK',
    });
    const decision = decideIntegrityOpenAction(current, null, now);
    expect(decision.action).toBe('hold');
    if (decision.action === 'hold') {
      expect(decision.reason).toBe('fresh-verdict');
      expect(decision.displayJob).toBe(current);
    }
  });

  it('auto-runs when /current is a stale SUCCEEDED (5 days) even if VERIFY succeeded', () => {
    const current = job({
      finishedAt: new Date(now - 5 * 24 * 60 * 60 * 1000).toISOString(),
      chainStatus: 'OK',
    });
    expect(decideIntegrityOpenAction(current, null, now)).toEqual({
      action: 'auto-run',
      displayJob: current,
      reason: 'stale-or-absent',
    });
  });

  it('prefers a newer fresh NIGHTLY last-verdict over a stale operator /current', () => {
    const current = job({
      jobId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      finishedAt: new Date(now - 5 * 24 * 60 * 60 * 1000).toISOString(),
      trigger: 'MANUAL',
    });
    const lastVerdict = job({
      jobId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      finishedAt: new Date(now - 60 * 60 * 1000).toISOString(),
      trigger: 'NIGHTLY',
      type: 'RUN_VALIDATION',
    });
    const decision = decideIntegrityOpenAction(current, lastVerdict, now);
    expect(decision).toEqual({
      action: 'hold',
      displayJob: lastVerdict,
      reason: 'fresh-verdict',
    });
  });

  it('auto-runs when both are stale, displaying the newer', () => {
    const current = job({
      jobId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      finishedAt: new Date(now - 3 * 24 * 60 * 60 * 1000).toISOString(),
    });
    const lastVerdict = job({
      jobId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      finishedAt: new Date(now - 2 * 24 * 60 * 60 * 1000).toISOString(),
      trigger: 'NIGHTLY',
    });
    expect(decideIntegrityOpenAction(current, lastVerdict, now)).toEqual({
      action: 'auto-run',
      displayJob: lastVerdict,
      reason: 'stale-or-absent',
    });
  });

  it('auto-runs on 204/null current and null last-verdict (error-on-open = fail-open)', () => {
    expect(decideIntegrityOpenAction(null, null, now)).toEqual({
      action: 'auto-run',
      displayJob: null,
      reason: 'stale-or-absent',
    });
  });

  it('does not treat fresh VERIFY_ENTRY_HMAC without chainStatus as a D1 stop', () => {
    const current = job({
      type: 'VERIFY_ENTRY_HMAC_RANGE',
      chainStatus: null,
      finishedAt: new Date(now - 1000).toISOString(),
    });
    expect(decideIntegrityOpenAction(current, null, now).action).toBe('auto-run');
  });
});

describe('pickNewerIntegrityJob', () => {
  it('picks by finishedAt then ignores abandoned', () => {
    const older = job({
      jobId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      finishedAt: '2026-10-10T00:00:00.000Z',
    });
    const newer = job({
      jobId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      finishedAt: '2026-10-12T00:00:00.000Z',
    });
    expect(pickNewerIntegrityJob(older, newer)?.jobId).toBe(newer.jobId);
    expect(
      pickNewerIntegrityJob(newer, { ...older, abandonedAt: '2026-10-13T00:00:00.000Z' })
        ?.jobId,
    ).toBe(newer.jobId);
  });
});

describe('defaultAutoVerifyInstantRange', () => {
  it('returns a 24 h Instant window ending at now', () => {
    const now = new Date('2026-10-13T12:00:00.000Z');
    const range = defaultAutoVerifyInstantRange(now);
    expect(range.to).toBe(now.toISOString());
    expect(Date.parse(range.to) - Date.parse(range.from)).toBe(INTEGRITY_AUTO_VERIFY_WINDOW_MS);
  });
});

describe('resolveIntegrityMicroDashboardTone', () => {
  const now = Date.parse('2026-10-13T12:00:00.000Z');

  it('maps RUNNING + nightly busyHolder', () => {
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          status: 'RUNNING',
          busyHolder: 'NIGHTLY',
          trigger: 'NIGHTLY',
          finishedAt: undefined,
          chainStatus: null,
        }),
        now,
      ),
    ).toBe('nightly-running');
  });

  it('maps SUCCEEDED chainStatus without using resultSummary vocabulary', () => {
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          chainStatus: 'OK',
          finishedAt: new Date(now - 1000).toISOString(),
          resultSummary: 'succeeded with gaps',
        }),
        now,
      ),
    ).toBe('intact');
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          chainStatus: 'UNDECLARED_GAP_DETECTED',
          undeclaredGaps: 2,
          finishedAt: new Date(now - 1000).toISOString(),
        }),
        now,
      ),
    ).toBe('gaps');
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          chainStatus: 'CHAIN_INTEGRITY_VIOLATION_DETECTED',
          invalidCheckpoints: 1,
          finishedAt: new Date(now - 1000).toISOString(),
        }),
        now,
      ),
    ).toBe('violation');
  });

  it('marks stale when SUCCEEDED older than 24 h', () => {
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          chainStatus: 'OK',
          finishedAt: new Date(now - INTEGRITY_VERDICT_FRESH_MS - 1).toISOString(),
        }),
        now,
      ),
    ).toBe('stale');
  });

  it('maps FAILED to did-not-complete', () => {
    expect(
      resolveIntegrityMicroDashboardTone(
        job({
          status: 'FAILED',
          finishedAt: new Date(now - 1000).toISOString(),
          chainStatus: null,
        }),
        now,
      ),
    ).toBe('did-not-complete');
  });
});

describe('scope + helpers', () => {
  it('detects picker vs verdict display mismatch (#696)', () => {
    expect(
      isIntegrityVerdictScopeMismatch(
        { from: '2026-10-01', to: '2026-10-07' },
        { from: '2026-10-01', to: '2026-10-08' },
      ),
    ).toBe(true);
    expect(
      isIntegrityVerdictScopeMismatch(
        { from: '2026-10-01', to: '2026-10-07' },
        { from: '2026-10-01', to: '2026-10-07' },
      ),
    ).toBe(false);
  });

  it('maps trigger sources and window hours', () => {
    expect(integrityMicroDashboardSource('AUTO')).toBe('auto');
    expect(integrityMicroDashboardSource('MANUAL')).toBe('manual');
    expect(integrityMicroDashboardSource('NIGHTLY')).toBe('nightly');
    expect(integrityWindowDaysToHours(8)).toBe(193);
    expect(integrityWindowDaysToHours(92)).toBe(2209);
  });

  it('detects job-backed glance and non-green status', () => {
    expect(hasJobBackedChainGlance(job({ chainStatus: 'OK' }))).toBe(true);
    expect(hasJobBackedChainGlance(job({ chainStatus: null }))).toBe(false);
    expect(isIntegrityChainStatusNonGreen('UNDECLARED_GAP_DETECTED')).toBe(true);
    expect(isIntegrityChainStatusNonGreen('OK')).toBe(false);
  });
});

describe('startIntegrityRunningPoll', () => {
  it('polls immediately, then on the interval (UX latency, not a freeze)', () => {
    const poll = vi.fn();
    vi.useFakeTimers();
    const stop = startIntegrityRunningPoll(poll, 3000);
    expect(poll).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(2999);
    expect(poll).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1);
    expect(poll).toHaveBeenCalledTimes(2);

    stop();
    vi.advanceTimersByTime(3000);
    expect(poll).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });
});

describe('retainIntegrityGlanceOnIdle204 / last-verdict (Patrick B2 + m2)', () => {
  it('clears escape-sticky EXPIRED on 204 so abandon does not stick', () => {
    const expired = job({
      status: 'EXPIRED',
      finishedAt: '2026-10-13T11:00:00.000Z',
      chainStatus: null,
    });
    expect(retainIntegrityGlanceOnIdle204(expired)).toBeNull();
    expect(applyIntegrityLastVerdictAfterIdle204(expired, null)).toBeNull();
  });

  it('clears a row that already carries abandonedAt', () => {
    const abandoned = job({
      status: 'EXPIRED',
      abandonedAt: '2026-10-13T12:00:00.000Z',
      chainStatus: null,
    });
    expect(retainIntegrityGlanceOnIdle204(abandoned)).toBeNull();
  });

  it('keeps a finished SUCCEEDED glance when last-verdict is empty', () => {
    const succeeded = job({
      finishedAt: '2026-10-13T11:00:00.000Z',
      chainStatus: 'OK',
    });
    expect(retainIntegrityGlanceOnIdle204(succeeded)).toBe(succeeded);
    expect(applyIntegrityLastVerdictAfterIdle204(succeeded, null)).toBe(succeeded);
  });

  it('reloads /last-verdict after 204 (nightly end)', () => {
    const nightlyRunning = job({
      status: 'RUNNING',
      busyHolder: 'NIGHTLY',
      trigger: 'NIGHTLY',
      finishedAt: undefined,
      chainStatus: null,
    });
    const lastVerdict = job({
      jobId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      finishedAt: '2026-10-13T11:55:00.000Z',
      trigger: 'NIGHTLY',
      type: 'RUN_VALIDATION',
    });
    expect(retainIntegrityGlanceOnIdle204(nightlyRunning)).toBe(nightlyRunning);
    expect(applyIntegrityLastVerdictAfterIdle204(nightlyRunning, lastVerdict)).toBe(
      lastVerdict,
    );
    expect(applyIntegrityLastVerdictAfterIdle204(nightlyRunning, null)).toBeNull();
  });
});

describe('integrityReverifyDisabled (Christophe m2)', () => {
  it('disables while chainLoading even when the slot is idle', () => {
    expect(
      integrityReverifyDisabled({
        asyncStartsBlocked: false,
        chainLoading: true,
        rangeFrom: '2026-10-01',
        rangeTo: '2026-10-02',
      }),
    ).toBe(true);
  });

  it('enables only when idle, not loading, and range is set', () => {
    expect(
      integrityReverifyDisabled({
        asyncStartsBlocked: false,
        chainLoading: false,
        rangeFrom: '2026-10-01',
        rangeTo: '2026-10-02',
      }),
    ).toBe(false);
    expect(
      integrityReverifyDisabled({
        asyncStartsBlocked: true,
        chainLoading: false,
        rangeFrom: '2026-10-01',
        rangeTo: '2026-10-02',
      }),
    ).toBe(true);
    expect(
      integrityReverifyDisabled({
        asyncStartsBlocked: false,
        chainLoading: false,
        rangeFrom: '',
        rangeTo: '2026-10-02',
      }),
    ).toBe(true);
  });
});

describe('integrityRunningPoll backoff / halt', () => {
  it('backs off exponentially and halts at max errors', () => {
    expect(integrityRunningPollDelayMs(0, 3000)).toBe(3000);
    expect(integrityRunningPollDelayMs(1, 3000)).toBe(6000);
    expect(integrityRunningPollDelayMs(2, 3000)).toBe(12000);
    expect(integrityRunningPollShouldHalt(INTEGRITY_RUNNING_POLL_MAX_ERRORS - 1)).toBe(false);
    expect(integrityRunningPollShouldHalt(INTEGRITY_RUNNING_POLL_MAX_ERRORS)).toBe(true);
  });
});

describe('startIntegrityRunningPollSession', () => {
  it('stops scheduling after unmount disposer (ignores late responses)', async () => {
    vi.useFakeTimers();
    let resolveFetch: ((value: IntegrityAsyncJobResponse | null) => void) | undefined;
    const fetchCurrent = vi.fn(
      () =>
        new Promise<IntegrityAsyncJobResponse | null>((resolve) => {
          resolveFetch = resolve;
        }),
    );
    const onJob = vi.fn();
    const stop = startIntegrityRunningPollSession({
      fetchCurrent,
      onJob,
      env: {
        isDocumentHidden: () => false,
        addVisibilityListener: () => () => undefined,
      },
    });

    expect(fetchCurrent).toHaveBeenCalledTimes(1);
    stop();
    resolveFetch?.(job({ status: 'SUCCEEDED', finishedAt: '2026-10-13T12:00:00.000Z' }));
    await Promise.resolve();
    expect(onJob).not.toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('pauses while hidden and resumes on visibilitychange (Christophe m1)', async () => {
    vi.useFakeTimers();
    let hidden = false;
    let visibilityListener: (() => void) | undefined;
    const fetchCurrent = vi.fn(async () =>
      job({ status: 'RUNNING', finishedAt: undefined, chainStatus: null }),
    );
    const onJob = vi.fn();

    const stop = startIntegrityRunningPollSession({
      fetchCurrent,
      onJob,
      intervalMs: 3000,
      env: {
        isDocumentHidden: () => hidden,
        addVisibilityListener: (listener) => {
          visibilityListener = listener;
          return () => {
            visibilityListener = undefined;
          };
        },
      },
    });

    // Immediate tick only — do not drain the scheduled 3 s interval yet.
    await Promise.resolve();
    expect(fetchCurrent).toHaveBeenCalledTimes(1);
    expect(onJob).toHaveBeenCalledTimes(1);

    hidden = true;
    visibilityListener?.();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(fetchCurrent).toHaveBeenCalledTimes(1);

    hidden = false;
    visibilityListener?.();
    await Promise.resolve();
    expect(fetchCurrent).toHaveBeenCalledTimes(2);
    stop();
    vi.useRealTimers();
  });

  it('backs off on errors then halts (freeze path — swallowed errors)', async () => {
    vi.useFakeTimers();
    const fetchCurrent = vi.fn(async () => {
      throw new Error('network');
    });
    const onJob = vi.fn();
    const onPollError = vi.fn();

    startIntegrityRunningPollSession({
      fetchCurrent,
      onJob,
      onPollError,
      intervalMs: 3000,
      maxErrors: 3,
      env: {
        isDocumentHidden: () => false,
        addVisibilityListener: () => () => undefined,
      },
    });

    await Promise.resolve();
    expect(onPollError).toHaveBeenLastCalledWith('transient', 1);

    await vi.advanceTimersByTimeAsync(6000);
    await Promise.resolve();
    expect(onPollError).toHaveBeenLastCalledWith('transient', 2);

    await vi.advanceTimersByTimeAsync(12_000);
    await Promise.resolve();
    expect(onPollError).toHaveBeenLastCalledWith('halted', 3);
    expect(onJob).not.toHaveBeenCalled();

    const callsAtHalt = fetchCurrent.mock.calls.length;
    await vi.advanceTimersByTimeAsync(60_000);
    expect(fetchCurrent.mock.calls.length).toBe(callsAtHalt);
    vi.useRealTimers();
  });

  it('resets the error budget when the tab becomes visible after a halt', async () => {
    vi.useFakeTimers();
    let hidden = false;
    let visibilityListener: (() => void) | undefined;
    const fetchCurrent = vi.fn(async () => {
      throw new Error('network');
    });
    const onPollError = vi.fn();

    startIntegrityRunningPollSession({
      fetchCurrent,
      onPollError,
      onJob: vi.fn(),
      intervalMs: 3000,
      maxErrors: 2,
      env: {
        isDocumentHidden: () => hidden,
        addVisibilityListener: (listener) => {
          visibilityListener = listener;
          return () => {
            visibilityListener = undefined;
          };
        },
      },
    });

    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(6000);
    await Promise.resolve();
    expect(onPollError).toHaveBeenLastCalledWith('halted', 2);
    const callsAtHalt = fetchCurrent.mock.calls.length;

    hidden = false;
    visibilityListener?.();
    await Promise.resolve();
    expect(fetchCurrent.mock.calls.length).toBe(callsAtHalt + 1);
    expect(onPollError).toHaveBeenLastCalledWith('transient', 1);
    vi.useRealTimers();
  });
});
