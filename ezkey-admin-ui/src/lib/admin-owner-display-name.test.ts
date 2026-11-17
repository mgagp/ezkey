import { describe, expect, it } from 'vitest';
import { adminOwnerDisplayName } from './admin-owner-display-name';

describe('adminOwnerDisplayName', () => {
  it('prefers the full name', () => {
    expect(adminOwnerDisplayName({ firstName: 'Alice', lastName: 'Chen', username: 'alice' })).toBe('Alice Chen');
  });

  it('falls back to the username, then to null', () => {
    expect(adminOwnerDisplayName({ firstName: ' ', username: 'alice' })).toBe('alice');
    expect(adminOwnerDisplayName({})).toBeNull();
    expect(adminOwnerDisplayName(undefined)).toBeNull();
  });
});
