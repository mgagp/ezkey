import { describe, expect, it } from 'vitest';
import { enrollmentActiveToggle } from './enrollment-active-toggle';
import type { EnrollmentResponseDto } from '@/generated/admin-api/model';

type Status = EnrollmentResponseDto['enrollmentStatus'];

describe('enrollmentActiveToggle', () => {
  it('offers Deactivate on an active VERIFIED enrollment', () => {
    expect(enrollmentActiveToggle({ enrollmentStatus: 'VERIFIED', enrollmentActive: true })).toBe('deactivate');
  });

  it('offers Reactivate on an inactive VERIFIED enrollment', () => {
    expect(enrollmentActiveToggle({ enrollmentStatus: 'VERIFIED', enrollmentActive: false })).toBe('reactivate');
  });

  it.each(['CREATED', 'BOUND'] as Status[])('offers nothing at %s, which is inactive until verify', (status) => {
    expect(enrollmentActiveToggle({ enrollmentStatus: status, enrollmentActive: false })).toBeNull();
  });

  it.each(['REVOKED', 'EXPIRED', 'INVALID'] as Status[])('offers nothing at %s', (status) => {
    expect(enrollmentActiveToggle({ enrollmentStatus: status, enrollmentActive: false })).toBeNull();
    expect(enrollmentActiveToggle({ enrollmentStatus: status, enrollmentActive: true })).toBeNull();
  });

  it('offers nothing without an enrollment', () => {
    expect(enrollmentActiveToggle(undefined)).toBeNull();
  });
});
