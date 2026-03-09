import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Edit, Power, PowerOff } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useToast } from '@/context/toast-context';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { ApiError } from '@/lib/api-client';
import { getCountryOptionsGrouped } from '@/lib/countries';
import { getTimeZoneOptionsGrouped } from '@/lib/timezones';
import { formatDate } from '@/lib/utils';
import { listAdmins } from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import {
  getGetTenantQueryKey,
  getListTenantsQueryKey,
  useActivateTenant,
  useDeactivateTenant,
  useGetTenant,
  useUpdateTenant,
} from '@/generated/admin-api/tenants/tenants';
import type {
  AdminResponseDto,
  PagedModelAdminResponseDto,
  TenantResponseDto,
  TenantUpdateRequestDto,
} from '@/generated/admin-api/model';

// ── Info row helper ──────────────────────────────────────────────────────────

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-4">
      <dt className="w-40 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">
        {label}
      </dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

// ── Edit tenant dialog ────────────────────────────────────────────────────────

const editSchema = z.object({
  tenantName: z.string().min(3).max(100).optional().or(z.literal('')),
  tenantDescription: z.string().max(500).optional().or(z.literal('')),
  organizationName: z.string().max(255).optional().or(z.literal('')),
  organizationDomain: z.string().regex(/^$|^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/, 'Invalid domain').optional().or(z.literal('')),
  countryCode: z.string().regex(/^$|^[A-Z]{2}$/, '2-letter code').optional().or(z.literal('')),
  timezone: z.string().max(50).optional().or(z.literal('')),
  primaryContactName: z.string().max(255).optional().or(z.literal('')),
  primaryContactEmail: z.string().email('Invalid email').optional().or(z.literal('')),
});
type EditFormValues = z.infer<typeof editSchema>;

function EditTenantDialog({
  open,
  onClose,
  tenant,
}: {
  open: boolean;
  onClose: () => void;
  tenant: TenantResponseDto;
}) {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const countryGrouped = getCountryOptionsGrouped();
  const timezoneGrouped = getTimeZoneOptionsGrouped();
  const { register, handleSubmit, formState: { errors } } = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: {
      tenantName: tenant.tenantName ?? '',
      tenantDescription: tenant.tenantDescription ?? '',
      organizationName: tenant.organizationName ?? '',
      organizationDomain: tenant.organizationDomain ?? '',
      countryCode: tenant.countryCode ?? '',
      timezone: tenant.timezone ?? '',
      primaryContactName: tenant.primaryContactName ?? '',
      primaryContactEmail: tenant.primaryContactEmail ?? '',
    },
  });

  const updateMutation = useUpdateTenant({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetTenantQueryKey(tenant.tenantId!) });
        await queryClient.invalidateQueries({ queryKey: getListTenantsQueryKey() });
        toast('Tenant updated successfully.');
        onClose();
      },
    },
  });

  const onSubmit = (values: EditFormValues) => {
    const dto: TenantUpdateRequestDto = {};
    if (values.tenantName) dto.tenantName = values.tenantName;
    if (values.tenantDescription !== undefined) dto.tenantDescription = values.tenantDescription || undefined;
    if (values.organizationName !== undefined) dto.organizationName = values.organizationName || undefined;
    if (values.organizationDomain !== undefined) dto.organizationDomain = values.organizationDomain || undefined;
    if (values.countryCode !== undefined) dto.countryCode = values.countryCode || undefined;
    if (values.timezone !== undefined) dto.timezone = values.timezone || undefined;
    if (values.primaryContactName !== undefined) dto.primaryContactName = values.primaryContactName || undefined;
    if (values.primaryContactEmail !== undefined) dto.primaryContactEmail = values.primaryContactEmail || undefined;
    updateMutation.mutate({ id: tenant.tenantId!, data: dto });
  };

  return (
    <Dialog open={open} onClose={onClose} title="Edit Tenant" size="lg" dismissible={false}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <Label htmlFor="e-name">Tenant Name</Label>
          <Input id="e-name" error={errors.tenantName?.message} {...register('tenantName')} />
        </div>
        <div>
          <Label htmlFor="e-desc">Description</Label>
          <Textarea id="e-desc" rows={2} {...register('tenantDescription')} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="e-orgname">Organization Name</Label>
            <Input id="e-orgname" {...register('organizationName')} />
          </div>
          <div>
            <Label htmlFor="e-orgdomain">Organization Domain</Label>
            <Input id="e-orgdomain" error={errors.organizationDomain?.message} {...register('organizationDomain')} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="e-country">Country</Label>
            <Select id="e-country" error={errors.countryCode?.message} {...register('countryCode')}>
              <option value="">—</option>
              <optgroup label="North America">
                {countryGrouped.quickNorthAmerica.map(({ code, name }) => (
                  <option key={code} value={code}>{name} ({code})</option>
                ))}
              </optgroup>
              <optgroup label="Europe (France &amp; nearby)">
                {countryGrouped.quickEurope.map(({ code, name }) => (
                  <option key={code} value={code}>{name} ({code})</option>
                ))}
              </optgroup>
              <optgroup label="All countries">
                {countryGrouped.all.map(({ code, name }) => (
                  <option key={code} value={code}>{name} ({code})</option>
                ))}
              </optgroup>
            </Select>
          </div>
          <div>
            <Label htmlFor="e-tz">Timezone</Label>
            <Select id="e-tz" {...register('timezone')}>
              <option value="">—</option>
              <optgroup label="North America">
                {timezoneGrouped.quickNorthAmerica.map((tz) => (
                  <option key={tz} value={tz}>{tz}</option>
                ))}
              </optgroup>
              <optgroup label="Europe (France &amp; nearby)">
                {timezoneGrouped.quickEurope.map((tz) => (
                  <option key={tz} value={tz}>{tz}</option>
                ))}
              </optgroup>
              <optgroup label="All time zones">
                {timezoneGrouped.all.map((tz) => (
                  <option key={tz} value={tz}>{tz}</option>
                ))}
              </optgroup>
            </Select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="e-cname">Primary Contact Name</Label>
            <Input id="e-cname" {...register('primaryContactName')} />
          </div>
          <div>
            <Label htmlFor="e-cemail">Primary Contact Email</Label>
            <Input id="e-cemail" type="email" error={errors.primaryContactEmail?.message} {...register('primaryContactEmail')} />
          </div>
        </div>

        {updateMutation.isError && (
          <Alert variant="error">
            {updateMutation.error instanceof ApiError ? updateMutation.error.message : 'Failed to update tenant.'}
          </Alert>
        )}

        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" isLoading={updateMutation.isPending}>Save Changes</Button>
        </div>
      </form>
    </Dialog>
  );
}

