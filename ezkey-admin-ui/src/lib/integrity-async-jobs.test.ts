import { describe, expect, it } from 'vitest';
import { isIntegrityAsyncEscapeStatus } from './integrity-async-jobs';

describe('isIntegrityAsyncEscapeStatus', () => {
  it('is true for operator Escape sticky (TTL / explicit cancel)', () => {
    expect(isIntegrityAsyncEscapeStatus('EXPIRED')).toBe(true);
    expect(isIntegrityAsyncEscapeStatus('CANCELLED')).toBe(true);
  });

  it('is false for crash INTERRUPTED and non-sticky statuses', () => {
    expect(isIntegrityAsyncEscapeStatus('INTERRUPTED')).toBe(false);
    expect(isIntegrityAsyncEscapeStatus('RUNNING')).toBe(false);
    expect(isIntegrityAsyncEscapeStatus('SUCCEEDED')).toBe(false);
    expect(isIntegrityAsyncEscapeStatus('FAILED')).toBe(false);
    expect(isIntegrityAsyncEscapeStatus(null)).toBe(false);
    expect(isIntegrityAsyncEscapeStatus(undefined)).toBe(false);
  });
});
