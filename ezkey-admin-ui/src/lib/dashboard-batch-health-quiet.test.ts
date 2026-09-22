import { describe, expect, it } from 'vitest';
import { resolveBatchHealthQuietReason } from './dashboard-batch-health-quiet';

describe('resolveBatchHealthQuietReason', () => {
  it('collapses when every job succeeded', () => {
    expect(
      resolveBatchHealthQuietReason(
        [{ lastStatus: 'SUCCESS' }, { lastStatus: 'SUCCESS' }],
        false,
      ),
    ).toBe('success');
  });

  it('collapses never-run only when jobs are expected idle', () => {
    const neverRun: { lastStatus?: 'NEVER_RUN' }[] = [
      { lastStatus: 'NEVER_RUN' },
      { lastStatus: undefined },
    ];
    expect(resolveBatchHealthQuietReason(neverRun, true)).toBe('never-run');
    expect(resolveBatchHealthQuietReason(neverRun, false)).toBeNull();
  });

  it('stays open on failure even if other jobs are quiet', () => {
    expect(
      resolveBatchHealthQuietReason(
        [{ lastStatus: 'SUCCESS' }, { lastStatus: 'FAILED' }],
        false,
      ),
    ).toBeNull();
    expect(
      resolveBatchHealthQuietReason(
        [{ lastStatus: 'NEVER_RUN' }, { lastStatus: 'FAILED' }],
        true,
      ),
    ).toBeNull();
  });

  it('stays open on an empty job list', () => {
    expect(resolveBatchHealthQuietReason([], true)).toBeNull();
  });
});