// ── Activate / Deactivate dialog ──────────────────────────────────────────────

function ToggleActiveDialog({
  open,
  onClose,
  tenant,
}: {
  open: boolean;
  onClose: () => void;
  tenant: TenantResponseDto;
}) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const isActive = tenant.active;
  const [reason, setReason] = useState('');

  const body = { reason: reason || undefined };
  const deactivateMutation = useDeactivateTenant({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetTenantQueryKey(tenant.tenantId!) });
        await queryClient.invalidateQueries({ queryKey: getListTenantsQueryKey() });
        toast('Tenant deactivated successfully.');
        setReason('');
        onClose();
      },
    },
  });
  const activateMutation = useActivateTenant({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetTenantQueryKey(tenant.tenantId!) });
        await queryClient.invalidateQueries({ queryKey: getListTenantsQueryKey() });
        toast('Tenant activated successfully.');
        setReason('');
        onClose();
      },
    },
  });
  const mutation = isActive ? deactivateMutation : activateMutation;

  const handleClose = () => { mutation.reset(); setReason(''); onClose(); };

  return (
    <Dialog open={open} onClose={handleClose} title={isActive ? 'Deactivate Tenant' : 'Activate Tenant'} size="sm">
      <div className="space-y-4">
        <p className="text-sm">
          {isActive
            ? <>Are you sure you want to deactivate <strong>{tenant.tenantName}</strong>? All integrations and enrollments under this tenant will become inaccessible.</>
            : <>Re-activate <strong>{tenant.tenantName}</strong>? All resources under this tenant will become accessible again.</>
          }
        </p>
        <div className="space-y-1.5">
          <Label htmlFor="toggle-reason">Reason <span className="text-fg-muted font-normal">(min 10 chars, for audit trail)</span></Label>
          <Input
            id="toggle-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Justification for this action..."
          />
        </div>

        {mutation.isError && (
          <Alert variant="error">
            {mutation.error instanceof ApiError ? mutation.error.message : 'Operation failed.'}
          </Alert>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={handleClose}>Cancel</Button>
          <Button
            variant={isActive ? 'destructive' : 'primary'}
            isLoading={mutation.isPending}
            onClick={() => {
              if (isActive) deactivateMutation.mutate({ id: tenant.tenantId!, data: body });
              else activateMutation.mutate({ id: tenant.tenantId!, data: body });
            }}
            disabled={reason.length > 0 && reason.length < 10}
          >
            {isActive ? <PowerOff className="size-3.5 mr-1.5" /> : <Power className="size-3.5 mr-1.5" />}
            {isActive ? 'Deactivate' : 'Activate'}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

// ── Admin type badge ──────────────────────────────────────────────────────────

function AdminTypeBadge({ type }: { type: AdminResponseDto['adminType'] }) {
  if (type === 'GLOBAL_ADMIN') return <Badge variant="warning">Global</Badge>;
  return <Badge variant="muted">Tenant</Badge>;
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function TenantDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const tenantId = Number(id);

  const [editOpen, setEditOpen] = useState(false);
  const [toggleOpen, setToggleOpen] = useState(false);

  const { data: tenant, isLoading } = useGetTenant<TenantResponseDto>(
    isNaN(tenantId) ? 0 : tenantId,
    { query: { enabled: !isNaN(tenantId) } },
  );

  // Admins list for this tenant (Global Admin can see cross-tenant)
  const { data: admins, pagination: admPagination, isLoading: loadingAdmins } = usePaginatedFromOrval<AdminResponseDto, { tenantId?: number }>({
    queryKey: ['admins', 'for-tenant', tenantId],
    baseParams: { tenantId: isNaN(tenantId) ? undefined : tenantId },
    defaultSize: 10,
    fetchPage: (params) => listAdmins(params) as Promise<PagedModelAdminResponseDto>,
  });

  const adminColumns: ColumnDef<AdminResponseDto>[] = [
    { header: 'ID', key: 'adminId', className: 'w-14', sortKey: 'adminId', render: (r) => <span className="font-mono text-xs">{r.adminId}</span> },
    { header: 'Username', key: 'username', sortKey: 'username', render: (r) => <span className="font-medium">{r.username}</span> },
    { header: 'Type', key: 'adminType', render: (r) => <AdminTypeBadge type={r.adminType} /> },
    { header: 'Active', key: 'active', render: (r) => <Badge variant={r.active ? 'success' : 'muted'}>{r.active ? 'Yes' : 'No'}</Badge> },
    { header: 'Created', key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.createdAt ?? '')}</span> },
  ];

  return (
    <AppShell
      title={tenant?.tenantName ?? 'Tenant'}
      breadcrumb={[{ label: 'Tenants', path: '/tenants' }]}
    >
      <div className="space-y-6">

        {isLoading && (
          <div className="flex justify-center py-12">
            <span className="size-6 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
          </div>
        )}

        {tenant && (
          <>
            {/* Detail cards */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              <Card className="lg:col-span-2">
                <CardHeader><CardTitle>Tenant Details</CardTitle></CardHeader>
                <CardContent>
                  <dl className="space-y-3">
                    <InfoRow label="ID"><span className="font-mono">{tenant.tenantId}</span></InfoRow>
                    <InfoRow label="Name"><span className="font-medium">{tenant.tenantName}</span></InfoRow>
                    {tenant.tenantDescription && (
                      <InfoRow label="Description"><span className="text-fg-muted">{tenant.tenantDescription}</span></InfoRow>
                    )}
                    <InfoRow label="Status">
                      <div className="flex items-center gap-1.5">
                        <Badge variant={tenant.active ? 'success' : 'muted'}>
                          {tenant.active ? 'Active' : 'Inactive'}
                        </Badge>
                        {tenant.isSystemTenant && <Badge variant="warning">System Tenant</Badge>}
                      </div>
                    </InfoRow>
                    {tenant.organizationName && (
                      <InfoRow label="Organization"><span>{tenant.organizationName}</span></InfoRow>
                    )}
                    {tenant.organizationDomain && (
                      <InfoRow label="Domain"><span className="font-mono">{tenant.organizationDomain}</span></InfoRow>
                    )}
                    {tenant.countryCode && (
                      <InfoRow label="Country"><span>{tenant.countryCode}</span></InfoRow>
                    )}
                    {tenant.timezone && (
                      <InfoRow label="Timezone"><span>{tenant.timezone}</span></InfoRow>
                    )}
                    {tenant.primaryContactName && (
                      <InfoRow label="Contact Name"><span>{tenant.primaryContactName}</span></InfoRow>
                    )}
                    {tenant.primaryContactEmail && (
                      <InfoRow label="Contact Email"><span>{tenant.primaryContactEmail}</span></InfoRow>
                    )}
                    <InfoRow label="Created"><span className="text-fg-muted">{formatDate(tenant.createdAt ?? '')}</span></InfoRow>
                    {tenant.updatedAt && (
                      <InfoRow label="Updated"><span className="text-fg-muted">{formatDate(tenant.updatedAt)}</span></InfoRow>
                    )}
                    {tenant.deactivatedAt && (
                      <InfoRow label="Deactivated At"><span className="text-error">{formatDate(tenant.deactivatedAt)}</span></InfoRow>
                    )}
                  </dl>
                </CardContent>
              </Card>

              {/* Actions card */}
              <div className="space-y-4">
                <Card>
                  <CardHeader><CardTitle>Actions</CardTitle></CardHeader>
                  <CardContent className="space-y-2">
                    {!tenant.isSystemTenant && (
                      <Button
                        variant="secondary"
                        size="sm"
                        className="w-full justify-start gap-2"
                        onClick={() => setEditOpen(true)}
                      >
                        <Edit className="size-3.5" />
                        Edit Tenant
                      </Button>
                    )}
                    <Button
                      variant="secondary"
                      size="sm"
                      className="w-full justify-start gap-2"
                      onClick={() => navigate(`/integrations?tenantId=${tenant.tenantId}`)}
                    >
                      View Integrations
                    </Button>
                  </CardContent>
                </Card>

                {/* Danger zone */}
                {!tenant.isSystemTenant && (
                  <Card className="border-error">
                    <CardHeader><CardTitle>Danger Zone</CardTitle></CardHeader>
                    <CardContent>
                      <p className="text-xs text-fg-muted mb-3">
                        {tenant.active
                          ? 'Deactivating a tenant will make all its integrations, enrollments, and admin accounts inaccessible.'
                          : 'This tenant is currently inactive. Re-activating will restore access to all resources.'}
                      </p>
                      <Button
                        variant={tenant.active ? 'destructive' : 'primary'}
                        size="sm"
                        className="gap-1.5"
                        onClick={() => setToggleOpen(true)}
                      >
                        {tenant.active ? <PowerOff className="size-3.5" /> : <Power className="size-3.5" />}
                        {tenant.active ? 'Deactivate Tenant' : 'Activate Tenant'}
                      </Button>
                    </CardContent>
                  </Card>
                )}
              </div>
            </div>

            {/* Admins section */}
            <div className="border-2 border-fg shadow-brutal bg-surface">
              <div className="px-4 py-3 border-b-2 border-fg bg-bg">
                <h3 className="text-xs font-black uppercase tracking-widest text-fg-muted">
                  Administrators for this Tenant
                </h3>
              </div>
              <DataTable
                columns={adminColumns}
                data={admins}
                isLoading={loadingAdmins}
                onRowClick={(row) => navigate(`/admins?adminId=${row.adminId}`)}
                keyExtractor={(r, i) => r.adminId ?? i}
                emptyMessage="No admins found for this tenant."
                currentSort={admPagination.sort}
                onSort={admPagination.setSort}
              />
              <Pagination
                page={admPagination.page}
                totalPages={admPagination.totalPages}
                totalElements={admPagination.totalElements}
                isFirst={admPagination.isFirst}
                isLast={admPagination.isLast}
                onPrevPage={admPagination.prevPage}
                onNextPage={admPagination.nextPage}
                pageSize={admPagination.size}
                onPageSizeChange={admPagination.setPageSize}
              />
            </div>

            {/* Dialogs */}
            <EditTenantDialog
              open={editOpen}
              onClose={() => setEditOpen(false)}
              tenant={tenant}
            />
            <ToggleActiveDialog
              open={toggleOpen}
              onClose={() => setToggleOpen(false)}
              tenant={tenant}
            />
          </>
        )}
      </div>
    </AppShell>
  );
}
