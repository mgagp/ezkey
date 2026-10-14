/** @vitest-environment jsdom */

import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { useCallback, useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { IntegrityAsyncJobResponse } from '@/lib/integrity-async-jobs';
import { applyIntegrityAsyncJobChange } from '@/lib/integrity-micro-dashboard';
import { IntegrityMicroDashboard } from './integrity-micro-dashboard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts?.count != null ? `${key}:${String(opts.count)}` : key,
  }),
}));

vi.mock('@/context/use-toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

const getCurrentIntegrityAsyncJob = vi.fn();
const abandonIntegrityAsyncJob = vi.fn();
const getLastIntegrityVerdict = vi.fn();

vi.mock('@/lib/integrity-async-jobs', async () => {
  const actual = await vi.importActual<typeof import('@/lib/integrity-async-jobs')>(
    '@/lib/integrity-async-jobs',
  );
  return {
    ...actual,
    getCurrentIntegrityAsyncJob: (...args: unknown[]) => getCurrentIntegrityAsyncJob(...args),
    abandonIntegrityAsyncJob: (...args: unknown[]) => abandonIntegrityAsyncJob(...args),
    getLastIntegrityVerdict: (...args: unknown[]) => getLastIntegrityVerdict(...args),
  };
});

/** Fixed "now" so SUCCEEDED fixtures stay within the 24 h fresh window. */
const FROZEN_NOW = new Date('2026-10-13T13:00:00.000Z');

function job(
  overrides: Partial<IntegrityAsyncJobResponse> = {},
): IntegrityAsyncJobResponse {
  return {
    jobId: '11111111-1111-1111-1111-111111111111',
    type: 'VERIFY_CHAIN_RANGE',
    status: 'SUCCEEDED',
    chainStatus: 'OK',
    // One hour before FROZEN_NOW — fresh under INTEGRITY_VERDICT_FRESH_MS.
    finishedAt: '2026-10-13T12:00:00.000Z',
    ...overrides,
  };
}

/** Uses the same idle-204 handler as pages/integrity.tsx (Patrick nit). */
function PageLikeHarness({
  initial,
  reverifyDisabled = false,
}: {
  initial: IntegrityAsyncJobResponse;
  reverifyDisabled?: boolean;
}) {
  const [current, setCurrent] = useState<IntegrityAsyncJobResponse | null>(initial);
  const onJobChange = useCallback((next: IntegrityAsyncJobResponse | null) => {
    applyIntegrityAsyncJobChange(next, {
      setJob: setCurrent,
      loadLastVerdict: () => getLastIntegrityVerdict(),
    });
  }, []);

  return (
    <IntegrityMicroDashboard
      job={current}
      onJobChange={onJobChange}
      active
      reverifyDisabled={reverifyDisabled}
      onReverify={() => undefined}
    />
  );
}

describe('IntegrityMicroDashboard poll + abandon', () => {
  beforeEach(() => {
    getCurrentIntegrityAsyncJob.mockReset();
    abandonIntegrityAsyncJob.mockReset();
    getLastIntegrityVerdict.mockReset();
    getLastIntegrityVerdict.mockResolvedValue(null);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('stops polling /current after a terminal SUCCEEDED status', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(FROZEN_NOW);
    const running = job({
      status: 'RUNNING',
      finishedAt: undefined,
      chainStatus: null,
    });
    const succeeded = job({
      status: 'SUCCEEDED',
      finishedAt: '2026-10-13T12:00:00.000Z',
      chainStatus: 'OK',
    });
    getCurrentIntegrityAsyncJob.mockResolvedValue(succeeded);

    render(<PageLikeHarness initial={running} />);

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByTestId('integrity-micro-dashboard').getAttribute('data-tone')).toBe(
      'intact',
    );

    const callsAfterTerminal = getCurrentIntegrityAsyncJob.mock.calls.length;
    expect(callsAfterTerminal).toBeGreaterThanOrEqual(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });
    expect(getCurrentIntegrityAsyncJob.mock.calls.length).toBe(callsAfterTerminal);
  });

  it('applies abandon response so EXPIRED + idle 204 clears without last-verdict (B2)', async () => {
    const expired = job({
      status: 'EXPIRED',
      finishedAt: '2026-10-13T11:00:00.000Z',
      chainStatus: null,
    });
    const abandoned = {
      ...expired,
      abandonedAt: '2026-10-13T12:05:00.000Z',
    };
    abandonIntegrityAsyncJob.mockResolvedValue(abandoned);
    getCurrentIntegrityAsyncJob.mockResolvedValue(null);
    getLastIntegrityVerdict.mockResolvedValue(null);

    render(<PageLikeHarness initial={expired} reverifyDisabled />);

    expect(screen.getByTestId('integrity-micro-dashboard').getAttribute('data-tone')).toBe(
      'did-not-complete',
    );
    expect(screen.getByTestId('integrity-micro-dashboard-checked').getAttribute('data-checked-kind')).toBe(
      'finished',
    );

    fireEvent.click(screen.getByTestId('integrity-async-job-abandon'));
    fireEvent.click(screen.getByTestId('integrity-async-job-abandon-confirm'));

    await waitFor(() => {
      expect(abandonIntegrityAsyncJob).toHaveBeenCalledTimes(1);
      expect(screen.queryByTestId('integrity-micro-dashboard')).toBeNull();
    });
  });

  it('reloads /last-verdict after idle 204 while RUNNING nightly', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(FROZEN_NOW);
    const nightlyRunning = job({
      status: 'RUNNING',
      busyHolder: 'NIGHTLY',
      trigger: 'NIGHTLY',
      finishedAt: undefined,
      chainStatus: null,
      startedByUsername: 'system',
    });
    const lastVerdict = job({
      jobId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      finishedAt: '2026-10-13T11:55:00.000Z',
      trigger: 'NIGHTLY',
      type: 'RUN_VALIDATION',
      chainStatus: 'OK',
    });
    getCurrentIntegrityAsyncJob.mockResolvedValue(null);
    getLastIntegrityVerdict.mockResolvedValue(lastVerdict);

    render(<PageLikeHarness initial={nightlyRunning} />);

    expect(screen.getByTestId('integrity-micro-dashboard-checked').getAttribute('data-checked-kind')).toBe(
      'started',
    );

    await waitFor(() => {
      expect(getLastIntegrityVerdict).toHaveBeenCalled();
      expect(screen.getByTestId('integrity-micro-dashboard').getAttribute('data-tone')).toBe(
        'intact',
      );
    });
  });

});
