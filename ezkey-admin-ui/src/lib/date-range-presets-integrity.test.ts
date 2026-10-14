import { describe, expect, it } from 'vitest';
import {
  estimateIntegrityWindowHours,
  integrityExclusiveApiParamsToDisplayRange,
  integrityExclusiveDateRangeToApiParams,
} from '@/lib/date-range-presets';
import { INTEGRITY_REPORT_MAX_WINDOW_HOURS } from '@/lib/integrity-async-job-report-hydration';

describe('integrityExclusiveDateRangeToApiParams', () => {
  it('uses start of day after to as exclusive upper bound', () => {
    const { createdAfter, createdBefore } = integrityExclusiveDateRangeToApiParams(
      '2026-07-03',
      '2026-07-03',
      'America/New_York',
    );
    expect(createdAfter).toBe('2026-07-03T04:00:00.000Z');
    expect(createdBefore).toBe('2026-07-04T04:00:00.000Z');
  });

  it('estimates a single calendar day as 24 hours', () => {
    const hours = estimateIntegrityWindowHours('2026-07-03', '2026-07-03', 'America/New_York');
    expect(hours).toBe(24);
  });

  it('default 7-day lookback over America/Toronto fall-back stays within the 193h cap', () => {
    // Inclusive calendar from/to for UI default lookback ending on fall-back Sunday.
    const hours = estimateIntegrityWindowHours(
      '2026-10-25',
      '2026-11-01',
      'America/Toronto',
    );
    expect(hours).toBe(193);
    expect(hours).toBeLessThanOrEqual(INTEGRITY_REPORT_MAX_WINDOW_HOURS);
    const { createdAfter, createdBefore } = integrityExclusiveDateRangeToApiParams(
      '2026-10-25',
      '2026-11-01',
      'America/Toronto',
    );
    expect(createdAfter).toBe('2026-10-25T04:00:00.000Z');
    expect(createdBefore).toBe('2026-11-02T05:00:00.000Z');
  });
});

describe('integrityExclusiveApiParamsToDisplayRange', () => {
  it('converts exclusive Instant end back to inclusive calendar day in the zone', () => {
    const { createdAfter, createdBefore } = integrityExclusiveDateRangeToApiParams(
      '2026-07-03',
      '2026-07-03',
      'America/New_York',
    );
    expect(
      integrityExclusiveApiParamsToDisplayRange(
        createdAfter,
        createdBefore,
        'America/New_York',
      ),
    ).toEqual({ from: '2026-07-03', to: '2026-07-03' });
  });
});
