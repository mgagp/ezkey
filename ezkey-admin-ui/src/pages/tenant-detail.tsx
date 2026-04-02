import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Edit, Power, PowerOff, UserPlus } from 'lucide-react';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
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
import { useListDetailPageNavigation } from '@/hooks/use-list-detail-page-navigation';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { DetailPageNav } from '@/components/ui/detail-page-nav';
import { ApiError } from '@/lib/api-client';
import { getCountryOptionsGrouped } from '@/lib/countries';
import { getTimeZoneOptionsGrouped } from '@/lib/timezones';
import { isPhoneNumberInputValid, normalizePhoneNumberInput } from '@/lib/phone-number';
import { formatDate } from '@/lib/utils';
import { listAdmins } from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import {
  getGetTenantQueryKey,
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

function EditTenantDialog({
  open,
  onClose,
  tenant,
}: {
  open: boolean;
  onClose: () => void;
  tenant: TenantResponseDto;
}) {
  const { t } = useTranslation('tenants');
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const editSchema = useMemo(
    () =>
      z.object({
        tenantName: z.string().min(3).max(100).optional().or(z.literal('')),
        tenantDescription: z.string().max(500).optional().or(z.literal('')),
        organizationName: z.string().max(255).optional().or(z.literal('')),
        organizationDomain: z.string().regex(/^$|^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/, t('validation.invalidDomain')).optional().or(z.literal('')),
        countryCode: z.string().regex(/^$|^[A-Z]{2}$/, t('validation.twoLetterCode')).optional().or(z.literal('')),
        timezone: z.string().max(50).optional().or(z.literal('')),
        primaryContactName: z.string().max(255).optional().or(z.literal('')),
        primaryContactEmail: z.string().email(t('validation.invalidEmail')).optional().or(z.literal('')),
        primaryContactPhoneNumber: z
          .string()
          .refine((value) => value === '' || isPhoneNumberInputValid(value), t('validation.invalidPhone'))
          .optional()
          .or(z.literal('')),
      }),
    [t],
  );
  type EditFormValues = z.infer<typeof editSchema>;

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
      primaryContactPhoneNumber: tenant.primaryContactPhoneNumber ?? '',
    },
  });

  const updateMutation = useUpdateTenant({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetTenantQueryKey(tenant.tenantId!) });
        await queryClient.invalidateQueries({ queryKey: ['tenants'] });
        toast(t('edit.toastSuccess'));
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
    if (values.primaryContactPhoneNumber !== undefined) {
      dto.primaryContactPhoneNumber = normalizePhoneNumberInput(values.primaryContactPhoneNumber);
    }
    updateMutation.mutate({ id: tenant.tenantId!, data: dto });
  };

  return (
    <Dialog open={open} onClose={onClose} title={t('edit.title')} size="lg" dismissible={false}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <Label htmlFor="e-name">{t('edit.tenantName')}</Label>
          <Input id="e-name" error={errors.tenantName?.message} {...register('tenantName')} />
        </div>
        <div>
          <Label htmlFor="e-desc">{t('detail.infoDescription')}</Label>
          <Textarea id="e-desc" rows={2} {...register('tenantDescription')} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="e-orgname">{t('create.organizationName')}</Label>
            <Input id="e-orgname" {...register('organizationName')} />
          </div>
          <div>
            <Label htmlFor="e-orgdomain">{t('create.organizationDomain')}</Label>
            <Input id="e-orgdomain" error={errors.organizationDomain?.message} {...register('organizationDomain')} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="e-country">{t('create.country')}</Label>
            <Select id="e-country" error={errors.countryCode?.message} {...register('countryCode')}>
              <option value="">—</option>
              <optgroup label={t('create.optgroupNorthAmerica')}>
                {countryGrouped.quickNorthAmerica.map(({ code, name }) => (
                  <option key={code} value={code}>{name} ({code})</option>
                ))}
              </optgroup>
              <optgroup label={t('create.optgroupEurope')}>
                {countryGrouped.quickEurope.map(({ code, name }) => (
                  <option key={code} value={code}>{name} ({code})</option>
                ))}
              </optgroup>
              <optgroup label={t('create.optgroupAllCountries')}>
                {countryGrouped.all.map(({ code, name }) => (
                  <option key={code} value={code}>{name} ({code})</option>
                ))}
              </optgroup>
            </Select>
          </div>
          <div>
            <Label htmlFor="e-tz">{t('create.timezone')}</Label>
            <Select id="e-tz" {...register('timezone')}>
              <option value="">—</option>
              <optgroup label={t('create.optgroupNorthAmerica')}>
                {timezoneGrouped.quickNorthAmerica.map((tz) => (
                  <option key={tz} value={tz}>{tz}</option>
                ))}
              </optgroup>
              <optgroup label={t('create.optgroupEurope')}>
                {timezoneGrouped.quickEurope.map((tz) => (
                  <option key={tz} value={tz}>{tz}</option>
                ))}
              </optgroup>
              <optgroup label={t('create.optgroupAllTimeZones')}>
                {timezoneGrouped.all.map((tz) => (
                  <option key={tz} value={tz}>{tz}</option>
                ))}
              </optgroup>
            </Select>
          </div>
        </div>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_minmax(0,0.8fr)]">
          <div>
            <Label htmlFor="e-cname">{t('create.primaryContactName')}</Label>
            <Input id="e-cname" {...register('primaryContactName')} />
          </div>
          <div>
            <Label htmlFor="e-cphone">{t('create.primaryContactPhone')}</Label>
            <Input id="e-cphone" error={errors.primaryContactPhoneNumber?.message} {...register('primaryContactPhoneNumber')} />
          </div>
        </div>
        <div>
          <Label htmlFor="e-cemail">{t('create.primaryContactEmail')}</Label>
          <Input id="e-cemail" type="email" error={errors.primaryContactEmail?.message} {...register('primaryContactEmail')} />
        </div>

        {updateMutation.isError && (
          <Alert variant="error">
            {updateMutation.error instanceof ApiError ? updateMutation.error.message : t('edit.errorUpdate')}
          </Alert>
        )}

        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>{t('edit.cancel')}</Button>
          <Button type="submit" isLoading={updateMutation.isPending}>{t('edit.saveChanges')}</Button>
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
  const { t } = useTranslation('tenants');
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const isActive = tenant.active;
  const [reason, setReason] = useState('');

  const body = { reason: reason || undefined };
  const deactivateMutation = useDeactivateTenant({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetTenantQueryKey(tenant.tenantId!) });
        await queryClient.invalidateQueries({ queryKey: ['tenants'] });
        toast(t('toggle.toastDeactivated'));
        setReason('');
        onClose();
      },
    },
  });
  const activateMutation = useActivateTenant({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetTenantQueryKey(tenant.tenantId!) });
        await queryClient.invalidateQueries({ queryKey: ['tenants'] });
        toast(t('toggle.toastActivated'));
        setReason('');
        onClose();
      },
    },
  });
  const mutation = isActive ? deactivateMutation : activateMutation;

  const handleClose = () => { mutation.reset(); setReason(''); onClose(); };

  return (
    <Dialog open={open} onClose={handleClose} title={isActive ? t('toggle.deactivateTitle') : t('toggle.activateTitle')} size="sm">
      <div className="space-y-4">
        <p className="text-sm">
          {isActive
            ? t('toggle.deactivateConfirm', { name: tenant.tenantName })
            : t('toggle.activateConfirm', { name: tenant.tenantName })}
        </p>
        <div className="space-y-1.5">
          <Label htmlFor="toggle-reason">{t('toggle.reasonLabel')} <span className="text-fg-muted font-normal">{t('toggle.reasonHint')}</span></Label>
          <Input
            id="toggle-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t('toggle.reasonPlaceholder')}
          />
          <DemoReasonBadges onSelect={setReason} />
        </div>

        {mutation.isError && (
          <Alert variant="error">
            {mutation.error instanceof ApiError ? mutation.error.message : t('toggle.errorOperation')}
          </Alert>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={handleClose}>{t('toggle.cancel')}</Button>
          <Button
            variant={isActive ? 'destructive' : 'primary'}
            isLoading={mutation.isPending}
            onClick={() => {
              if (isActive) deactivateMutation.mutate({ id: tenant.tenantId!, data: body });
              else activateMutation.mutate({ id: tenant.tenantId!, data: body });
            }}
            disabled={
              mutation.isPending
              || mutation.isSuccess
              || (reason.length > 0 && reason.length < 10)
            }
          >
            {isActive ? <PowerOff className="size-3.5 mr-1.5" /> : <Power className="size-3.5 mr-1.5" />}
            {isActive ? t('toggle.deactivate') : t('toggle.activate')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

// ── Admin type badge ──────────────────────────────────────────────────────────

function AdminTypeBadge({ type }: { type: AdminResponseDto['adminType'] }) {
  const { t } = useTranslation('tenants');
  if (type === 'GLOBAL_ADMIN') return <Badge variant="warning">{t('detail.adminTypeGlobal')}</Badge>;
  return <Badge variant="muted">{t('detail.adminTypeTenant')}</Badge>;
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function TenantDetailPage() {
  const { t } = useTranslation('tenants');
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const tenantId = Number(id);

  const { nav: tenantListNav, goPrev: goPrevTenant, goNext: goNextTenant, showEndOfPageHint: showTenantListEndHint } =
    useListDetailPageNavigation({
      currentId: tenantId,
      pathPrefix: '/tenants',
    });

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
    { header: t('detail.adminColumns.id'), key: 'adminId', className: 'w-14', sortKey: 'adminId', render: (r) => <span className="font-mono text-xs">{r.adminId}</span> },
    { header: t('detail.adminColumns.username'), key: 'username', sortKey: 'username', render: (r) => <span className="font-medium">{r.username}</span> },
    { header: t('detail.adminColumns.type'), key: 'adminType', render: (r) => <AdminTypeBadge type={r.adminType} /> },
    { header: t('detail.adminColumns.active'), key: 'active', render: (r) => <Badge variant={r.active ? 'success' : 'muted'}>{r.active ? t('detail.activeYes') : t('detail.activeNo')}</Badge> },
    { header: t('detail.adminColumns.created'), key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.createdAt ?? '')}</span> },
  ];

  return (
    <AppShell
      title={tenant?.tenantName ?? t('detail.fallbackTitle')}
      detailNav={
        tenantListNav ? (
          <DetailPageNav
            hasPrev={tenantListNav.prevId !== undefined}
            hasNext={tenantListNav.nextId !== undefined}
            onPrev={goPrevTenant}
            onNext={goNextTenant}
            showEndOfPageHint={showTenantListEndHint}
          />
        ) : undefined
      }
      breadcrumb={[{ label: t('detail.breadcrumbTenants'), path: '/tenants' }]}
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
                <CardHeader><CardTitle>{t('detail.tenantDetails')}</CardTitle></CardHeader>
                <CardContent>
                  <dl className="space-y-3">
                    <InfoRow label={t('detail.infoId')}><span className="font-mono">{tenant.tenantId}</span></InfoRow>
                    <InfoRow label={t('detail.infoName')}><span className="font-medium">{tenant.tenantName}</span></InfoRow>
                    {tenant.tenantDescription && (
                      <InfoRow label={t('detail.infoDescription')}><span className="text-fg-muted">{tenant.tenantDescription}</span></InfoRow>
                    )}
                    <InfoRow label={t('detail.infoStatus')}>
                      <div className="flex items-center gap-1.5">
                        <Badge variant={tenant.active ? 'success' : 'muted'}>
                          {tenant.active ? t('list.statusActive') : t('list.statusInactive')}
                        </Badge>
                        {tenant.isSystemTenant && <Badge variant="warning">{t('detail.systemTenant')}</Badge>}
                      </div>
                    </InfoRow>
                    {tenant.organizationName && (
                      <InfoRow label={t('detail.infoOrganization')}><span>{tenant.organizationName}</span></InfoRow>
                    )}
                    {tenant.organizationDomain && (
                      <InfoRow label={t('detail.infoDomain')}><span className="font-mono">{tenant.organizationDomain}</span></InfoRow>
                    )}
                    {tenant.countryCode && (
                      <InfoRow label={t('detail.infoCountry')}><span>{tenant.countryCode}</span></InfoRow>
                    )}
                    {tenant.timezone && (
                      <InfoRow label={t('detail.infoTimezone')}><span>{tenant.timezone}</span></InfoRow>
                    )}
                    {tenant.primaryContactName && (
                      <InfoRow label={t('detail.infoContactName')}><span>{tenant.primaryContactName}</span></InfoRow>
                    )}
                    {tenant.primaryContactEmail && (
                      <InfoRow label={t('detail.infoContactEmail')}><span>{tenant.primaryContactEmail}</span></InfoRow>
                    )}
                    {tenant.primaryContactPhoneNumber && (
                      <InfoRow label={t('detail.infoContactPhone')}><span>{tenant.primaryContactPhoneNumber}</span></InfoRow>
                    )}
                    <InfoRow label={t('detail.infoCreated')}><span className="text-fg-muted">{formatDate(tenant.createdAt ?? '')}</span></InfoRow>
                    {tenant.updatedAt && (
                      <InfoRow label={t('detail.infoUpdated')}><span className="text-fg-muted">{formatDate(tenant.updatedAt)}</span></InfoRow>
                    )}
                    {tenant.deactivatedAt && (
                      <InfoRow label={t('detail.infoDeactivatedAt')}><span className="text-error">{formatDate(tenant.deactivatedAt)}</span></InfoRow>
                    )}
                  </dl>
                </CardContent>
              </Card>

              {/* Actions card */}
              <div className="space-y-4">
                <Card>
                  <CardHeader><CardTitle>{t('detail.actions')}</CardTitle></CardHeader>
                  <CardContent className="space-y-2">
                    {!tenant.isSystemTenant && (
                      <Button
                        variant="secondary"
                        size="sm"
                        className="w-full justify-start gap-2"
                        onClick={() => setEditOpen(true)}
                      >
                        <Edit className="size-3.5" />
                        {t('detail.editTenant')}
                      </Button>
                    )}
                    {!tenant.isSystemTenant && (
                      <Button
                        variant="secondary"
                        size="sm"
                        className="w-full justify-start gap-2"
                        onClick={() =>
                          navigate(`/admins?tenantId=${tenant.tenantId}&createTenantAdmin=1`)
                        }
                      >
                        <UserPlus className="size-3.5" />
                        {t('detail.addTenantAdmin')}
                      </Button>
                    )}
                    <Button
                      variant="secondary"
                      size="sm"
                      className="w-full justify-start gap-2"
                      onClick={() => navigate(`/integrations?tenantId=${tenant.tenantId}`)}
                    >
                      {t('detail.viewIntegrations')}
                    </Button>
                  </CardContent>
                </Card>

                {/* Danger zone */}
                {!tenant.isSystemTenant && (
                  <Card className="border-error">
                    <CardHeader><CardTitle>{t('detail.dangerZone')}</CardTitle></CardHeader>
                    <CardContent>
                      <p className="text-xs text-fg-muted mb-3">
                        {tenant.active
                          ? t('detail.deactivateWarning')
                          : t('detail.activateWarning')}
                      </p>
                      <Button
                        variant={tenant.active ? 'destructive' : 'primary'}
                        size="sm"
                        className="gap-1.5"
                        onClick={() => setToggleOpen(true)}
                      >
                        {tenant.active ? <PowerOff className="size-3.5" /> : <Power className="size-3.5" />}
                        {tenant.active ? t('detail.deactivateTenant') : t('detail.activateTenant')}
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
                  {t('detail.adminsSectionTitle')}
                </h3>
              </div>
              <PaginatedTable
                columns={adminColumns}
                data={admins}
                isLoading={loadingAdmins}
                onRowClick={(row) => navigate(`/admins?adminId=${row.adminId}`)}
                keyExtractor={(r, i) => r.adminId ?? i}
                emptyMessage={t('detail.adminsEmpty')}
                currentSort={admPagination.sort}
                onSort={admPagination.setSort}
                pagination={admPagination}
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
