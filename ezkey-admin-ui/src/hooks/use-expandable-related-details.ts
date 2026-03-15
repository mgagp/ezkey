import { useCallback, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getAdminById, getGetAdminByIdQueryKey } from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import { getById1, getGetById1QueryKey } from '@/generated/admin-api/integrations/integrations';
import { getById, getGetByIdQueryKey } from '@/generated/admin-api/enrollments/enrollments';
import { getTenant, getGetTenantQueryKey } from '@/generated/admin-api/tenants/tenants';
import type { AdminResponseDto } from '@/generated/admin-api/model';
import type { EnrollmentResponseDto } from '@/generated/admin-api/model';
import type { IntegrationResponseDto } from '@/generated/admin-api/model';
import type { TenantResponseDto } from '@/generated/admin-api/model';

export interface ExpandableRelatedDetailsParams {
  tenantId?: number | null;
  integrationId?: number | null;
  enrollmentId?: number | null;
  adminId?: number | null;
}

export interface ExpandableRelatedDetailsResult {
  expand: () => void;
  isExpanded: boolean;
  tenant: TenantResponseDto | undefined;
  integration: IntegrationResponseDto | undefined;
  enrollment: EnrollmentResponseDto | undefined;
  admin: AdminResponseDto | undefined;
  isLoading: boolean;
  hasAnyFk: boolean;
}

/**
 * Optional on-demand loading of related entities by foreign key IDs.
 * When the user clicks "More details", fetches tenant, integration, enrollment, admin
 * (for whichever IDs are present) and exposes them for inline display.
 * Queries are enabled only when isExpanded is true to avoid extra API calls by default.
 */
export function useExpandableRelatedDetails(
  params: ExpandableRelatedDetailsParams,
): ExpandableRelatedDetailsResult {
  const { tenantId, integrationId, enrollmentId, adminId } = params;
  const [isExpanded, setIsExpanded] = useState(false);
  const expand = useCallback(() => setIsExpanded(true), []);

  const hasAnyFk = useMemo(
    () =>
      (tenantId != null && Number.isFinite(tenantId)) ||
      (integrationId != null && Number.isFinite(integrationId)) ||
      (enrollmentId != null && Number.isFinite(enrollmentId)) ||
      (adminId != null && Number.isFinite(adminId)),
    [tenantId, integrationId, enrollmentId, adminId],
  );

  const tenantEnabled = isExpanded && tenantId != null && Number.isFinite(tenantId);
  const integrationEnabled = isExpanded && integrationId != null && Number.isFinite(integrationId);
  const enrollmentEnabled = isExpanded && enrollmentId != null && Number.isFinite(enrollmentId);
  const adminEnabled = isExpanded && adminId != null && Number.isFinite(adminId);

  const { data: tenant, isLoading: loadingTenant } = useQuery({
    queryKey: getGetTenantQueryKey(tenantId ?? 0),
    queryFn: () => getTenant(tenantId!) as Promise<TenantResponseDto>,
    enabled: tenantEnabled,
  });

  const { data: integration, isLoading: loadingIntegration } = useQuery({
    queryKey: getGetById1QueryKey(integrationId ?? 0),
    queryFn: () => getById1(integrationId!) as Promise<IntegrationResponseDto>,
    enabled: integrationEnabled,
  });

  const { data: enrollment, isLoading: loadingEnrollment } = useQuery({
    queryKey: getGetByIdQueryKey(enrollmentId ?? 0),
    queryFn: () => getById(enrollmentId!) as Promise<EnrollmentResponseDto>,
    enabled: enrollmentEnabled,
  });

  const { data: admin, isLoading: loadingAdmin } = useQuery({
    queryKey: getGetAdminByIdQueryKey(adminId ?? 0),
    queryFn: () => getAdminById(adminId!) as Promise<AdminResponseDto>,
    enabled: adminEnabled,
  });

  const isLoading =
    (tenantEnabled && loadingTenant) ||
    (integrationEnabled && loadingIntegration) ||
    (enrollmentEnabled && loadingEnrollment) ||
    (adminEnabled && loadingAdmin);

  return {
    expand,
    isExpanded,
    tenant: tenantEnabled ? tenant : undefined,
    integration: integrationEnabled ? integration : undefined,
    enrollment: enrollmentEnabled ? enrollment : undefined,
    admin: adminEnabled ? admin : undefined,
    isLoading,
    hasAnyFk,
  };
}
