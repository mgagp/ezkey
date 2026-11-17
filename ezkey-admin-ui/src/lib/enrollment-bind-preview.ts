import type { EnrollmentResponseDto } from '@/generated/admin-api/model';

type BindPreviewFields = Pick<EnrollmentResponseDto, 'enrollmentStatus' | 'isSystemIntegration'>;

/**
 * Whether the enrollment record offers its QR card (#1180, #1313).
 *
 * The QR holds the proof token, so it follows the CREATED rule. An administrator's MFA enrollment
 * (system integration) never shows it here: the Admin API answers 409 admin-enrollment, and the
 * administrator record is the single place to reveal it.
 */
export function showsEnrollmentQrCard(enrollment: BindPreviewFields): boolean {
  return enrollment.enrollmentStatus === 'CREATED' && enrollment.isSystemIntegration !== true;
}

/**
 * Whether the enrollment record shows the integrated-delivery notice: only while the bind material
 * is still needed, and never for an administrator's MFA enrollment, whose material is not shown on
 * this record (#1313).
 */
export function showsIntegratedDeliveryNotice(enrollment: BindPreviewFields): boolean {
  return (
    (enrollment.enrollmentStatus === 'CREATED' || enrollment.enrollmentStatus === 'BOUND')
    && enrollment.isSystemIntegration !== true
  );
}
