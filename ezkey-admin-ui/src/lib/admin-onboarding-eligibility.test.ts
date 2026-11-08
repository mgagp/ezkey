import { describe, expect, it } from 'vitest';
import { canShowOnboardingCredentials } from './admin-onboarding-eligibility';
import type { AdminResponseDto } from '@/generated/admin-api/model';

type Status = AdminResponseDto['enrollmentStatus'];

const admin = (enrollmentStatus: Status) => ({
  enrollmentId: 7,
  enrollmentStatus,
  lifecycleStatus: 'ACTIVE' as AdminResponseDto['lifecycleStatus'],
});

describe('canShowOnboardingCredentials', () => {
  it.each(['CREATED', 'BOUND'] as Status[])('shows the action at %s', (status) => {
    expect(canShowOnboardingCredentials(admin(status))).toBe(true);
  });

  it.each(['VERIFIED', 'INVALID', 'REVOKED', 'EXPIRED', undefined] as Status[])(
    'hides the action at %s',
    (status) => {
      expect(canShowOnboardingCredentials(admin(status))).toBe(false);
    },
  );

  it('hides the action without an enrollment', () => {
    expect(canShowOnboardingCredentials({ ...admin('CREATED'), enrollmentId: undefined })).toBe(false);
  });

  it('hides the action while activation is pending', () => {
    expect(
      canShowOnboardingCredentials({
        ...admin('CREATED'),
        lifecycleStatus: 'PENDING_ACTIVATION' as AdminResponseDto['lifecycleStatus'],
      }),
    ).toBe(false);
  });
});
