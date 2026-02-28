import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCw, Search } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { usePaginatedQuery } from '@/hooks/use-paginated-query';
import { useDebounce } from '@/hooks/use-debounce';
import { getIntegrationName } from '@/hooks/use-integrations';
import { api } from '@/lib/api-client';
import { formatDate } from '@/lib/utils';
import type { PageResponse } from '@/types/api';
import type { Integration } from '@/types/models';

export default function IntegrationsPage() {
  const navigate = useNavigate();
  const [nameInput, setNameInput] = useState('');
  const [activeFilter, setActiveFilter] = useState<'all' | 'active' | 'inactive'>('all');
  const debouncedName = useDebounce(nameInput, 300);

  const { data, pagination, isLoading, refetch } = usePaginatedQuery<Integration>({
    queryKey: ['integrations', debouncedName, activeFilter],
    queryFn: ({ page, size, sort }) => {
      const p = new URLSearchParams({ page: String(page), size: String(size), sort });
      if (debouncedName) p.set('integrationName', debouncedName);
      if (activeFilter === 'active') p.set('active', 'true');
      if (activeFilter === 'inactive') p.set('active', 'false');
      return api.get<PageResponse<Integration>>(`/api/v1/integrations?${p.toString()}`);
    },
  });

  const columns: ColumnDef<Integration>[] = [
    { header: 'ID', key: 'id', className: 'w-14', render: (row) => <span className="font-mono text-xs">{row.id}</span> },
    { header: 'Code', key: 'code', render: (row) => <span className="font-mono text-xs">{row.code}</span> },
    { header: 'Name', key: 'name', render: (row) => <span className="font-medium">{getIntegrationName(row)}</span> },
    {
      header: 'Status',
      key: 'active',
      render: (row) => <Badge variant={row.active ? 'success' : 'muted'}>{row.active ? 'Active' : 'Inactive'}</Badge>,
    },
    { header: 'Created', key: 'createdAt', render: (row) => <span className="text-xs text-fg-muted">{formatDate(row.createdAt)}</span> },
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
        </div>

        {/* Table */}
        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => navigate(`/integrations/${row.id}`)}
            keyExtractor={(row) => row.id}
            emptyMessage="No integrations found. Create your first integration to get started."
          />
          <Pagination
            page={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            isFirst={pagination.isFirst}
            isLast={pagination.isLast}
            onPrevPage={pagination.prevPage}
            onNextPage={pagination.nextPage}
          />
        </div>

        <p className="text-xs text-fg-muted italic">
          Click a row to view integration details and enrollments.
        </p>
      </div>
    </AppShell>
  );
}
