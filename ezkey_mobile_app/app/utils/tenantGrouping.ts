/**
 * Groups enrollments by tenant for display on the home screen.
 */

import type {EnrollmentSummary} from '../services/api/types';

export type TenantGroup = {
  tenantId?: number;
  tenantName: string;
  enrollments: EnrollmentSummary[];
};

const UNKNOWN_TENANT_NAME = 'Unknown tenant';

function normalizeTenantName(name: string | null | undefined): string {
  if (name == null || name.trim() === '') {
    return UNKNOWN_TENANT_NAME;
  }
  return name.trim();
}

/**
 * Groups enrollments by tenant. Groups sorted by tenant name; enrollments by createdAt desc.
 */
export function groupEnrollmentsByTenant(
  enrollments: EnrollmentSummary[],
): TenantGroup[] {
  const byKey = new Map<string, EnrollmentSummary[]>();

  for (const e of enrollments) {
    const tenantName = normalizeTenantName(e.tenantName);
    const key = `${e.tenantId ?? ''}\t${tenantName}`;
    const list = byKey.get(key) ?? [];
    list.push(e);
    byKey.set(key, list);
  }

  const groups: TenantGroup[] = [];
  for (const [, list] of byKey) {
    const first = list[0];
    const tenantName = normalizeTenantName(first?.tenantName);
    const sorted = [...list].sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
    groups.push({
      tenantId: first?.tenantId,
      tenantName,
      enrollments: sorted,
    });
  }

  groups.sort((a, b) => {
    if (a.tenantName === UNKNOWN_TENANT_NAME) return 1;
    if (b.tenantName === UNKNOWN_TENANT_NAME) return -1;
    return a.tenantName.localeCompare(b.tenantName);
  });

  return groups;
}
