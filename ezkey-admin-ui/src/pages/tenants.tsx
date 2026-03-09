import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Building2, Plus, RefreshCw, Search } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { useToast } from '@/context/toast-context';
import { useDebounce } from '@/hooks/use-debounce';
import { ApiError } from '@/lib/api-client';
import { isDemoMode, tenantDemoPresets } from '@/lib/demo-mode';
import { getCountryOptionsGrouped } from '@/lib/countries';
import { getTimeZoneOptionsGrouped } from '@/lib/timezones';
import { formatDate } from '@/lib/utils';
import { getListTenantsQueryKey, useCreateTenant, useListTenants } from '@/generated/admin-api/tenants/tenants';
import type { TenantResponseDto } from '@/generated/admin-api/model';

// ── Create form schema ────────────────────────────────────────────────────────

const createSchema = z.object({
  tenantName: z.string().min(3, 'Min 3 characters').max(100, 'Max 100 characters'),
  tenantDescription: z.string().max(500).optional().or(z.literal('')),
  organizationName: z.string().max(255).optional().or(z.literal('')),
  organizationDomain: z
    .string()
    .regex(/^$|^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/, 'Must be a valid domain')
    .optional()
    .or(z.literal('')),
  countryCode: z
    .string()
    .regex(/^$|^[A-Z]{2}$/, 'Must be a 2-letter country code (e.g. US, FR)')
    .optional()
    .or(z.literal('')),
  timezone: z.string().max(50).optional().or(z.literal('')),
  primaryContactName: z.string().max(255).optional().or(z.literal('')),
  primaryContactEmail: z.string().email('Invalid email').optional().or(z.literal('')),
});
type CreateFormValues = z.infer<typeof createSchema>;

// ── Create dialog ─────────────────────────────────────────────────────────────

function CreateTenantDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { sessionDemoOn } = useDemoModeSession();
  const countryGrouped = getCountryOptionsGrouped();
  const timezoneGrouped = getTimeZoneOptionsGrouped();
  const { register, handleSubmit, reset, formState: { errors } } = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
  });

  const createMutation = useCreateTenant({
    mutation: {
      onSuccess: async (tenant) => {
        await queryClient.invalidateQueries({ queryKey: getListTenantsQueryKey() });
        toast(`Tenant "${(tenant as unknown as TenantResponseDto).tenantName}" created successfully.`);
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
    <Dialog open={open} onClose={handleClose} title="Create Tenant" size="lg" dismissible={false}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {isDemoMode && sessionDemoOn && (
          <div className="flex flex-wrap items-center gap-2 p-2 border-2 border-accent/30 bg-accent/5">
            <span className="text-xs font-bold text-fg-muted uppercase tracking-wider">Fill demo:</span>
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
          <Label htmlFor="t-name">Tenant Name *</Label>
          <Input id="t-name" placeholder="e.g. Garage du coin" error={errors.tenantName?.message} {...register('tenantName')} />
        </div>
        <div>
          <Label htmlFor="t-desc">Description</Label>
          <Textarea id="t-desc" placeholder="Porsche, Mercedes, Audi" rows={2} {...register('tenantDescription')} />
        </div>

        {/* Row 2 — Organization */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="t-orgname">Organization Name</Label>
            <Input id="t-orgname" placeholder="Garage du coin (Porsche, Mercedes, Audi)" {...register('organizationName')} />
          </div>
          <div>
            <Label htmlFor="t-orgdomain">Organization Domain</Label>
            <Input id="t-orgdomain" placeholder="garageducoin.com" error={errors.organizationDomain?.message} {...register('organizationDomain')} />
          </div>
        </div>

        {/* Row 3 — Location */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="t-country">Country</Label>
            <Select id="t-country" error={errors.countryCode?.message} {...register('countryCode')}>
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
            <Label htmlFor="t-tz">Timezone</Label>
            <Select id="t-tz" {...register('timezone')}>
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

        {/* Row 4 — Contact */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="t-cname">Primary Contact Name</Label>
            <Input id="t-cname" placeholder="Oscar Dupont" {...register('primaryContactName')} />
          </div>
          <div>
            <Label htmlFor="t-cemail">Primary Contact Email</Label>
            <Input id="t-cemail" type="email" placeholder="oscar@garageducoin.com" error={errors.primaryContactEmail?.message} {...register('primaryContactEmail')} />
          </div>
        </div>

        {createMutation.isError && (
          <Alert variant="error">
            {createMutation.error instanceof ApiError
              ? createMutation.error.message
              : 'Failed to create tenant.'}
          </Alert>
        )}

        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="ghost" onClick={handleClose}>Cancel</Button>
          <Button type="submit" isLoading={createMutation.isPending}>
            <Building2 className="size-3.5 mr-1.5" />
            Create Tenant
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function TenantsPage() {
  const navigate = useNavigate();
  const [nameInput, setNameInput] = useState('');
  const [activeFilter, setActiveFilter] = useState<'all' | 'active' | 'inactive'>('all');
  const [createOpen, setCreateOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const debouncedName = useDebounce(nameInput, 300);

  const { data: allTenantsRaw, isLoading, refetch } = useListTenants<TenantResponseDto[]>();
  const allTenants = allTenantsRaw ?? [];

  // Client-side filtering
  const filtered = useMemo(() => {
    let result = allTenants;
    if (debouncedName) {
      const lower = debouncedName.toLowerCase();
      result = result.filter((t) => t.tenantName?.toLowerCase().includes(lower));
    }
    if (activeFilter === 'active') result = result.filter((t) => t.active);
    if (activeFilter === 'inactive') result = result.filter((t) => !t.active);
    return result;
  }, [allTenants, debouncedName, activeFilter]);

  // Client-side pagination
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const data = filtered.slice(page * pageSize, (page + 1) * pageSize);

  const columns: ColumnDef<TenantResponseDto>[] = [
    { header: 'ID', key: 'tenantId', className: 'w-14', sortKey: 'tenantId', render: (r) => <span className="font-mono text-xs">{r.tenantId}</span> },
    { header: 'Name', key: 'tenantName', sortKey: 'tenantName', render: (r) => <span className="font-medium">{r.tenantName}</span> },
    {
      header: 'Organization',
      key: 'organizationName',
      render: (r) => <span className="text-xs text-fg-muted">{r.organizationName ?? '—'}</span>,
    },
    {
      header: 'Domain',
      key: 'organizationDomain',
      render: (r) => r.organizationDomain
        ? <span className="font-mono text-xs">{r.organizationDomain}</span>
        : <span className="text-fg-muted">—</span>,
    },
    {
      header: 'Status',
      key: 'active',
      sortKey: 'active',
      render: (r) => (
        <div className="flex items-center gap-1.5">
          <Badge variant={r.active ? 'success' : 'muted'}>{r.active ? 'Active' : 'Inactive'}</Badge>
          {r.isSystemTenant && <Badge variant="warning">System</Badge>}
        </div>
      ),
    },
    {
      header: 'Country',
      key: 'countryCode',
      render: (r) => <span className="text-xs">{r.countryCode ?? '—'}</span>,
    },
    { header: 'Created', key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.createdAt ?? '')}</span> },
  ];

  return (
    <AppShell title="Tenants">
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="flex-1 min-w-52 relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-fg-muted pointer-events-none" />
            <Input
              placeholder="Search by name..."
              value={nameInput}
              onChange={(e) => setNameInput(e.target.value)}
              className="pl-8"
            />
          </div>
          <div className="w-40">
            <Select value={activeFilter} onChange={(e) => setActiveFilter(e.target.value as typeof activeFilter)}>
              <option value="all">All Status</option>
              <option value="active">Active only</option>
              <option value="inactive">Inactive only</option>
            </Select>
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5 ml-auto">
            <Plus className="size-3.5" />
            Create Tenant
          </Button>
        </div>

        {/* Table */}
        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => navigate(`/tenants/${row.tenantId}`)}
            keyExtractor={(r, i) => r.tenantId ?? i}
            emptyMessage="No tenants found."
          />
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={filtered.length}
            isFirst={page === 0}
            isLast={page >= totalPages - 1}
            onPrevPage={() => setPage((p) => Math.max(0, p - 1))}
            onNextPage={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            pageSize={pageSize}
            onPageSizeChange={(s) => { setPageSize(s); setPage(0); }}
          />
        </div>
      </div>

      <CreateTenantDialog open={createOpen} onClose={() => setCreateOpen(false)} />
    </AppShell>
  );
}
