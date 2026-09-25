import { describe, expect, it } from 'vitest';
import { isIntegrityAsyncEscapeStatus } from './integrity-async-jobs';

describe('isIntegrityAsyncEscapeStatus', () => {
  it('is true for sticky Escape statuses', () => {
    expect(isIntegrityAsyncEscapeStatus('EXPIRED')).toBe(true);
    expect(isIntegrityAsyncEscapeStatus('CANCELLED')).toBe(true);
    expect(isIntegrityAsyncEscapeStatus('INTERRUPTED')).toBe(true);
  });

  it('is false for healthy / terminal non-escape statuses', () => {
    expect(isIntegrityAsyncEscapeStatus('RUNNING')).toBe(false);
    expect(isIntegrityAsyncEscapeStatus('SUCCEEDED')).toBe(false);
    expect(isIntegrityAsyncEscapeStatus('FAILED')).toBe(false);
    expect(isIntegrityAsyncEscapeStatus(null)).toBe(false);
    expect(isIntegrityAsyncEscapeStatus(undefined)).toBe(false);
  });
});
