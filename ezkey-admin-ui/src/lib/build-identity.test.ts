import { describe, expect, it } from 'vitest';
import { getBuildShortSha } from './build-identity';

describe('getBuildShortSha', () => {
  it('returns a short SHA as-is', () => {
    expect(getBuildShortSha('abc1234')).toBe('abc1234');
  });

  it('truncates longer SHA values to 7 characters', () => {
    expect(getBuildShortSha('abcdef0123456789')).toBe('abcdef0');
  });

  it('falls back to dev when the value is blank', () => {
    expect(getBuildShortSha('')).toBe('dev');
    expect(getBuildShortSha('   ')).toBe('dev');
  });

  it('uses Vite-injected SHA when called with no argument', () => {
    const sha = getBuildShortSha();
    expect(sha.length).toBeGreaterThan(0);
    expect(sha.length).toBeLessThanOrEqual(7);
    expect(sha).not.toBe('v0.1.0');
  });
});
