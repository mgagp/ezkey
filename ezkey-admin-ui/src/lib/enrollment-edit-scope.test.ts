import { describe, expect, it } from 'vitest';
import { enrollmentEditScope, showsContactEmail } from './enrollment-edit-scope';
import type { EnrollmentResponseDto } from '@/generated/admin-api/model';

type Status = EnrollmentResponseDto['enrollmentStatus'];

describe('enrollmentEditScope', () => {
  it('allows every field on an active VERIFIED enrollment', () => {
    expect(enrollmentEditScope({ enrollmentStatus: 'VERIFIED', enrollmentActive: true })).toBe('all');
  });

  it('allows only the contact email on a CREATED enrollment, which is inactive until verify', () => {
    expect(enrollmentEditScope({ enrollmentStatus: 'CREATED', enrollmentActive: false })).toBe(
      'contactEmail',
    );
  });

  it.each(['BOUND', 'INVALID', 'REVOKED', 'EXPIRED'] as Status[])('allows nothing at %s', (status) => {
    expect(enrollmentEditScope({ enrollmentStatus: status, enrollmentActive: true })).toBe('none');
  });

  it('allows nothing on an inactive VERIFIED enrollment', () => {
    expect(enrollmentEditScope({ enrollmentStatus: 'VERIFIED', enrollmentActive: false })).toBe('none');
  });

  // #1313: an administrator's enrollment carries no contact email.
  it('allows nothing on an administrator enrollment at CREATED', () => {
    expect(
      enrollmentEditScope({ enrollmentStatus: 'CREATED', enrollmentActive: false, isSystemIntegration: true }),
    ).toBe('none');
  });

  it('keeps the metadata edit on an active VERIFIED administrator enrollment', () => {
    expect(
      enrollmentEditScope({ enrollmentStatus: 'VERIFIED', enrollmentActive: true, isSystemIntegration: true }),
    ).toBe('all');
  });
});

describe('showsContactEmail', () => {
  it('hides the contact email on an administrator enrollment only', () => {
    expect(showsContactEmail({ isSystemIntegration: true })).toBe(false);
    expect(showsContactEmail({ isSystemIntegration: false })).toBe(true);
    expect(showsContactEmail({})).toBe(true);
  });
});
