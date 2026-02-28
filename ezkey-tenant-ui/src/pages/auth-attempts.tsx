import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCw, Shield } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { AuthAttemptStatusBadge } from '@/components/feature/auth-attempt-status-badge';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { usePaginatedQuery } from '@/hooks/use-paginated-query';
import { useIntegrations } from '@/hooks/use-integrations';
import { useDebounce } from '@/hooks/use-debounce';
import { api } from '@/lib/api-client';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import type { PageResponse } from '@/types/api';
import type { AuthAttempt } from '@/types/models';

// ── Detail dialog ─────────────────────────────────────────────────────────────

function AttemptDetailDialog({
  attempt,
  onClose,
}: {
  attempt: AuthAttempt | null;
  onClose: () => void;
}) {
  if (!attempt) return null;

  function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div className="flex gap-4">
        <dt className="w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">{label}</dt>
        <dd className="text-sm">{children}</dd>
      </div>
    );
  }

  return (
    <Dialog open={attempt !== null} onClose={onClose} title={`Auth Attempt #${attempt.authAttemptId}`} size="md">
      <dl className="space-y-3">
        <InfoRow label="ID"><span className="font-mono">{attempt.authAttemptId}</span></InfoRow>
        <InfoRow label="Status"><AuthAttemptStatusBadge status={attempt.authAttemptStatus} /></InfoRow>
        <InfoRow label="Enrollment">
          <span className="font-mono">#{attempt.enrollmentId}</span>
        </InfoRow>
        <InfoRow label="Challenge">
          {attempt.authAttemptChallenge != null
            ? <span className="font-mono font-bold">{String(attempt.authAttemptChallenge).padStart(2, '0')}</span>
            : <span className="text-fg-muted">—</span>}
        </InfoRow>
        <InfoRow label="Created"><span className="text-fg-muted">{formatDate(attempt.createdAt)}</span></InfoRow>
        <InfoRow label="Expires"><span className="text-fg-muted">{formatDate(attempt.expiresAt)}</span></InfoRow>
        {attempt.authAttemptProofToken && (
          <InfoRow label="Proof Token">
            <span className="font-mono text-xs break-all text-success">{attempt.authAttemptProofToken}</span>
          </InfoRow>
        )}
      </dl>
      <div className="flex justify-end pt-4">
        <Button onClick={onClose}>Close</Button>
      </div>
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AuthAttemptsPage() {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState('');
  const [enrollmentIdInput, setEnrollmentIdInput] = useState('');
  const [integrationFilter, setIntegrationFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [selectedAttempt, setSelectedAttempt] = useState<AuthAttempt | null>(null);

  const debouncedEnrollmentId = useDebounce(enrollmentIdInput, 400);
  const { list: integrations, lookup } = useIntegrations();

  const { data, pagination, isLoading, refetch } = usePaginatedQuery<AuthAttempt>({
    queryKey: ['auth-attempts', statusFilter, debouncedEnrollmentId, integrationFilter, dateFrom, dateTo],
    queryFn: ({ page, size, sort }) => {
      const p = new URLSearchParams({ page: String(page), size: String(size), sort });
      if (statusFilter) p.set('status', statusFilter);
      if (debouncedEnrollmentId) p.set('enrollmentId', debouncedEnrollmentId);
      if (integrationFilter) p.set('integrationId', integrationFilter);
      if (dateFrom) p.set('createdAfter', new Date(dateFrom).toISOString());
      if (dateTo) p.set('createdBefore', new Date(dateTo + 'T23:59:59').toISOString());
      return api.get<PageResponse<AuthAttempt>>(`/api/v1/auth-attempts?${p.toString()}`);
    },
  });

  const columns: ColumnDef<AuthAttempt>[] = [
    { header: 'ID', key: 'authAttemptId', className: 'w-14', render: (r) => <span className="font-mono text-xs">{r.authAttemptId}</span> },
    { header: 'Status', key: 'authAttemptStatus', render: (r) => <AuthAttemptStatusBadge status={r.authAttemptStatus} /> },
    {
      header: 'Enrollment',
      key: 'enrollmentId',
      render: (r) => (
        <button
          className="font-mono text-xs text-accent hover:underline"
          onClick={(e) => { e.stopPropagation(); navigate(`/enrollments/${r.enrollmentId}`); }}
        >
          #{r.enrollmentId}
        </button>
      ),
    },
    {
      header: 'Integration',
      key: 'integration',
      render: (r) => {
        // Auth attempt doesn't directly expose integrationId — show via lookup if available
        const name = Array.from(lookup.entries()).find(() => false);
        void name; // not directly available from auth attempt DTO
        return <span className="text-xs text-fg-muted">via #{r.enrollmentId}</span>;
      },
    },
    {
      header: 'Challenge',
      key: 'authAttemptChallenge',
      render: (r) =>
        r.authAttemptChallenge != null ? (
          <span className="font-mono font-bold">{String(r.authAttemptChallenge).padStart(2, '0')}</span>
        ) : (
          <span className="text-fg-muted">—</span>
        ),
    },
    { header: 'Created', key: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatRelativeTime(r.createdAt)}</span> },
    { header: 'Expires', key: 'expiresAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.expiresAt)}</span> },
  ];

  // Suppress unused variable warning
  void integrations;

  return (
    <AppShell title="Auth Attempts">
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="w-36">
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="">All Status</option>
              <option value="PENDING">Pending</option>
              <option value="READ">Read</option>
              <option value="ACCEPTED">Accepted</option>
              <option value="REJECTED">Rejected</option>
              <option value="EXPIRED">Expired</option>
              <option value="INVALID">Invalid</option>
            </Select>
          </div>
          <div className="w-44">
            <Select value={integrationFilter} onChange={(e) => setIntegrationFilter(e.target.value)}>
              <option value="">All Integrations</option>
              {integrations.map((i) => (
                <option key={i.id} value={String(i.id)}>{i.code}</option>
              ))}
            </Select>
          </div>
          <div className="w-36">
            <Input
              placeholder="Enrollment ID"
              value={enrollmentIdInput}
              onChange={(e) => setEnrollmentIdInput(e.target.value)}
              type="number"
              min={1}
            />
          </div>
          <div className="flex items-center gap-2">
            <Label className="text-xs shrink-0">From</Label>
            <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="w-36" />
          </div>
          <div className="flex items-center gap-2">
            <Label className="text-xs shrink-0">To</Label>
            <Input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="w-36" />
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5 ml-auto">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
        </div>

        <div className="flex items-center gap-2">
          <Shield className="size-3.5 text-fg-muted" />
          <p className="text-xs text-fg-muted italic">Read-only — tenant-scoped view of authentication attempts.</p>
        </div>

        {/* Table */}
        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedAttempt(row)}
            keyExtractor={(r) => r.authAttemptId}
            emptyMessage="No auth attempts found for the selected filters."
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
      </div>

      <AttemptDetailDialog
        attempt={selectedAttempt}
        onClose={() => setSelectedAttempt(null)}
      />
    </AppShell>
  );
}
