import { describe, expect, it } from 'vitest';
import {
  estimateIntegrityWindowHours,
  integrityExclusiveDateRangeToApiParams,
} from '@/lib/date-range-presets';

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
});
