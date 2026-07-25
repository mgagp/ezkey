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

import type {EnrollmentMetadataRecord} from '../services/storage/enrollmentStorage';
import type {Installation} from '../services/api/types';
import {
  hydrateInstallationMetadata,
  shouldShowInstallationHostHint,
} from './installationMetadata';

/**
 * Group of enrollments belonging to a single tenant.
 *
 * Rows are display-safe metadata records so enrollments whose secrets are unusable (MOB-015)
 * can be grouped and rendered alongside healthy ones.
 *
 * @since 2025
 */
export type TenantGroup = {
  tenantId?: number;
  tenantName: string;
  tenantDescription?: string;
  enrollments: EnrollmentMetadataRecord[];
};

export type InstallationGroup = {
  installation: Installation;
  showHostHint: boolean;
  tenantGroups: TenantGroup[];
  ungroupedEnrollments: EnrollmentMetadataRecord[];
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
function sortEnrollmentsWithinGroup(items: EnrollmentMetadataRecord[]): EnrollmentMetadataRecord[] {
  return [...items].sort((left, right) => {
    if (left.favorited && !right.favorited) return -1;
    if (!left.favorited && right.favorited) return 1;
    return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
  });
}

function sortInstallations(left: InstallationGroup, right: InstallationGroup): number {
  const nameCmp = left.installation.name.localeCompare(right.installation.name, undefined, {
    sensitivity: 'base',
  });
  if (nameCmp !== 0) {
    return nameCmp;
  }

  return (left.installation.host ?? '').localeCompare(right.installation.host ?? '', undefined, {
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

function buildInstallationGroup(enrollment: EnrollmentMetadataRecord): Pick<InstallationGroup, 'installation' | 'showHostHint'> {
  const hydrated = hydrateInstallationMetadata(enrollment);
  const installation = hydrated.installation ?? {
    id: hydrated.id,
    name: 'Ezkey installation',
  };

  return {
    installation,
    showHostHint: shouldShowInstallationHostHint(installation),
  };
}

/**
 * Groups enrollments by installation, then by tenant where tenant metadata is present.
 *
 * @param enrollments Enrollments to group (assumed pre-sorted if needed)
 * @return Ordered list of installation groups
 * @since 2025
 */
export function groupEnrollmentsByInstallation(enrollments: EnrollmentMetadataRecord[]): InstallationGroup[] {
  const grouped = new Map<string, EnrollmentMetadataRecord[]>();

  for (const enrollment of enrollments) {
    const hydrated = hydrateInstallationMetadata(enrollment);
    const installationId = hydrated.installation?.id ?? hydrated.id;
    const list = grouped.get(installationId) ?? [];
    list.push(hydrated);
    grouped.set(installationId, list);
  }

  const groups = Array.from(grouped.values()).map(items => {
    const [first] = items;
    const tenantGroups = new Map<string, EnrollmentMetadataRecord[]>();
    const ungroupedEnrollments: EnrollmentMetadataRecord[] = [];

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
      ...buildInstallationGroup(first),
      tenantGroups: normalizedTenantGroups,
      ungroupedEnrollments: sortEnrollmentsWithinGroup(ungroupedEnrollments),
    };
  });

  return groups.sort(sortInstallations);
}

export const groupEnrollmentsByTenant = groupEnrollmentsByInstallation;
