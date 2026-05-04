import type { AdminResponseDto } from '@/generated/admin-api/model';

/**
 * Whether a pending administrator is eligible for Global Admin activation-code re-issue via the API.
 * The Admin API also rejects inactive tenants; the UI surfaces that via standard API errors.
 */
export function canReissuePendingAdministratorActivationCode(
  admin: Pick<AdminResponseDto, 'active' | 'lifecycleStatus' | 'enrollmentId'> | null | undefined,
): boolean {
  return (
    admin?.active === true
    && admin.lifecycleStatus === 'PENDING_ACTIVATION'
    && admin.enrollmentId == null
  );
}
