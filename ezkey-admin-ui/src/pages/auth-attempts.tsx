import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { RefreshCw, Shield } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { AuthAttemptStatusBadge } from '@/components/feature/auth-attempt-status-badge';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { dateRangeToApiParams } from '@/lib/date-range-presets';
import { useIntegrations } from '@/hooks/use-integrations';
import { useDebounce } from '@/hooks/use-debounce';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import { search2 } from '@/generated/admin-api/auth-attempts/auth-attempts';
import type { AuthAttemptDto, PagedModelAuthAttemptDto, Search2Params } from '@/generated/admin-api/model';

// ── Detail dialog ─────────────────────────────────────────────────────────────

function AttemptDetailDialog({
  attempt,
  onClose,
}: {
  attempt: AuthAttemptDto | null;
  onClose: () => void;
}) {
  const { t } = useTranslation('auth-attempts');
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
    <Dialog open={attempt !== null} onClose={onClose} title={t('detail.title', { id: attempt.authAttemptId })} size="md">
      <dl className="space-y-3">
        <InfoRow label={t('detail.labelId')}><span className="font-mono">{attempt.authAttemptId}</span></InfoRow>
        <InfoRow label={t('detail.labelStatus')}><AuthAttemptStatusBadge status={attempt.authAttemptStatus} /></InfoRow>
        <InfoRow label={t('detail.labelEnrollment')}>
          <span className="font-mono">#{attempt.enrollmentId}</span>
        </InfoRow>
        <InfoRow label={t('detail.labelChallenge')}>
          {attempt.authAttemptChallenge != null
            ? <span className="font-mono font-bold">{String(attempt.authAttemptChallenge).padStart(2, '0')}</span>
            : <span className="text-fg-muted">—</span>}
        </InfoRow>
        <InfoRow label={t('detail.labelCreated')}><span className="text-fg-muted">{formatDate(attempt.createdAt)}</span></InfoRow>
        <InfoRow label={t('detail.labelExpires')}><span className="text-fg-muted">{formatDate(attempt.expiresAt)}</span></InfoRow>
        {attempt.authAttemptProofToken && (
          <InfoRow label={t('detail.labelProofToken')}>
            <span className="font-mono text-xs break-all text-success">{attempt.authAttemptProofToken}</span>
          </InfoRow>
        )}
      </dl>
      <div className="flex justify-end pt-4">
        <Button onClick={onClose}>{t('detail.close')}</Button>
      </div>
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AuthAttemptsPage() {
  const { t } = useTranslation('auth-attempts');
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState('');
  const [enrollmentIdInput, setEnrollmentIdInput] = useState('');
  const [integrationFilter, setIntegrationFilter] = useState('');
  const [dateRange, setDateRange] = useState({ from: '', to: '' });
  const [selectedAttempt, setSelectedAttempt] = useState<AuthAttemptDto | null>(null);

  const debouncedEnrollmentId = useDebounce(enrollmentIdInput, 400);
  const { list: integrations, lookup } = useIntegrations();

  const apiDateParams =
    dateRange.from && dateRange.to
      ? dateRangeToApiParams(dateRange.from, dateRange.to)
      : { createdAfter: undefined as string | undefined, createdBefore: undefined as string | undefined };

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<AuthAttemptDto, {
    status?: string;
    enrollmentId?: number;
    integrationId?: number;
    createdAfter?: string;
    createdBefore?: string;
  }>({
    queryKey: ['auth-attempts', statusFilter, debouncedEnrollmentId, integrationFilter, dateRange.from, dateRange.to],
    baseParams: {
      status: statusFilter || undefined,
      enrollmentId: debouncedEnrollmentId ? parseInt(debouncedEnrollmentId, 10) : undefined,
      integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
      createdAfter: apiDateParams.createdAfter,
      createdBefore: apiDateParams.createdBefore,
    },
    fetchPage: (params) => search2(params as Search2Params) as Promise<PagedModelAuthAttemptDto>,
  });

  const columns: ColumnDef<AuthAttemptDto>[] = [
    { header: t('list.columns.id'), key: 'authAttemptId', className: 'w-14', sortKey: 'authAttemptId', render: (r) => <span className="font-mono text-xs">{r.authAttemptId}</span> },
    { header: t('list.columns.status'), key: 'authAttemptStatus', sortKey: 'authAttemptStatus', render: (r) => <AuthAttemptStatusBadge status={r.authAttemptStatus} /> },
    {
      header: t('list.columns.enrollment'),
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
      header: t('list.columns.integration'),
      key: 'integration',
      render: (r) => {
        const name = Array.from(lookup.entries()).find(() => false);
        void name;
        return <span className="text-xs text-fg-muted">via #{r.enrollmentId}</span>;
      },
    },
    {
      header: t('list.columns.challenge'),
      key: 'authAttemptChallenge',
      render: (r) =>
        r.authAttemptChallenge != null ? (
          <span className="font-mono font-bold">{String(r.authAttemptChallenge).padStart(2, '0')}</span>
        ) : (
          <span className="text-fg-muted">—</span>
        ),
    },
    { header: t('list.columns.created'), key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatRelativeTime(r.createdAt)}</span> },
    { header: t('list.columns.expires'), key: 'expiresAt', sortKey: 'expiresAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.expiresAt)}</span> },
  ];

  // Suppress unused variable warning
  void integrations;

  return (
    <AppShell title={t('list.title')}>
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="w-36">
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="">{t('list.filterStatusAll')}</option>
              <option value="PENDING">{t('list.filterStatusPending')}</option>
              <option value="READ">{t('list.filterStatusRead')}</option>
              <option value="ACCEPTED">{t('list.filterStatusAccepted')}</option>
              <option value="REJECTED">{t('list.filterStatusRejected')}</option>
              <option value="EXPIRED">{t('list.filterStatusExpired')}</option>
              <option value="INVALID">{t('list.filterStatusInvalid')}</option>
            </Select>
          </div>
          <div className="w-44">
            <Select value={integrationFilter} onChange={(e) => setIntegrationFilter(e.target.value)}>
              <option value="">{t('list.filterIntegrationAll')}</option>
              {integrations.map((i) => (
                <option key={i.id} value={String(i.id)}>{i.code}</option>
              ))}
            </Select>
          </div>
          <div className="w-36">
            <Input
              placeholder={t('list.enrollmentIdPlaceholder')}
              value={enrollmentIdInput}
              onChange={(e) => setEnrollmentIdInput(e.target.value)}
              type="number"
              min={1}
            />
          </div>
          <DateRangeFilter value={dateRange} onChange={setDateRange} showClear={true} emptyOptionLabel={t('list.dateRangeFull')} />
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5 ml-auto">
            <RefreshCw className="size-3.5" />
            {t('list.refresh')}
          </Button>
        </div>

        <div className="flex items-center gap-2">
          <Shield className="size-3.5 text-fg-muted" />
          <p className="text-xs text-fg-muted italic">{t('list.hintReadOnly')}</p>
        </div>

        {/* Table */}
        <div>
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedAttempt(row)}
            keyExtractor={(r) => r.authAttemptId}
            emptyMessage={t('list.emptyMessage')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
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
