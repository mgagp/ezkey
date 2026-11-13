import type { EnrollmentResponseDto } from '@/generated/admin-api/model';

/**
 * What the operator may edit on an enrollment. The Admin API accepts every metadata field on an
 * active VERIFIED enrollment, and only contactEmail on a CREATED one, so an invitation can
 * get an address before a send (#1206 step 5).
 */
export type EnrollmentEditScope = 'all' | 'contactEmail' | 'none';

export function enrollmentEditScope(
  enrollment: Pick<EnrollmentResponseDto, 'enrollmentStatus' | 'enrollmentActive'> | null | undefined,
): EnrollmentEditScope {
  // A CREATED enrollment stays inactive until verify, so the active flag only gates VERIFIED.
  if (enrollment?.enrollmentStatus === 'CREATED') return 'contactEmail';
  if (enrollment?.enrollmentStatus === 'VERIFIED' && enrollment.enrollmentActive === true) return 'all';
  return 'none';
}
