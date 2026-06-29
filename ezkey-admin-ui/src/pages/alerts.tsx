import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ContextHelp } from '@/components/ui/context-help';
import { Select } from '@/components/ui/select';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { type ColumnDef } from '@/components/data-table/data-table';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { formatDate } from '@/lib/utils';
import { listAlerts } from '@/generated/admin-api/alerts/alerts';
import type {
  AlertResponseDto,
  AlertResponseDtoAlertType,
  AlertResponseDtoSeverity,
  AlertResponseDtoStatus,
  ListAlertsParams,
  PagedModelAlertResponseDto,
} from '@/generated/admin-api/model';

type StatusFilter = AlertResponseDtoStatus | '';
type SeverityFilter = AlertResponseDtoSeverity | '';
type TypeFilter = AlertResponseDtoAlertType | '';

type BaseParams = {
  status?: AlertResponseDtoStatus;
  severity?: AlertResponseDtoSeverity;
  alertType?: AlertResponseDtoAlertType;
};

function severityVariant(
  severity: AlertResponseDtoSeverity | undefined,
): 'default' | 'warning' | 'error' | 'muted' {
  if (severity === 'CRITICAL') return 'error';
  if (severity === 'WARNING') return 'warning';
  if (severity === 'INFO') return 'muted';
  return 'default';
}

function statusVariant(
  status: AlertResponseDtoStatus | undefined,
): 'default' | 'success' | 'warning' | 'muted' {
  if (status === 'OPEN') return 'warning';
  if (status === 'RESOLVED') return 'success';
  return 'default';
}

export default function AlertsPage() {
  const { t } = useTranslation(['alerts', 'common']);
  const navigate = useNavigate();

  const [statusFilter, setStatusFilter] = useState<StatusFilter>('OPEN');
  const [severityFilter, setSeverityFilter] = useState<SeverityFilter>('');
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('');

  const baseParams: BaseParams = {
    status: statusFilter || undefined,
    severity: severityFilter || undefined,
    alertType: typeFilter || undefined,
  };

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<
    AlertResponseDto,
    BaseParams
  >({
    queryKey: ['alerts', statusFilter, severityFilter, typeFilter],
    baseParams,
    fetchPage: (params) =>
      listAlerts(params as ListAlertsParams) as Promise<PagedModelAlertResponseDto>,
    defaultSort: 'createdAt,DESC',
  });

  const columns: ColumnDef<AlertResponseDto>[] = [
    {
      header: t('alerts:list.columns.id'),
      key: 'alertId',
      className: 'w-14',
      sortKey: 'alertId',
      render: (r) => <span className="font-mono text-xs">{r.alertId}</span>,
    },
    {
      header: t('alerts:list.columns.type'),
      key: 'alertType',
      sortKey: 'alertType',
      render: (r) => (
        <span className="text-xs font-medium">
          {r.alertType
            ? t(`alerts:type.${r.alertType}`, { defaultValue: r.alertType })
            : '—'}
        </span>
      ),
    },
    {
      header: t('alerts:list.columns.severity'),
      key: 'severity',
      sortKey: 'severity',
      render: (r) => (
        <Badge variant={severityVariant(r.severity)}>
          {r.severity
            ? t(`alerts:severity.${r.severity}`, { defaultValue: r.severity })
            : '—'}
        </Badge>
      ),
    },
    {
      header: t('alerts:list.columns.status'),
      key: 'status',
      sortKey: 'status',
      render: (r) => (
        <Badge variant={statusVariant(r.status)}>
          {r.status
            ? t(`alerts:status.${r.status}`, { defaultValue: r.status })
            : '—'}
        </Badge>
      ),
    },
    {
      header: t('alerts:list.columns.occurrences'),
      key: 'occurrenceCount',
      className: 'w-24 text-right',
      render: (r) => (
        <span className="text-xs font-mono text-fg-muted">{r.occurrenceCount ?? 1}</span>
      ),
    },
    {
      header: t('alerts:list.columns.createdAt'),
      key: 'createdAt',
      sortKey: 'createdAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">
          {r.createdAt ? formatDate(r.createdAt) : '—'}
        </span>
      ),
    },
    {
      header: t('alerts:list.columns.lastSeenAt'),
      key: 'lastSeenAt',
      sortKey: 'lastSeenAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">
          {r.lastSeenAt ? formatDate(r.lastSeenAt) : '—'}
        </span>
      ),
    },
    {
      header: t('alerts:list.columns.resolvedAt'),
      key: 'resolvedAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">
          {r.resolvedAt ? formatDate(r.resolvedAt) : '—'}
        </span>
      ),
    },
  ];

  return (
    <AppShell title={t('alerts:list.title')}>
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <AlertTriangle className="size-4 text-fg-muted" />
          <p className="text-sm text-fg-muted">{t('alerts:list.subtitle')}</p>
          <ContextHelp
            title={t('alerts:list.help.topic')}
            content={<p>{t('alerts:list.help.body')}</p>}
            ariaLabel={t('alerts:list.help.topic')}
          />
        </div>

        <div className="flex gap-3 items-center flex-wrap">
          <div className="w-44">
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
              aria-label={t('alerts:list.filterStatus')}
            >
              <option value="">{t('alerts:list.filterStatusAll')}</option>
              <option value="OPEN">{t('alerts:list.filterStatusOpen')}</option>
              <option value="RESOLVED">{t('alerts:list.filterStatusResolved')}</option>
            </Select>
          </div>
          <div className="w-44">
            <Select
              value={severityFilter}
              onChange={(e) => setSeverityFilter(e.target.value as SeverityFilter)}
              aria-label={t('alerts:list.filterSeverity')}
            >
              <option value="">{t('alerts:list.filterSeverityAll')}</option>
              <option value="INFO">{t('alerts:severity.INFO')}</option>
              <option value="WARNING">{t('alerts:severity.WARNING')}</option>
              <option value="CRITICAL">{t('alerts:severity.CRITICAL')}</option>
            </Select>
          </div>
          <div className="w-72">
            <Select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value as TypeFilter)}
              aria-label={t('alerts:list.filterAlertType')}
            >
              <option value="">{t('alerts:list.filterAlertTypeAll')}</option>
              <option value="AUDIT_CHAIN_GAP_PENDING">
                {t('alerts:type.AUDIT_CHAIN_GAP_PENDING')}
              </option>
              <option value="AUDIT_INTEGRITY_RUPTURE">
                {t('alerts:type.AUDIT_INTEGRITY_RUPTURE')}
              </option>
            </Select>
          </div>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => refetch()}
            className="gap-1.5 ml-auto"
          >
            <RefreshCw className="size-3.5" />
            {t('alerts:list.refresh')}
          </Button>
        </div>

        <div>
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            keyExtractor={(r, i) => r.alertId ?? i}
            emptyMessage={t('alerts:list.emptyMessage')}
            onRowClick={(row) => navigate(`/alerts/${row.alertId}`)}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>
      </div>
    </AppShell>
  );
}
