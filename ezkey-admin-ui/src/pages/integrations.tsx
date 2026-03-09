import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Plus, RefreshCw, Search } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
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
import { integrationDemoPresets, isDemoMode } from '@/lib/demo-mode';
import { formatDate } from '@/lib/utils';
import { create, search } from '@/generated/admin-api/integrations/integrations';
import type { IntegrationCreateRequestDto, IntegrationCreateResponseDto, IntegrationResponseDto, PagedModelIntegrationResponseDto } from '@/generated/admin-api/model';

// ── Create form schema ────────────────────────────────────────────────────────

const createSchema = z.object({
  code: z
    .string()
    .min(2, 'Code must be at least 2 characters')
    .max(100, 'Code must be at most 100 characters')
    .regex(/^[a-zA-Z0-9_-]+$/, 'Code must contain only letters, numbers, hyphens, and underscores'),
  name: z.string().min(1, 'Name is required').max(255, 'Name must be at most 255 characters'),
  description: z.string().max(500).optional().or(z.literal('')),
});
type CreateFormValues = z.infer<typeof createSchema>;

// ── Create dialog ─────────────────────────────────────────────────────────────

function CreateIntegrationDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { sessionDemoOn } = useDemoModeSession();
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
    <Dialog open={open} onClose={handleClose} title="Create Integration" size="md" dismissible={false}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {isDemoMode && sessionDemoOn && (
          <div className="flex flex-wrap items-center gap-2 p-2 border-2 border-accent/30 bg-accent/5">
            <span className="text-xs font-bold text-fg-muted uppercase tracking-wider">Fill demo:</span>
            {integrationDemoPresets.map((preset) => (
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
          <Label htmlFor="create-code">Code</Label>
          <Input
            id="create-code"
            {...register('code')}
            placeholder="e.g. admin-console"
            className="mt-1 font-mono"
          />
          {errors.code && <p className="text-xs text-error mt-1">{errors.code.message}</p>}
          <p className="text-[10px] text-fg-muted mt-0.5">Alphanumeric, hyphens, underscores. Unique per tenant.</p>
        </div>
        <div>
          <Label htmlFor="create-name">Name</Label>
          <Input
            id="create-name"
            {...register('name')}
            placeholder="e.g. Administration"
          />
          {errors.name && <p className="text-xs text-error mt-1">{errors.name.message}</p>}
          <p className="text-[10px] text-fg-muted mt-0.5">Display name for the application.</p>
        </div>
        <div>
          <Label htmlFor="create-description">Description (optional)</Label>
          <Textarea
            id="create-description"
            {...register('description')}
            placeholder="e.g. Console d'administration"
            rows={2}
          />
        </div>
        {createMutation.isError && (
          <p className="text-xs text-error">
            {createMutation.error instanceof ApiError
              ? createMutation.error.message
              : 'Failed to create integration.'}
          </p>
        )}
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" isLoading={createMutation.isPending}>
            Create Integration
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

export default function IntegrationsPage() {
  const navigate = useNavigate();
  const [nameInput, setNameInput] = useState('');
  const [activeFilter, setActiveFilter] = useState<'all' | 'active' | 'inactive'>('all');
  const [createOpen, setCreateOpen] = useState(false);
  const debouncedName = useDebounce(nameInput, 300);

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<IntegrationResponseDto, { integrationName?: string; active?: boolean }>({
    queryKey: ['integrations', debouncedName, activeFilter],
    baseParams: {
      integrationName: debouncedName || undefined,
      active: activeFilter === 'all' ? undefined : activeFilter === 'active',
    },
    fetchPage: (params) => search(params) as Promise<PagedModelIntegrationResponseDto>,
  });

  const columns: ColumnDef<IntegrationResponseDto>[] = [
    { header: 'ID', key: 'id', className: 'w-14', sortKey: 'id', render: (row) => <span className="font-mono text-xs">{row.id}</span> },
    { header: 'Code', key: 'code', render: (row) => <span className="font-mono text-xs">{row.code}</span> },
    { header: 'Name', key: 'name', render: (row) => <span className="font-medium">{getIntegrationName(row)}</span> },
    {
      header: 'Status',
      key: 'active',
      sortKey: 'active',
      render: (row) => <Badge variant={row.active ? 'success' : 'muted'}>{row.active ? 'Active' : 'Inactive'}</Badge>,
    },
    { header: 'Tenant', key: 'tenantId', className: 'w-16', render: (row) => <span className="font-mono text-xs">{row.tenantId ?? '—'}</span> },
    { header: 'Created', key: 'createdAt', sortKey: 'createdAt', render: (row) => <span className="text-xs text-fg-muted">{formatDate(row.createdAt ?? '')}</span> },
  ];

  return (
    <AppShell title="Integrations">
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
            <Select
              value={activeFilter}
              onChange={(e) => setActiveFilter(e.target.value as typeof activeFilter)}
            >
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
            Create Integration
          </Button>
        </div>

        {/* Table */}
        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => navigate(`/integrations/${row.id}`)}
            keyExtractor={(row, i) => row.id ?? i}
            emptyMessage="No integrations found. Create your first integration to get started."
            currentSort={pagination.sort}
            onSort={pagination.setSort}
          />
          <Pagination
            page={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            isFirst={pagination.isFirst}
            isLast={pagination.isLast}
            onPrevPage={pagination.prevPage}
            onNextPage={pagination.nextPage}
            pageSize={pagination.size}
            onPageSizeChange={pagination.setPageSize}
          />
        </div>

        <p className="text-xs text-fg-muted italic">
          Click a row to view integration details and enrollments.
        </p>
      </div>

      <CreateIntegrationDialog open={createOpen} onClose={() => setCreateOpen(false)} />
    </AppShell>
  );
}
