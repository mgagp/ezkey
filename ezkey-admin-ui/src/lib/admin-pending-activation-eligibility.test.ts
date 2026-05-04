import { describe, expect, it } from 'vitest';
import { canReissuePendingAdministratorActivationCode } from './admin-pending-activation-eligibility';
import type { AdminResponseDto } from '@/generated/admin-api/model';

describe('canReissuePendingAdministratorActivationCode', () => {
  it('allows active pending admins without enrollment', () => {
    const admin = {
      active: true,
      lifecycleStatus: 'PENDING_ACTIVATION' as AdminResponseDto['lifecycleStatus'],
      enrollmentId: undefined,
    };
    expect(canReissuePendingAdministratorActivationCode(admin)).toBe(true);
  });

  it('rejects when enrollment exists', () => {
    const admin = {
      active: true,
      lifecycleStatus: 'PENDING_ACTIVATION' as AdminResponseDto['lifecycleStatus'],
      enrollmentId: 99,
    };
    expect(canReissuePendingAdministratorActivationCode(admin)).toBe(false);
  });

  it('rejects when not pending activation', () => {
    const admin = {
      active: true,
      lifecycleStatus: 'ACTIVE' as AdminResponseDto['lifecycleStatus'],
      enrollmentId: undefined,
    };
    expect(canReissuePendingAdministratorActivationCode(admin)).toBe(false);
  });

  it('rejects inactive administrators', () => {
    const admin = {
      active: false,
      lifecycleStatus: 'PENDING_ACTIVATION' as AdminResponseDto['lifecycleStatus'],
      enrollmentId: undefined,
    };
    expect(canReissuePendingAdministratorActivationCode(admin)).toBe(false);
  });
});
