import type { EnrollmentResponseDto } from '@/generated/admin-api/model';

/**
 * What the operator may edit on an enrollment. The Admin API accepts every metadata field on an
 * active VERIFIED enrollment, and only contactEmail on a CREATED one, so an invitation can
 * get an address before a send (#1206 step 5).
 *
 * An administrator's MFA enrollment (system integration) has no contact email: the administrator's
 * email lives on the administrator record and the API refuses any contactEmail write with 409
 * admin-enrollment (#1313). So it has nothing to edit at CREATED, and its VERIFIED edit hides the
 * contact email field (see showsContactEmail).
 */
export type EnrollmentEditScope = 'all' | 'contactEmail' | 'none';

type EditScopeFields = Pick<EnrollmentResponseDto, 'enrollmentStatus' | 'enrollmentActive' | 'isSystemIntegration'>;

export function enrollmentEditScope(enrollment: EditScopeFields | null | undefined): EnrollmentEditScope {
  // A CREATED enrollment stays inactive until verify, so the active flag only gates VERIFIED.
  if (enrollment?.enrollmentStatus === 'CREATED') {
    return showsContactEmail(enrollment) ? 'contactEmail' : 'none';
  }
  if (enrollment?.enrollmentStatus === 'VERIFIED' && enrollment.enrollmentActive === true) return 'all';
  return 'none';
}

/** Whether the record shows and edits a contact email: never for an administrator's enrollment (#1313). */
export function showsContactEmail(
  enrollment: Pick<EnrollmentResponseDto, 'isSystemIntegration'> | null | undefined,
): boolean {
  return enrollment?.isSystemIntegration !== true;
}
