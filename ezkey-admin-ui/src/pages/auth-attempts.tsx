import { useState, useCallback, useEffect, useMemo } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { FileText, RefreshCw, Shield } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { AuthAttemptStatusBadge } from '@/components/feature/auth-attempt-status-badge';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { Dialog } from '@/components/ui/dialog';
import { DetailInfoRow } from '@/components/ui/detail-info-row';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { useDetailNavigation } from '@/hooks/use-detail-navigation';
import { DetailDialogHeaderNav } from '@/components/ui/detail-dialog-header-nav';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { useAuth } from '@/context/use-auth';
import { useDisplayTimezone } from '@/context/use-display-timezone';
import { dateRangeToApiParams } from '@/lib/date-range-presets';
import { useIntegrations } from '@/hooks/use-integrations';
import { useDebounce } from '@/hooks/use-debounce';
import { formatDate } from '@/lib/utils';
import {
  ROLLING_24H_PRESET_PARAM,
  ROLLING_24H_PRESET_VALUE,
  buildAuthAttemptAuditTrailUrl,
  getRolling24HoursWindowIso,
} from '@/lib/dashboard-drilldown-links';
import { search2 } from '@/generated/admin-api/auth-attempts/auth-attempts';
import type { AuthAttemptDto, PagedModelAuthAttemptDto, Search2Params } from '@/generated/admin-api/model';

const AUTH_STATUSES = ['PENDING', 'READ', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'INVALID'] as const;

function parseAuthStatusFilter(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  return (AUTH_STATUSES as readonly string[]).includes(value) ? value : '';
}

function parsePositiveIntString(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const n = Number.parseInt(value, 10);
  if (!Number.isInteger(n) || n <= 0) {
    return '';
  }
  return String(n);
}

// ── Detail dialog ─────────────────────────────────────────────────────────────

