import type { AdminResponseDto } from '@/generated/admin-api/model';

/**
 * Whether the onboarding credentials action applies to an administrator (#1266): the Admin API
 * returns bind material only while the enrollment is CREATED or BOUND. An older list response
 * without enrollmentStatus hides the action, which fails closed.
 */
export function canShowOnboardingCredentials(
  admin: Pick<AdminResponseDto, 'enrollmentId' | 'enrollmentStatus' | 'lifecycleStatus'>,
): boolean {
  return (
    admin.enrollmentId != null
    && admin.lifecycleStatus !== 'PENDING_ACTIVATION'
    && (admin.enrollmentStatus === 'CREATED' || admin.enrollmentStatus === 'BOUND')
  );
}
