import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Users } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { usePaginatedQuery } from '@/hooks/use-paginated-query';
import { getIntegrationName } from '@/hooks/use-integrations';
import { api } from '@/lib/api-client';
import { formatDate } from '@/lib/utils';
import type { PageResponse } from '@/types/api';
import type { Enrollment, Integration } from '@/types/models';

// ── Info row helper ──────────────────────────────────────────────────────────

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-4">
      <dt className="w-32 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">
        {label}
      </dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function IntegrationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const integrationId = Number(id);

  const { data: integration, isLoading } = useQuery({
    queryKey: ['integration', integrationId],
    queryFn: () => api.get<Integration>(`/api/v1/integrations/${integrationId}`),
    enabled: !isNaN(integrationId),
  });

  const { data: enrollments, pagination: enrPagination, isLoading: loadingEnr } = usePaginatedQuery<Enrollment>({
    queryKey: ['enrollments', 'for-integration', integrationId],
    queryFn: ({ page, size, sort }) =>
      api.get<PageResponse<Enrollment>>(
        `/api/v1/enrollments?integrationId=${integrationId}&page=${page}&size=${size}&sort=${sort}`,
      ),
    defaultSize: 10,
  });

  const enrollmentColumns: ColumnDef<Enrollment>[] = [
    { header: 'ID', key: 'enrollmentId', className: 'w-14', render: (r) => <span className="font-mono text-xs">{r.enrollmentId}</span> },
    { header: 'Name', key: 'enrollmentName', render: (r) => <span className="font-medium">{r.enrollmentName}</span> },
    { header: 'Status', key: 'enrollmentStatus', render: (r) => <EnrollmentStatusBadge status={r.enrollmentStatus} /> },
    { header: 'Active', key: 'enrollmentActive', render: (r) => <Badge variant={r.enrollmentActive ? 'success' : 'muted'}>{r.enrollmentActive ? 'Yes' : 'No'}</Badge> },
    { header: 'Verified', key: 'verifiedAt', render: (r) => <span className="text-xs text-fg-muted">{r.verifiedAt ? formatDate(r.verifiedAt) : '—'}</span> },
  ];

  const name = integration ? getIntegrationName(integration) : '...';

  return (
    <AppShell title={isLoading ? 'Integration' : name}>
      <div className="space-y-6">

        {/* Back */}
        <Button variant="ghost" size="sm" onClick={() => navigate('/integrations')} className="gap-1.5 -ml-2">
          <ArrowLeft className="size-3.5" />
          Back to Integrations
        </Button>

        {/* Details */}
        {integration && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            <Card className="lg:col-span-2">
              <CardHeader><CardTitle>Integration Details</CardTitle></CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label="ID"><span className="font-mono">{integration.id}</span></InfoRow>
                  <InfoRow label="Code"><span className="font-mono">{integration.code}</span></InfoRow>
                  {integration.i18n.map((entry) => (
                    <InfoRow key={entry.language} label={`Name (${entry.language.toUpperCase()})`}>
                      <div>
                        <div className="font-medium">{entry.name}</div>
                        {entry.description && <div className="text-xs text-fg-muted mt-0.5">{entry.description}</div>}
                      </div>
                    </InfoRow>
                  ))}
                  <InfoRow label="Status">
                    <Badge variant={integration.active ? 'success' : 'muted'}>
                      {integration.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </InfoRow>
                  <InfoRow label="Created">
                    <span className="text-fg-muted">{formatDate(integration.createdAt)}</span>
                  </InfoRow>
                </dl>
              </CardContent>
            </Card>

            <Card>
              <CardHeader><CardTitle>Actions</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    className="w-full justify-start gap-2"
                    onClick={() => navigate(`/enrollments?integrationId=${integration.id}`)}
                  >
                    <Users className="size-3.5" />
                    View All Enrollments
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        )}

        {/* Enrollments section */}
        <div className="border-2 border-fg shadow-brutal bg-surface">
          <div className="flex items-center justify-between px-4 py-3 border-b-2 border-fg bg-bg">
            <h3 className="text-xs font-black uppercase tracking-widest text-fg-muted">
              Enrollments for this Integration
            </h3>
            <Button
              size="sm"
              onClick={() => navigate(`/enrollments?integrationId=${integrationId}`)}
            >
              <Users className="size-3.5" />
              New Enrollment
            </Button>
          </div>
          <DataTable
            columns={enrollmentColumns}
            data={enrollments}
            isLoading={loadingEnr}
            onRowClick={(row) => navigate(`/enrollments/${row.enrollmentId}`)}
            keyExtractor={(row) => row.enrollmentId}
            emptyMessage="No enrollments for this integration yet."
          />
          <Pagination
            page={enrPagination.page}
            totalPages={enrPagination.totalPages}
            totalElements={enrPagination.totalElements}
            isFirst={enrPagination.isFirst}
            isLast={enrPagination.isLast}
            onPrevPage={enrPagination.prevPage}
            onNextPage={enrPagination.nextPage}
          />
        </div>
      </div>
    </AppShell>
  );
}
