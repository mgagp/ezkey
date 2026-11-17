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
