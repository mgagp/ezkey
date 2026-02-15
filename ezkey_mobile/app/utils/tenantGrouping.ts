/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: tenantGrouping
 * Description: Groups enrollments by tenant for hierarchical display in the home screen.
 * @since 2025
 */

import type {StoredEnrollment} from '../services/storage/enrollmentStorage';

const UNKNOWN_TENANT_NAME = 'Unknown tenant';

/**
 * Group of enrollments belonging to a single tenant.
 *
 * @param tenantId Optional tenant identifier
 * @param tenantName Display name of the tenant
 * @param tenantDescription Optional tenant description
 * @param enrollments Enrollments in this tenant group
 * @since 2025
 */
export type TenantGroup = {
  tenantId?: number;
  tenantName: string;
  tenantDescription?: string;
  enrollments: StoredEnrollment[];
};

function normalizeTenantName(name: string | null | undefined): string {
  if (name == null || name.trim() === '') {
    return UNKNOWN_TENANT_NAME;
  }
  return name.trim();
}

function normalizeTenantDescription(desc: string | null | undefined): string | undefined {
  if (desc == null || desc.trim() === '') {
    return undefined;
  }
  return desc.trim();
}

/**
 * Sorts enrollments: favorites first, then newest by createdAt.
 *
 * @param items Enrollments to sort
 * @return Sorted array
 * @since 2025
 */
function sortEnrollmentsWithinGroup(items: StoredEnrollment[]): StoredEnrollment[] {
  return [...items].sort((left, right) => {
    if (left.favorited && !right.favorited) return -1;
    if (!left.favorited && right.favorited) return 1;
    return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
  });
}

/**
 * Groups enrollments by tenant, mirroring the demo device layout.
 *
 * Groups are keyed by (tenantId, tenantName, tenantDescription). When tenantId is absent
 * (backward compatibility), tenantName is used. Empty or null tenant names become
 * "Unknown tenant".
 *
 * Tenant groups are sorted: "Unknown tenant" last, then alphabetically by tenantName,
 * then by tenantId. Enrollments within each group are sorted by favorites then createdAt.
 *
 * @param enrollments Enrollments to group (assumed pre-sorted if needed)
 * @return Ordered list of tenant groups
 * @since 2025
 */
export function groupEnrollmentsByTenant(enrollments: StoredEnrollment[]): TenantGroup[] {
  const grouped = new Map<string, StoredEnrollment[]>();

  for (const e of enrollments) {
    const tenantName = normalizeTenantName(e.tenantName);
    const tenantDescription = normalizeTenantDescription(e.tenantDescription);
    const isUnknown = tenantName === UNKNOWN_TENANT_NAME;
    const tenantId = isUnknown ? null : (e.tenantId ?? null);
    const key = JSON.stringify({tenantId, tenantName, tenantDescription});

    const list = grouped.get(key) ?? [];
    list.push(e);
    grouped.set(key, list);
  }

  const keys = Array.from(grouped.keys());
  keys.sort((a, b) => {
    const ka = JSON.parse(a) as {tenantId: number | null; tenantName: string};
    const kb = JSON.parse(b) as {tenantId: number | null; tenantName: string};
    const unknownA = ka.tenantName === UNKNOWN_TENANT_NAME ? 1 : 0;
    const unknownB = kb.tenantName === UNKNOWN_TENANT_NAME ? 1 : 0;
    if (unknownA !== unknownB) return unknownA - unknownB;
    const nameCmp = (ka.tenantName ?? '').localeCompare(kb.tenantName ?? '', undefined, {
      sensitivity: 'base',
    });
    if (nameCmp !== 0) return nameCmp;
    const idA = ka.tenantId ?? 0;
    const idB = kb.tenantId ?? 0;
    return idA - idB;
  });

  return keys.map(key => {
    const parsed = JSON.parse(key) as {
      tenantId: number | null;
      tenantName: string;
      tenantDescription?: string;
    };
    const list = grouped.get(key)!;
    return {
      tenantId: parsed.tenantId ?? undefined,
      tenantName: parsed.tenantName,
      tenantDescription: parsed.tenantDescription,
      enrollments: sortEnrollmentsWithinGroup(list),
    };
  });
}
