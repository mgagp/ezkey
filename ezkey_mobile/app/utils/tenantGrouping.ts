/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: tenantGrouping
 * Description: Groups enrollments by Ezkey installation first, then by tenant for the home screen.
 * @since 2025
 */

import type {StoredEnrollment} from '../services/storage/enrollmentStorage';
import {
  hydrateInstallationMetadata,
  shouldShowInstallationHostHint,
} from './installationMetadata';

type InstallationBranding = {
  installationId: string;
  installationName: string;
  installationDescription?: string;
  installationHost?: string;
  installationAboutUrl?: string;
  showHostHint: boolean;
};

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

export type InstallationGroup = InstallationBranding & {
  tenantGroups: TenantGroup[];
  ungroupedEnrollments: StoredEnrollment[];
};

function normalizeTenantName(name: string | null | undefined): string | undefined {
  if (name == null || name.trim() === '') {
    return undefined;
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

function sortInstallations(left: InstallationBranding, right: InstallationBranding): number {
  const nameCmp = left.installationName.localeCompare(right.installationName, undefined, {
    sensitivity: 'base',
  });
  if (nameCmp !== 0) {
    return nameCmp;
  }

  return (left.installationHost ?? '').localeCompare(right.installationHost ?? '', undefined, {
    sensitivity: 'base',
  });
}

function sortTenantGroups(left: TenantGroup, right: TenantGroup): number {
  const nameCmp = left.tenantName.localeCompare(right.tenantName, undefined, {
    sensitivity: 'base',
  });
  if (nameCmp !== 0) {
    return nameCmp;
  }

  return (left.tenantId ?? 0) - (right.tenantId ?? 0);
}

function buildInstallationBranding(enrollment: StoredEnrollment): InstallationBranding {
  const hydrated = hydrateInstallationMetadata(enrollment);

  return {
    installationId: hydrated.installationId ?? hydrated.id,
    installationName: hydrated.installationName ?? 'Ezkey installation',
    installationDescription: hydrated.installationDescription,
    installationHost: hydrated.installationHost,
    installationAboutUrl: hydrated.installationAboutUrl,
    showHostHint: shouldShowInstallationHostHint(hydrated),
  };
}

/**
 * Groups enrollments by installation, then by tenant where tenant metadata is present.
 *
 * @param enrollments Enrollments to group (assumed pre-sorted if needed)
 * @return Ordered list of installation groups
 * @since 2025
 */
export function groupEnrollmentsByTenant(enrollments: StoredEnrollment[]): InstallationGroup[] {
  const grouped = new Map<string, StoredEnrollment[]>();

  for (const enrollment of enrollments) {
    const hydrated = hydrateInstallationMetadata(enrollment);
    const installationId = hydrated.installationId ?? hydrated.id;
    const list = grouped.get(installationId) ?? [];
    list.push(hydrated);
    grouped.set(installationId, list);
  }

  const groups = Array.from(grouped.values()).map(items => {
    const [first] = items;
    const tenantGroups = new Map<string, StoredEnrollment[]>();
    const ungroupedEnrollments: StoredEnrollment[] = [];

    items.forEach(item => {
      const tenantName = normalizeTenantName(item.tenantName);
      if (!tenantName) {
        ungroupedEnrollments.push(item);
        return;
      }

      const tenantDescription = normalizeTenantDescription(item.tenantDescription);
      const key = JSON.stringify({
        tenantId: item.tenantId ?? null,
        tenantName,
        tenantDescription,
      });
      const list = tenantGroups.get(key) ?? [];
      list.push(item);
      tenantGroups.set(key, list);
    });

    const normalizedTenantGroups = Array.from(tenantGroups.entries())
      .map(([key, tenantItems]) => {
        const parsed = JSON.parse(key) as {
          tenantId: number | null;
          tenantName: string;
          tenantDescription?: string;
        };
        return {
          tenantId: parsed.tenantId ?? undefined,
          tenantName: parsed.tenantName,
          tenantDescription: parsed.tenantDescription,
          enrollments: sortEnrollmentsWithinGroup(tenantItems),
        };
      })
      .sort(sortTenantGroups);

    return {
      ...buildInstallationBranding(first),
      tenantGroups: normalizedTenantGroups,
      ungroupedEnrollments: sortEnrollmentsWithinGroup(ungroupedEnrollments),
    };
  });

  return groups.sort(sortInstallations);
}
