import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Building2, Plus, RefreshCw, Search } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip } from '@/components/ui/tooltip';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { useToast } from '@/context/toast-context';
import { useDebounce } from '@/hooks/use-debounce';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { ApiError } from '@/lib/api-client';
import { isDemoMode, tenantDemoPresets } from '@/lib/demo-mode';
import { getCountryOptionsGrouped } from '@/lib/countries';
import { getTimeZoneOptionsGrouped } from '@/lib/timezones';
import { buildListDetailNavState } from '@/lib/list-detail-navigation';
import { formatDate } from '@/lib/utils';
import { useCreateTenant, listTenants } from '@/generated/admin-api/tenants/tenants';
import type { PagedModelTenantResponseDto, TenantResponseDto } from '@/generated/admin-api/model';

// ── Create dialog ─────────────────────────────────────────────────────────────

function CreateTenantDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation('tenants');
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { sessionDemoOn } = useDemoModeSession();
  const countryGrouped = getCountryOptionsGrouped();
  const timezoneGrouped = getTimeZoneOptionsGrouped();

  const createSchema = useMemo(
    () =>
      z.object({
        tenantName: z.string().min(3, t('validation.minChars', { n: 3 })).max(100, t('validation.maxChars', { n: 100 })),
        tenantDescription: z.string().max(500).optional().or(z.literal('')),
        organizationName: z.string().max(255).optional().or(z.literal('')),
        organizationDomain: z
          .string()
          .regex(/^$|^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/, t('validation.validDomain'))
          .optional()
          .or(z.literal('')),
        countryCode: z
          .string()
          .regex(/^$|^[A-Z]{2}$/, t('validation.countryCode'))
          .optional()
          .or(z.literal('')),
        timezone: z.string().max(50).optional().or(z.literal('')),
        primaryContactName: z.string().max(255).optional().or(z.literal('')),
        primaryContactEmail: z.string().email(t('validation.invalidEmail')).optional().or(z.literal('')),
      }),
    [t],
  );
  type CreateFormValues = z.infer<typeof createSchema>;

  const { register, handleSubmit, reset, formState: { errors } } = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
  });

  const createMutation = useCreateTenant({
    mutation: {
      onSuccess: async (tenant) => {
        await queryClient.invalidateQueries({ queryKey: ['tenants'] });
        toast(t('create.toastSuccess', { name: (tenant as unknown as TenantResponseDto).tenantName }));
        reset();
        onClose();
      },
    },
  });

  const handleClose = () => {
    reset();
    createMutation.reset();
    onClose();
  };

  const onSubmit = (values: CreateFormValues) => {
    createMutation.mutate({
      data: {
        tenantName: values.tenantName,
        tenantDescription: values.tenantDescription || undefined,
        organizationName: values.organizationName || undefined,
        organizationDomain: values.organizationDomain || undefined,
        countryCode: values.countryCode || undefined,
        timezone: values.timezone || undefined,
        primaryContactName: values.primaryContactName || undefined,
        primaryContactEmail: values.primaryContactEmail || undefined,
      },
    });
  };

  return (
    <Dialog open={open} onClose={handleClose} title={t('create.title')} size="lg" dismissible={false}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {isDemoMode && sessionDemoOn && (
          <div className="flex flex-wrap items-center gap-2 p-2 border-2 border-accent/30 bg-accent/5">
            <span className="text-xs font-bold text-fg-muted uppercase tracking-wider">{t('create.fillDemo')}</span>
            {tenantDemoPresets.map((preset) => (
              <Button
                key={preset.id}
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => reset(preset.values)}
              >
                {preset.label}
              </Button>
            ))}
          </div>
        )}
        {/* Row 1 */}
        <div>
          <Label htmlFor="t-name">{t('create.tenantName')}</Label>
          <Input id="t-name" placeholder={t('create.tenantNamePlaceholder')} error={errors.tenantName?.message} {...register('tenantName')} />
        </div>
        <div>
          <Label htmlFor="t-desc">{t('create.description')}</Label>
          <Textarea id="t-desc" placeholder={t('create.descriptionPlaceholder')} rows={2} {...register('tenantDescription')} />
        </div>

        {/* Row 2 — Organization */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="t-orgname">{t('create.organizationName')}</Label>
            <Input id="t-orgname" placeholder={t('create.organizationNamePlaceholder')} {...register('organizationName')} />
          </div>
          <div>
            <Label htmlFor="t-orgdomain">{t('create.organizationDomain')}</Label>
            <Input id="t-orgdomain" placeholder={t('create.organizationDomainPlaceholder')} error={errors.organizationDomain?.message} {...register('organizationDomain')} />
          </div>
        </div>

        {/* Row 3 — Location */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="t-country">{t('create.country')}</Label>
            <Select id="t-country" error={errors.countryCode?.message} {...register('countryCode')}>
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
            <Label htmlFor="t-tz">{t('create.timezone')}</Label>
            <Select id="t-tz" {...register('timezone')}>
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

        {/* Row 4 — Contact */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="t-cname">{t('create.primaryContactName')}</Label>
            <Input id="t-cname" placeholder={t('create.primaryContactNamePlaceholder')} {...register('primaryContactName')} />
          </div>
          <div>
            <Label htmlFor="t-cemail">{t('create.primaryContactEmail')}</Label>
            <Input id="t-cemail" type="email" placeholder={t('create.primaryContactEmailPlaceholder')} error={errors.primaryContactEmail?.message} {...register('primaryContactEmail')} />
          </div>
        </div>

        {createMutation.isError && (
          <Alert variant="error">
            {createMutation.error instanceof ApiError
              ? createMutation.error.message
              : t('create.errorCreate')}
          </Alert>
        )}

        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="ghost" onClick={handleClose}>{t('create.cancel')}</Button>
          <Button type="submit" isLoading={createMutation.isPending}>
            <Building2 className="size-3.5 mr-1.5" />
            {t('create.submit')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function TenantsPage() {
  const { t } = useTranslation('tenants');
  const navigate = useNavigate();
  const [nameInput, setNameInput] = useState('');
  const [activeFilter, setActiveFilter] = useState<'all' | 'active' | 'inactive'>('all');
  const [createOpen, setCreateOpen] = useState(false);
  const debouncedName = useDebounce(nameInput, 300);

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<
    TenantResponseDto,
    { tenantName?: string; active?: boolean }
  >({
    queryKey: ['tenants', debouncedName, activeFilter],
    baseParams: {
      tenantName: debouncedName || undefined,
      active: activeFilter === 'all' ? undefined : activeFilter === 'active',
    },
    fetchPage: (params) => listTenants(params) as Promise<PagedModelTenantResponseDto>,
    defaultSort: 'createdAt,DESC',
  });

  const columns: ColumnDef<TenantResponseDto>[] = [
    { header: t('list.columns.id'), key: 'tenantId', className: 'w-14', sortKey: 'tenantId', render: (r) => <span className="font-mono text-xs">{r.tenantId}</span> },
    { header: t('list.columns.name'), key: 'tenantName', sortKey: 'tenantName', render: (r) => <span className="font-medium">{r.tenantName}</span> },
    {
      header: t('list.columns.organization'),
      key: 'organizationName',
      render: (r) => <span className="text-xs text-fg-muted">{r.organizationName ?? '—'}</span>,
    },
    {
      header: t('list.columns.domain'),
      key: 'organizationDomain',
      render: (r) => r.organizationDomain
        ? <span className="font-mono text-xs">{r.organizationDomain}</span>
        : <span className="text-fg-muted">—</span>,
    },
    {
      header: t('list.columns.status'),
      key: 'active',
      sortKey: 'active',
      headerTooltip: t('list.tooltipStatus'),
      render: (r) => (
        <div className="flex items-center gap-1.5">
          <Badge variant={r.active ? 'success' : 'muted'}>{r.active ? t('list.statusActive') : t('list.statusInactive')}</Badge>
          {r.isSystemTenant && (
            <Tooltip content={t('list.tooltipSystemTenant')}>
              <Badge variant="warning">{t('list.statusSystem')}</Badge>
            </Tooltip>
          )}
        </div>
      ),
    },
    {
      header: t('list.columns.country'),
      key: 'countryCode',
      render: (r) => <span className="text-xs">{r.countryCode ?? '—'}</span>,
    },
    { header: t('list.columns.created'), key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.createdAt ?? '')}</span> },
  ];

  return (
    <AppShell title={t('list.title')}>
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="flex-1 min-w-52 relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-fg-muted pointer-events-none" />
            <Input
              placeholder={t('list.searchPlaceholder')}
              value={nameInput}
              onChange={(e) => setNameInput(e.target.value)}
              className="pl-8"
            />
          </div>
          <div className="w-40">
            <Select value={activeFilter} onChange={(e) => setActiveFilter(e.target.value as typeof activeFilter)}>
              <option value="all">{t('list.filterAll')}</option>
              <option value="active">{t('list.filterActive')}</option>
              <option value="inactive">{t('list.filterInactive')}</option>
            </Select>
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="size-3.5" />
            {t('list.refresh')}
          </Button>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5 ml-auto">
            <Plus className="size-3.5" />
            {t('list.createTenant')}
          </Button>
        </div>

        {/* Table */}
        <div>
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => {
              const st = buildListDetailNavState(data, (r) => r.tenantId ?? 0, row, !pagination.isLast);
              navigate(`/tenants/${row.tenantId}`, { state: st ?? undefined });
            }}
            keyExtractor={(r, i) => r.tenantId ?? i}
            emptyMessage={t('list.emptyMessage')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>
      </div>

      <CreateTenantDialog open={createOpen} onClose={() => setCreateOpen(false)} />
    </AppShell>
  );
}
