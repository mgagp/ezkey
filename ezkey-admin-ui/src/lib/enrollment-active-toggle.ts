import type { EnrollmentResponseDto } from '@/generated/admin-api/model';

/**
 * The on/off switch an operator may use on an enrollment. The active flag is a switch for
 * VERIFIED enrollments only: a CREATED or BOUND enrollment is inactive until verify, and the
 * Admin API refuses to reactivate it (docs/LIFECYCLE_GOVERNANCE.md, #1277).
 */
export type EnrollmentActiveToggle = 'deactivate' | 'reactivate' | null;

export function enrollmentActiveToggle(
  enrollment: Pick<EnrollmentResponseDto, 'enrollmentStatus' | 'enrollmentActive'> | null | undefined,
): EnrollmentActiveToggle {
  if (enrollment?.enrollmentStatus !== 'VERIFIED') return null;
  return enrollment.enrollmentActive === true ? 'deactivate' : 'reactivate';
}
