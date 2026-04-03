import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Plus, RefreshCw, Search } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { HelpInlineButton } from '@/components/help/help-inline-button';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { useDebounce } from '@/hooks/use-debounce';
import { getIntegrationName } from '@/hooks/use-integrations';
import { ApiError } from '@/lib/api-client';
import { integrationDemoPresets, isDemoMode, resolveIntegrationDemoPresetForLocale } from '@/lib/demo-mode';
import { buildListDetailNavState } from '@/lib/list-detail-navigation';
import { formatDate } from '@/lib/utils';
import { create, search } from '@/generated/admin-api/integrations/integrations';
import type { IntegrationCreateRequestDto, IntegrationCreateResponseDto, IntegrationResponseDto, PagedModelIntegrationResponseDto } from '@/generated/admin-api/model';

type IntegrationLifecycleStatus = 'ACTIVE' | 'INACTIVE' | 'RETIRED';
type IntegrationListFilter = 'operational' | 'active' | 'inactive' | 'retired' | 'all';

type IntegrationWithLifecycleStatus = IntegrationResponseDto & {
  lifecycleStatus?: IntegrationLifecycleStatus;
};

function getLifecycleStatus(integration: IntegrationResponseDto): IntegrationLifecycleStatus {
  const lifecycleStatus = (integration as IntegrationWithLifecycleStatus).lifecycleStatus;
  if (lifecycleStatus) {
    return lifecycleStatus;
  }
  // Transitional fallback until the derived `active` field is removed from the API surface.
  return integration.active ? 'ACTIVE' : 'INACTIVE';
}

// ── Create dialog ─────────────────────────────────────────────────────────────

function CreateIntegrationDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t, i18n } = useTranslation(['integrations', 'demo']);
  const integrationPresetsForLocale = useMemo(
    () => integrationDemoPresets.map((preset) => resolveIntegrationDemoPresetForLocale(preset, t)),
    [t, i18n.language],
  );
  const queryClient = useQueryClient();
  const { sessionDemoOn } = useDemoModeSession();

  const createSchema = useMemo(
    () =>
      z.object({
        code: z
          .string()
          .min(2, t('validation.codeMin', { n: 2 }))
          .max(100, t('validation.codeMax', { n: 100 }))
          .regex(/^[a-zA-Z0-9_-]+$/, t('validation.codeRegex')),
        name: z.string().min(1, t('validation.nameRequired')).max(255, t('validation.nameMax', { n: 255 })),
        description: z.string().max(500).optional().or(z.literal('')),
      }),
    [t],
  );
  type CreateFormValues = z.infer<typeof createSchema>;

  const { register, handleSubmit, reset, formState: { errors } } = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
  });

  const createMutation = useMutation({
    mutationFn: (data: IntegrationCreateRequestDto) =>
      create(data) as Promise<IntegrationCreateResponseDto>,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['integrations'] });
      void queryClient.invalidateQueries({ queryKey: ['integrations-all'] });
      reset();
      onClose();
    },
  });

  const handleClose = () => {
    reset();
    createMutation.reset();
    onClose();
  };

  const onSubmit = (values: CreateFormValues) => {
    createMutation.mutate({
      code: values.code,
      name: values.name,
      description: values.description || undefined,
    });
  };

  return (
    <Dialog open={open} onClose={handleClose} title={t('create.title')} size="md" dismissible={false}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {isDemoMode && sessionDemoOn && (
          <div className="flex flex-wrap items-center gap-2 p-2 border-2 border-accent/30 bg-accent/5">
            <span className="text-xs font-bold text-fg-muted uppercase tracking-wider">{t('create.fillDemo')}</span>
            {integrationPresetsForLocale.map((preset) => (
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
        <div>
          <Label htmlFor="create-code">{t('create.code')}</Label>
          <Input
            id="create-code"
            {...register('code')}
            placeholder={t('create.codePlaceholder')}
            className="mt-1 font-mono"
          />
          {errors.code && <p className="text-xs text-error mt-1">{errors.code.message}</p>}
          <p className="text-[10px] text-fg-muted mt-0.5">{t('create.codeHint')}</p>
        </div>
        <div>
          <Label htmlFor="create-name">{t('create.name')}</Label>
          <Input
            id="create-name"
            {...register('name')}
            placeholder={t('create.namePlaceholder')}
          />
          {errors.name && <p className="text-xs text-error mt-1">{errors.name.message}</p>}
          <p className="text-[10px] text-fg-muted mt-0.5">{t('create.nameHint')}</p>
        </div>
        <div>
          <Label htmlFor="create-description">{t('create.description')}</Label>
          <Textarea
            id="create-description"
            {...register('description')}
            placeholder={t('create.descriptionPlaceholder')}
            rows={2}
          />
        </div>
        {createMutation.isError && (
          <p className="text-xs text-error">
            {createMutation.error instanceof ApiError
              ? createMutation.error.message
              : t('create.errorCreate')}
          </p>
        )}
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={handleClose}>
            {t('create.cancel')}
          </Button>
          <Button type="submit" isLoading={createMutation.isPending}>
            {t('create.submit')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

export default function IntegrationsPage() {
  const { t } = useTranslation('integrations');
  const navigate = useNavigate();
  const [nameInput, setNameInput] = useState('');
  const [lifecycleFilter, setLifecycleFilter] = useState<IntegrationListFilter>('operational');
  const [createOpen, setCreateOpen] = useState(false);
  const debouncedName = useDebounce(nameInput, 300);

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<
    IntegrationResponseDto,
    { integrationName?: string; active?: boolean; lifecycleStatus?: IntegrationLifecycleStatus; includeRetired?: boolean }
  >({
    queryKey: ['integrations', debouncedName, lifecycleFilter],
    baseParams: {
      integrationName: debouncedName || undefined,
      lifecycleStatus:
        lifecycleFilter === 'active'
          ? 'ACTIVE'
          : lifecycleFilter === 'inactive'
            ? 'INACTIVE'
            : lifecycleFilter === 'retired'
              ? 'RETIRED'
              : undefined,
      includeRetired: lifecycleFilter === 'all' ? true : undefined,
    },
    fetchPage: (params) => search(params) as Promise<PagedModelIntegrationResponseDto>,
  });

  const columns: ColumnDef<IntegrationResponseDto>[] = [
    { header: t('list.columns.id'), key: 'id', className: 'w-14', sortKey: 'id', render: (row) => <span className="font-mono text-xs">{row.id}</span> },
    { header: t('list.columns.code'), key: 'code', render: (row) => <span className="font-mono text-xs">{row.code}</span> },
    { header: t('list.columns.name'), key: 'name', render: (row) => <span className="font-medium">{getIntegrationName(row)}</span> },
    {
      header: t('list.columns.status'),
      key: 'lifecycleStatus',
      sortKey: 'lifecycleStatus',
      render: (row) => {
        const lifecycleStatus = getLifecycleStatus(row);
        const variant = lifecycleStatus === 'ACTIVE' ? 'success' : 'muted';
        const label =
          lifecycleStatus === 'ACTIVE'
            ? t('list.statusActive')
            : lifecycleStatus === 'INACTIVE'
              ? t('list.statusInactive')
              : t('list.statusRetired');
        return <Badge variant={variant}>{label}</Badge>;
      },
    },
    { header: t('list.columns.tenant'), key: 'tenantId', className: 'w-16', render: (row) => <span className="font-mono text-xs">{row.tenantId ?? '—'}</span> },
    { header: t('list.columns.created'), key: 'createdAt', sortKey: 'createdAt', render: (row) => <span className="text-xs text-fg-muted">{formatDate(row.createdAt ?? '')}</span> },
  ];

  return (
    <AppShell title={t('list.title')}>
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <HelpInlineButton className="p-1.5 hover:bg-fg/10 shrink-0 text-fg-muted hover:text-fg" />
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
            <Select
              value={lifecycleFilter}
              onChange={(e) => setLifecycleFilter(e.target.value as IntegrationListFilter)}
            >
              <option value="operational">{t('list.filterOperational')}</option>
              <option value="all">{t('list.filterAll')}</option>
              <option value="active">{t('list.filterActive')}</option>
              <option value="inactive">{t('list.filterInactive')}</option>
              <option value="retired">{t('list.filterRetired')}</option>
            </Select>
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="size-3.5" />
            {t('list.refresh')}
          </Button>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5 ml-auto">
            <Plus className="size-3.5" />
            {t('list.createIntegration')}
          </Button>
        </div>

        {/* Table */}
        <div>
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => {
              const st = buildListDetailNavState(data, (r) => r.id ?? 0, row, !pagination.isLast);
              navigate(`/integrations/${row.id}`, { state: st ?? undefined });
            }}
            keyExtractor={(row, i) => row.id ?? i}
            emptyMessage={t('list.emptyMessage')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>

        <p className="text-xs text-fg-muted italic">
          {t('list.hintClickRow')}
        </p>
      </div>

      <CreateIntegrationDialog open={createOpen} onClose={() => setCreateOpen(false)} />
    </AppShell>
  );
}
