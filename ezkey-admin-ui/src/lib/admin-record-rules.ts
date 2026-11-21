import type { AdminResponseDto } from '@/generated/admin-api/model';

/** Administrator created with an activation code that nobody has redeemed yet. */
export function isPendingActivationAdmin(
  admin: Pick<AdminResponseDto, 'lifecycleStatus'> | null | undefined,
): boolean {
  return admin?.lifecycleStatus === 'PENDING_ACTIVATION';
}

/** Active Tenant Admin who cannot work because the tenant chain is not operational. */
export function shouldShowTenantOperationalWarning(
  admin: Pick<AdminResponseDto, 'adminType' | 'active' | 'operational' | 'lifecycleStatus'>,
): boolean {
  return (
    admin.adminType !== 'GLOBAL_ADMIN'
    && admin.active === true
    && admin.lifecycleStatus === 'ACTIVE'
    && admin.operational === false
  );
}

/** Key under `detail.enrollmentState.*` in the admins namespace. */
export type AdminEnrollmentStateKey =
  | 'CREATED'
  | 'BOUND'
  | 'VERIFIED'
  | 'INVALID'
  | 'REVOKED'
  | 'EXPIRED'
  | 'NONE';

/**
 * Enrollment row of the administrator record (#1428, vision V-2026-06-05): the binding and
 * verification state of the linked enrollment. Only VERIFIED reads as done; CREATED and BOUND are
 * steps still owed; terminal states and "no enrollment" stay muted.
 */
export function adminEnrollmentState(
  admin: Pick<AdminResponseDto, 'enrollmentId' | 'enrollmentStatus'>,
): { key: AdminEnrollmentStateKey; variant: 'success' | 'warning' | 'muted' } {
  if (admin.enrollmentId == null || admin.enrollmentStatus == null) {
    return { key: 'NONE', variant: 'muted' };
  }
  switch (admin.enrollmentStatus) {
    case 'VERIFIED':
      return { key: 'VERIFIED', variant: 'success' };
    case 'CREATED':
    case 'BOUND':
      return { key: admin.enrollmentStatus, variant: 'warning' };
    default:
      return { key: admin.enrollmentStatus, variant: 'muted' };
  }
}
