import { describe, expect, it } from 'vitest';
import { adminEnrollmentState } from './admin-record-rules';

describe('adminEnrollmentState (#1428)', () => {
  it('shows no enrollment as muted', () => {
    expect(adminEnrollmentState({ enrollmentId: null, enrollmentStatus: null })).toEqual({
      key: 'NONE',
      variant: 'muted',
    });
    expect(adminEnrollmentState({})).toEqual({ key: 'NONE', variant: 'muted' });
  });

  it('never shows a CREATED enrollment as done', () => {
    expect(adminEnrollmentState({ enrollmentId: 7, enrollmentStatus: 'CREATED' })).toEqual({
      key: 'CREATED',
      variant: 'warning',
    });
  });

  it('shows BOUND as a step still owed', () => {
    expect(adminEnrollmentState({ enrollmentId: 7, enrollmentStatus: 'BOUND' })).toEqual({
      key: 'BOUND',
      variant: 'warning',
    });
  });

  it('shows only VERIFIED as success', () => {
    expect(adminEnrollmentState({ enrollmentId: 7, enrollmentStatus: 'VERIFIED' })).toEqual({
      key: 'VERIFIED',
      variant: 'success',
    });
  });

  it.each(['INVALID', 'REVOKED', 'EXPIRED'] as const)('shows %s muted with its own label', (status) => {
    expect(adminEnrollmentState({ enrollmentId: 7, enrollmentStatus: status })).toEqual({
      key: status,
      variant: 'muted',
    });
  });
});