function AttemptDetailDialog({
  attempt,
  onClose,
  onPrev,
  onNext,
  hasPrev,
  hasNext,
  showNav,
  showEndOfPageHint,
}: {
  attempt: AuthAttemptDto | null;
  onClose: () => void;
  onPrev: () => void;
  onNext: () => void;
  hasPrev: boolean;
  hasNext: boolean;
  showNav: boolean;
  showEndOfPageHint: boolean;
}) {
  const { t } = useTranslation('auth-attempts');
  const { t: tc } = useTranslation('common');
  const navigate = useNavigate();

  useDetailNavigation(attempt !== null && showNav, {
    hasPrev: hasPrev && showNav,
    hasNext: hasNext && showNav,
    onPrev,
    onNext,
  });

  if (!attempt) return null;

  return (
    <Dialog
      open={attempt !== null}
      onClose={onClose}
      title={t('detail.title', { id: attempt.authAttemptId })}
      size="md"
      headerActions={
        showNav ? (
          <DetailDialogHeaderNav
            hasPrev={hasPrev}
            hasNext={hasNext}
            onPrev={onPrev}
            onNext={onNext}
          />
        ) : undefined
      }
    >
      <div className="space-y-4">
        {showEndOfPageHint && (
          <p className="text-xs text-fg-muted italic border border-fg/20 bg-fg/[0.03] px-3 py-2">
            {tc('detailNav.endOfPageMore')}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={() => navigate(`/audit-logs?authAttemptId=${attempt.authAttemptId}&source=auth-attempt-detail`)}
          >
            {t('detail.viewRelatedAudits')}
          </Button>
        </div>
        <dl className="space-y-3">
          <DetailInfoRow label={t('detail.labelId')}><span className="font-mono">{attempt.authAttemptId}</span></DetailInfoRow>
          <DetailInfoRow label={t('detail.labelStatus')}><AuthAttemptStatusBadge status={attempt.authAttemptStatus} /></DetailInfoRow>
          <DetailInfoRow label={t('detail.labelEnrollment')}>
            {attempt.enrollmentId != null ? (
              <Link
                to={`/enrollments/${attempt.enrollmentId}`}
                className="text-sm font-medium text-accent hover:underline"
              >
                {attempt.enrollmentName?.trim()
                  || t('list.enrollmentFallback', { id: attempt.enrollmentId })}
              </Link>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </DetailInfoRow>
          {attempt.integrationId != null && (
            <DetailInfoRow label={t('detail.labelIntegration')}>
              <Link
                to={`/integrations/${attempt.integrationId}`}
                className="text-sm font-medium text-accent hover:underline"
              >
                {attempt.integrationName?.trim()
                  || t('list.integrationFallback', {
                    id: attempt.integrationId,
                  })}
              </Link>
            </DetailInfoRow>
          )}
          {attempt.tenantId != null && (
            <DetailInfoRow label={t('detail.labelTenant')}>
              <Link
                to={`/tenants/${attempt.tenantId}`}
                className="text-sm font-medium text-accent hover:underline"
              >
                {attempt.tenantName?.trim()
                  || t('list.tenantFallback', { id: attempt.tenantId })}
              </Link>
            </DetailInfoRow>
          )}
        <DetailInfoRow label={t('detail.labelChallenge')}>
          {attempt.authAttemptChallenge != null
            ? <span className="font-mono font-bold">{String(attempt.authAttemptChallenge).padStart(2, '0')}</span>
            : <span className="text-fg-muted">—</span>}
        </DetailInfoRow>
        {attempt.demoMitmSignatureEnabled && (
          <DetailInfoRow label={t('detail.labelDemoMitm')}>
            <Badge variant="warning">{t('detail.demoMitmOn')}</Badge>
          </DetailInfoRow>
        )}
        <DetailInfoRow label={t('detail.labelCreated')}><span className="text-fg-muted">{formatDate(attempt.createdAt)}</span></DetailInfoRow>
        <DetailInfoRow label={t('detail.labelExpires')}><span className="text-fg-muted">{formatDate(attempt.expiresAt)}</span></DetailInfoRow>
        {attempt.authAttemptProofToken && (
          <DetailInfoRow label={t('detail.labelProofToken')}>
            <span className="font-mono text-xs break-all text-success">{attempt.authAttemptProofToken}</span>
          </DetailInfoRow>
        )}
        </dl>
        <div className="flex justify-end pt-4">
          <Button onClick={onClose}>{t('detail.close')}</Button>
        </div>
      </div>
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AuthAttemptsPage() {
  const { t } = useTranslation('auth-attempts');
  const { session } = useAuth();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const { effectiveTimeZoneId } = useDisplayTimezone();
  const [searchParams, setSearchParams] = useSearchParams();

  const [statusFilter, setStatusFilter] = useState(() => parseAuthStatusFilter(searchParams.get('status')));
  const [enrollmentIdInput, setEnrollmentIdInput] = useState(
    () => parsePositiveIntString(searchParams.get('enrollmentId')),
  );
  const [integrationFilter, setIntegrationFilter] = useState(
    () => parsePositiveIntString(searchParams.get('integrationId')),
  );
  const [presetRolling24h, setPresetRolling24h] = useState(
    () => searchParams.get(ROLLING_24H_PRESET_PARAM) === ROLLING_24H_PRESET_VALUE,
  );
  const [dateRange, setDateRange] = useState({ from: '', to: '' });
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);

  const debouncedEnrollmentId = useDebounce(enrollmentIdInput, 400);
  const { list: integrations } = useIntegrations();

  const apiDateParams =
    !presetRolling24h && dateRange.from && dateRange.to
      ? dateRangeToApiParams(dateRange.from, dateRange.to, effectiveTimeZoneId)
      : { createdAfter: undefined as string | undefined, createdBefore: undefined as string | undefined };

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<AuthAttemptDto, {
    status?: string;
    enrollmentId?: number;
    integrationId?: number;
    createdAfter?: string;
    createdBefore?: string;
  }>({
    queryKey: [
      'auth-attempts',
      statusFilter,
      debouncedEnrollmentId,
      integrationFilter,
      presetRolling24h,
      dateRange.from,
      dateRange.to,
      effectiveTimeZoneId,
    ],
    baseParams: {
      status: statusFilter || undefined,
      enrollmentId: debouncedEnrollmentId ? parseInt(debouncedEnrollmentId, 10) : undefined,
      integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
      createdAfter: apiDateParams.createdAfter,
      createdBefore: apiDateParams.createdBefore,
    },
    fetchPage: (params) => {
      const p = params as Search2Params & { page: number; size: number; sort: string[] };
      if (presetRolling24h) {
        const w = getRolling24HoursWindowIso();
        return search2({
          ...p,
          createdAfter: w.createdAfter,
          createdBefore: w.createdBefore,
        }) as Promise<PagedModelAuthAttemptDto>;
      }
      return search2(p) as Promise<PagedModelAuthAttemptDto>;
    },
  });

  useEffect(() => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (statusFilter) {
          next.set('status', statusFilter);
        } else {
          next.delete('status');
        }
        if (debouncedEnrollmentId) {
          next.set('enrollmentId', debouncedEnrollmentId);
        } else {
          next.delete('enrollmentId');
        }
        if (integrationFilter) {
          next.set('integrationId', integrationFilter);
        } else {
          next.delete('integrationId');
        }
        if (presetRolling24h) {
          next.set(ROLLING_24H_PRESET_PARAM, ROLLING_24H_PRESET_VALUE);
        } else {
          next.delete(ROLLING_24H_PRESET_PARAM);
        }
        return next;
      },
      { replace: true },
    );
  }, [debouncedEnrollmentId, integrationFilter, presetRolling24h, setSearchParams, statusFilter]);

  const selectedAttempt = selectedIndex !== null ? data[selectedIndex] ?? null : null;
  const showRowNav = data.length > 1;
  const hasPrev = selectedIndex !== null && selectedIndex > 0;
  const hasNext = selectedIndex !== null && selectedIndex < data.length - 1;
  const showEndOfPageHint =
    selectedIndex !== null &&
    data.length > 0 &&
    selectedIndex === data.length - 1 &&
    !pagination.isLast;

  const goPrevAttempt = useCallback(() => {
    setSelectedIndex((i) => (i !== null && i > 0 ? i - 1 : i));
  }, []);

  const goNextAttempt = useCallback(() => {
    setSelectedIndex((i) => {
      if (i === null) return i;
      return i < data.length - 1 ? i + 1 : i;
    });
  }, [data.length]);

  const columns: ColumnDef<AuthAttemptDto>[] = useMemo(() => {
    const enrollmentCol: ColumnDef<AuthAttemptDto> = {
      header: t('list.columns.enrollment'),
      key: 'enrollmentId',
      sortKey: 'enrollmentId',
      render: (r) => {
        if (r.enrollmentId == null) {
          return <span className="text-fg-muted">—</span>;
        }
        const label =
          r.enrollmentName != null && r.enrollmentName.trim() !== ''
            ? r.enrollmentName.trim()
            : t('list.enrollmentFallback', { id: r.enrollmentId });
        return (
          <Link
            to={`/enrollments/${r.enrollmentId}`}
            className="text-xs font-medium text-accent hover:underline max-w-[12rem] truncate block"
            title={label}
            onClick={(e) => e.stopPropagation()}
          >
            {label}
          </Link>
        );
      },
    };

    const integrationCol: ColumnDef<AuthAttemptDto> = {
      header: t('list.columns.integration'),
      key: 'integrationName',
      render: (r) => {
        if (r.integrationId == null) {
          return <span className="text-fg-muted">—</span>;
        }
        const label =
          r.integrationName != null && r.integrationName.trim() !== ''
            ? r.integrationName.trim()
            : t('list.integrationFallback', { id: r.integrationId });
        return (
          <Link
            to={`/integrations/${r.integrationId}`}
            className="text-xs font-medium text-accent hover:underline max-w-[12rem] truncate block"
            title={label}
            onClick={(e) => e.stopPropagation()}
          >
            {label}
          </Link>
        );
      },
    };

    const tenantCol: ColumnDef<AuthAttemptDto> = {
      header: t('list.columns.tenant'),
      key: 'tenantName',
      render: (r) => {
        if (r.tenantId == null) {
          return <span className="text-fg-muted">—</span>;
        }
        const label =
          r.tenantName != null && r.tenantName.trim() !== ''
            ? r.tenantName.trim()
            : t('list.tenantFallback', { id: r.tenantId });
        return (
          <Link
            to={`/tenants/${r.tenantId}`}
            className="text-xs font-medium text-accent hover:underline"
            onClick={(e) => e.stopPropagation()}
          >
            {label}
          </Link>
        );
      },
    };

    return [
      {
        header: t('list.columns.id'),
        key: 'authAttemptId',
        className: 'w-14',
        sortKey: 'authAttemptId',
        render: (r) => <span className="font-mono text-xs">{r.authAttemptId}</span>,
      },
      {
        header: t('list.columns.status'),
        key: 'authAttemptStatus',
        sortKey: 'authAttemptStatus',
        render: (r) => <AuthAttemptStatusBadge status={r.authAttemptStatus} />,
      },
      enrollmentCol,
      integrationCol,
      ...(isGlobalAdmin ? [tenantCol] : []),
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
      {
        header: t('list.columns.created'),
        key: 'createdAt',
        sortKey: 'createdAt',
        render: (r) => (
          <span className="text-xs text-fg-muted whitespace-nowrap">{formatDate(r.createdAt)}</span>
        ),
      },
      {
        header: t('list.columns.expires'),
        key: 'expiresAt',
        sortKey: 'expiresAt',
        render: (r) => (
          <span className="text-xs text-fg-muted whitespace-nowrap">{formatDate(r.expiresAt)}</span>
        ),
      },
    ];
  }, [isGlobalAdmin, t]);

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
          <div className="w-52">
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
          <DateRangeFilter
            value={dateRange}
            onChange={(r) => {
              setDateRange(r);
              if (r.from || r.to) {
                setPresetRolling24h(false);
              }
            }}
            showClear={true}
            emptyOptionLabel={t('list.dateRangeFull')}
          />
          {presetRolling24h && (
            <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
              <p className="text-xs text-fg-muted max-w-md">{t('list.rolling24hHint')}</p>
              <Button type="button" variant="secondary" size="sm" onClick={() => setPresetRolling24h(false)}>
                {t('list.clearRollingPreset')}
              </Button>
              <Link
                to={buildAuthAttemptAuditTrailUrl()}
                className="inline-flex items-center gap-1 text-xs font-bold text-accent hover:underline"
              >
                <FileText className="size-3.5 shrink-0" aria-hidden />
                {t('list.viewAuditTrail')}
              </Link>
            </div>
          )}
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
            onRowClick={(row) => {
              const idx = data.findIndex((r) => r.authAttemptId === row.authAttemptId);
              setSelectedIndex(idx >= 0 ? idx : null);
            }}
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
        onClose={() => setSelectedIndex(null)}
        onPrev={goPrevAttempt}
        onNext={goNextAttempt}
        hasPrev={hasPrev}
        hasNext={hasNext}
        showNav={showRowNav}
        showEndOfPageHint={showEndOfPageHint}
      />
    </AppShell>
  );
}
