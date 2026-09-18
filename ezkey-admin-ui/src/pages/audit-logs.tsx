import { useState, useCallback, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { Info, ShieldCheck } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tooltip } from '@/components/ui/tooltip';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { Dialog } from '@/components/ui/dialog';
import { DetailInfoRow } from '@/components/ui/detail-info-row';
import { Select } from '@/components/ui/select';
import { useDetailNavigation } from '@/hooks/use-detail-navigation';
import { DetailDialogHeaderNav } from '@/components/ui/detail-dialog-header-nav';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { dateRangeToApiParams } from '@/lib/date-range-presets';
import { EventStatusBadge } from '@/components/feature/event-status-badge';
import { AUDIT_EVENT_TYPE_GROUPS, auditEventFilterToApiParams } from '@/lib/audit-event-type-family';
import { getAuditEventTypeLabel } from '@/lib/audit-event-type';
import { adminListDetailHref } from '@/lib/list-detail-navigation';
import { cn, formatDateWithTimezone, formatRelativeTime } from '@/lib/utils';
import {
  loadIntegrityInvestigationSession,
  mergeSingleEntryVerificationIntoSession,
  resolveEntryHmacDisplayState,
  resolveIntegrityReportEntryDisplayState,
  type IntegrityInvestigationSession,
  type EntryHmacDisplayState,
} from '@/lib/integrity-investigation-session';
import { useDisplayTimezone } from '@/context/use-display-timezone';
import { EntryHmacBadge } from '@/components/feature/entry-hmac-badge';
import {
  checkSingleEntryIntegrity,
  getAuditLogContext,
  getAuditLogs,
} from '@/generated/admin-api/audit-logs/audit-logs';
import type {
  AuditLogContextResponseDto,
  AuditLogResponseDto,
  GetAuditLogContextParams,
  GetAuditLogsParams,
  IntegrityReport,
  PagedModelAuditLogResponseDto,
} from '@/generated/admin-api/model';

type AuditLogQueryParams = GetAuditLogsParams & {
  authAttemptId?: number;
  integrationId?: number;
};
type AuditEventStatusFilter = NonNullable<GetAuditLogsParams['eventStatus']> | '';
type AuditApiNameFilter = NonNullable<GetAuditLogsParams['apiName']> | '';
type ContextEntityType = 'enrollment' | 'authAttempt' | 'integration' | '';

/** Operational heartbeat incident row (Admin API lifecycle). */

function parseEventStatusFilter(value: string | null | undefined): AuditEventStatusFilter {
  if (value === 'SUCCESS' || value === 'FAILURE' || value === 'ERROR') {
    return value;
  }
  return '';
}

function parseApiNameFilter(value: string | null | undefined): AuditApiNameFilter {
  if (value === 'ADMIN_API' || value === 'AUTH_API' || value === 'INTEGRATION_API') {
    return value;
  }
  return '';
}

function parsePositiveIntegerString(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return '';
  }
  return String(parsed);
}

function parseContextCount(value: string | null | undefined, fallback = 10): number {
  const parsed = Number.parseInt(value ?? '', 10);
  if (!Number.isInteger(parsed) || parsed < 0 || parsed > 50) {
    return fallback;
  }
  return parsed;
}

function parseContextEntityType(value: string | null | undefined): ContextEntityType {
  if (value === 'enrollment' || value === 'authAttempt' || value === 'integration') {
    return value;
  }
  return '';
}

function toDateInputValue(iso?: string | null): string {
  if (!iso) {
    return '';
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return '';
  }
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getLastHoursWindow(hours: number): { createdAfter: string; createdBefore: string } {
  const now = new Date();
  return {
    createdAfter: new Date(now.getTime() - hours * 60 * 60 * 1000).toISOString(),
    createdBefore: now.toISOString(),
  };
}

function getLast24HoursWindow(): { createdAfter: string; createdBefore: string } {
  return getLastHoursWindow(24);
}

function getLast48HoursWindow(): { createdAfter: string; createdBefore: string } {
  return getLastHoursWindow(48);
}

function detailHmacStatusClass(state: EntryHmacDisplayState): string {
  switch (state) {
    case 'violation':
    case 'violationRetamper':
      return 'text-error';
    case 'violationExplained':
      return 'text-warning';
    case 'verified':
      return 'text-success';
    default:
      return 'text-fg-muted';
  }
}

function detailHmacStatusLabel(state: EntryHmacDisplayState, t: (key: string) => string): string {
  switch (state) {
    case 'unsigned':
      return t('detail.hmacUnsigned');
    case 'violationExplained':
      return t('detail.hmacExplained');
    case 'violationRetamper':
      return t('detail.hmacRetamper');
    case 'violation':
      return t('detail.hmacViolation');
    case 'verified':
      return t('detail.hmacVerified');
    default:
      return t('detail.hmacSigned');
  }
}

// ── Detail dialog ─────────────────────────────────────────────────────────────

function AuditLogDetailDialog({
  log,
  onClose,
  onPrev,
  onNext,
  hasPrev,
  hasNext,
  showNav,
  showEndOfPageHint,
  onEntryIntegrityResolved,
}: {
  log: AuditLogResponseDto | null;
  onClose: () => void;
  onPrev: () => void;
  onNext: () => void;
  hasPrev: boolean;
  hasNext: boolean;
  showNav: boolean;
  showEndOfPageHint: boolean;
  onEntryIntegrityResolved?: (auditLogId: number, report: IntegrityReport) => void;
}) {
  const { t } = useTranslation('audit-logs');
  const { t: tc } = useTranslation('common');
  const [entryIntegrity, setEntryIntegrity] = useState<IntegrityReport | null>(null);
  const [entryIntegrityLoading, setEntryIntegrityLoading] = useState(false);

  useEffect(() => {
    if (!log?.auditLogId) {
      return;
    }
    let cancelled = false;
    setEntryIntegrityLoading(true);
    void checkSingleEntryIntegrity(log.auditLogId)
      .then((report) => {
        if (!cancelled) {
          const typed = report as unknown as IntegrityReport;
          setEntryIntegrity(typed);
          onEntryIntegrityResolved?.(log.auditLogId!, typed);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setEntryIntegrity(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setEntryIntegrityLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [log?.auditLogId, onEntryIntegrityResolved]);

  const entryHmacDisplayState = resolveIntegrityReportEntryDisplayState(
    Boolean(log?.entryHmac),
    entryIntegrity,
  );

  useDetailNavigation(log !== null && showNav, {
    hasPrev: hasPrev && showNav,
    hasNext: hasNext && showNav,
    onPrev,
    onNext,
  });

  if (!log) return null;

  return (
    <Dialog
      open={log !== null}
      onClose={onClose}
      title={t('detail.title', { id: log.auditLogId })}
      size="lg-wide"
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
        <dl className="space-y-2.5">
          <DetailInfoRow label={t('detail.labelId')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all"><span className="font-mono">{log.auditLogId}</span></DetailInfoRow>
          <DetailInfoRow label={t('detail.labelEventType')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            <span className="text-sm">{getAuditEventTypeLabel(log.eventType ?? undefined, t)}</span>
            {log.eventType && (
              <span className="ml-2 font-mono text-xs text-fg-muted">({log.eventType})</span>
            )}
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelStatus')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all"><EventStatusBadge status={log.eventStatus} /></DetailInfoRow>
          <DetailInfoRow label={t('detail.labelApi')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            {log.apiName ? (
              <Badge variant="muted">{log.apiName.replace('_API', '')}</Badge>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelAdminId')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            {log.adminId != null ? (
              log.adminUsername ? (
                <Link to={adminListDetailHref(log.adminId)} className="font-medium text-accent hover:underline">
                  {log.adminUsername}
                  <span className="ml-1.5 font-mono text-xs text-fg-muted">(ID {log.adminId})</span>
                </Link>
              ) : (
                <span className="font-mono">#{log.adminId}</span>
              )
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </DetailInfoRow>
          {log.targetAdminId != null && (
            <DetailInfoRow label={t('detail.labelTargetAdmin')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
              {log.targetAdminUsername ? (
                <Link to={adminListDetailHref(log.targetAdminId)} className="font-medium text-accent hover:underline">
                  {log.targetAdminUsername}
                  <span className="ml-1.5 font-mono text-xs text-fg-muted">(ID {log.targetAdminId})</span>
                </Link>
              ) : (
                <span className="font-mono">#{log.targetAdminId}</span>
              )}
            </DetailInfoRow>
          )}
          <DetailInfoRow label={t('detail.labelIntegration')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            {log.integrationId != null ? (
              log.integrationName ? (
                <Link to={`/integrations/${log.integrationId}`} className="font-medium text-accent hover:underline">
                  {log.integrationName}
                  <span className="ml-1.5 font-mono text-xs text-fg-muted">(ID {log.integrationId})</span>
                </Link>
              ) : (
                <span className="font-mono">#{log.integrationId}</span>
              )
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelEnrollment')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            {log.enrollmentId != null ? (
              log.enrollmentName ? (
                <Link to={`/enrollments/${log.enrollmentId}`} className="font-medium text-accent hover:underline">
                  {log.enrollmentName}
                  <span className="ml-1.5 font-mono text-xs text-fg-muted">(ID {log.enrollmentId})</span>
                </Link>
              ) : (
                <span className="font-mono">#{log.enrollmentId}</span>
              )
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </DetailInfoRow>
          {log.tenantId != null && (
            <DetailInfoRow label={t('detail.labelTenant')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
              {log.tenantName ? (
                <Link to={`/tenants/${log.tenantId}`} className="font-medium text-accent hover:underline">
                  {log.tenantName}
                  <span className="ml-1.5 font-mono text-xs text-fg-muted">(ID {log.tenantId})</span>
                </Link>
              ) : (
                <span className="font-mono">#{log.tenantId}</span>
              )}
            </DetailInfoRow>
          )}
          <DetailInfoRow label={t('detail.labelAuthAttempt')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            {log.authAttemptId != null ? (
              <span className="font-mono">#{log.authAttemptId}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelIpAddress')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            {log.ipAddress ? (
              <span className="font-mono text-xs">{log.ipAddress}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelUserAgent')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            <pre className="font-mono text-xs leading-snug text-fg-muted bg-fg/5 p-2 border border-fg/10 whitespace-pre-wrap break-words min-h-[3.25rem] max-w-full">
              {log.userAgent?.trim() ? log.userAgent : '—'}
            </pre>
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelReason')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            <pre className="text-xs bg-fg/5 p-2 overflow-auto max-h-32 whitespace-pre-wrap border border-fg/10">
              {log.reason?.trim() ? log.reason : '—'}
            </pre>
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelDetails')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            <pre className="text-xs leading-normal bg-fg/5 p-2 overflow-auto max-h-32 min-h-[5.5rem] whitespace-pre-wrap border border-fg/10">
              {log.eventDetails?.trim() ? log.eventDetails : '—'}
            </pre>
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelError')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            <div className="text-xs bg-fg/5 p-2 overflow-auto max-h-32 border border-fg/10">
              {log.errorMessage ? (
                <span className="text-error">{log.errorMessage}</span>
              ) : (
                <span className="text-fg-muted">—</span>
              )}
            </div>
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelCreated')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            <span className="text-fg-muted">
              {log.createdAt ? formatDateWithTimezone(log.createdAt) : '—'}
            </span>
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelHmacIntegrity')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            {entryIntegrityLoading ? (
              <span className="text-xs text-fg-muted">{t('detail.hmacChecking')}</span>
            ) : entryHmacDisplayState === 'unsigned' ? (
              <span className="text-xs text-fg-muted">{t('detail.hmacUnsigned')}</span>
            ) : (
              <div className="flex items-center gap-1.5">
                <EntryHmacBadge state={entryHmacDisplayState} />
                <span className={cn('text-xs font-bold', detailHmacStatusClass(entryHmacDisplayState))}>
                  {detailHmacStatusLabel(entryHmacDisplayState, t)}
                </span>
              </div>
            )}
          </DetailInfoRow>
          <DetailInfoRow label={t('detail.labelInstance')} className="min-w-0" valueClassName="min-w-0 flex-1 break-all">
            {log.instanceId ? (
              <span className="font-mono text-xs text-fg-muted">{log.instanceId}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </DetailInfoRow>
        </dl>
        <div className="flex justify-end pt-4">
          <Button onClick={onClose}>{t('detail.close')}</Button>
        </div>
      </div>
    </Dialog>
  );
}


// ── Main page ─────────────────────────────────────────────────────────────────

export default function AuditLogsPage() {
  const { t } = useTranslation('audit-logs');
  const { effectiveTimeZoneId } = useDisplayTimezone();
  const [searchParams, setSearchParams] = useSearchParams();
  const [eventFilter, setEventFilter] = useState(() => searchParams.get('eventType') ?? '');
  const [eventStatusFilter, setEventStatusFilter] = useState<AuditEventStatusFilter>(
    () => parseEventStatusFilter(searchParams.get('eventStatus')),
  );
  const [apiNameFilter, setApiNameFilter] = useState<AuditApiNameFilter>(
    () => parseApiNameFilter(searchParams.get('apiName')),
  );
  const [enrollmentFilter, setEnrollmentFilter] = useState(
    () => searchParams.get('enrollmentId') ?? '',
  );
  const [contextAnchorAuditLogId, setContextAnchorAuditLogId] = useState(
    () => parsePositiveIntegerString(searchParams.get('anchorAuditLogId')),
  );
  const [contextBeforeCount, setContextBeforeCount] = useState(
    () => parseContextCount(searchParams.get('beforeCount')),
  );
  const [contextAfterCount, setContextAfterCount] = useState(
    () => parseContextCount(searchParams.get('afterCount')),
  );
  const [authAttemptFilter, setAuthAttemptFilter] = useState(
    () => searchParams.get('authAttemptId') ?? '',
  );
  const [integrationFilter, setIntegrationFilter] = useState(
    () => searchParams.get('integrationId') ?? '',
  );
  const [contextSource, setContextSource] = useState(() => searchParams.get('source') ?? '');
  const [contextEntityType, setContextEntityType] = useState<ContextEntityType>(() => {
    const explicitEntityType = parseContextEntityType(searchParams.get('contextEntityType'));
    if (explicitEntityType) {
      return explicitEntityType;
    }
    if (searchParams.get('source') === 'enrollment-detail' && searchParams.get('enrollmentId')) {
      return 'enrollment';
    }
    if (searchParams.get('source') === 'auth-attempt-detail' && searchParams.get('authAttemptId')) {
      return 'authAttempt';
    }
    if (searchParams.get('source') === 'integration-detail' && searchParams.get('integrationId')) {
      return 'integration';
    }
    return '';
  });
  const [contextEntityId, setContextEntityId] = useState(
    () => searchParams.get('contextEntityId')
      ?? searchParams.get('enrollmentId')
      ?? searchParams.get('authAttemptId')
      ?? searchParams.get('integrationId')
      ?? '',
  );
  const [contextBaseDateRange, setContextBaseDateRange] = useState<{
    createdAfter: string;
    createdBefore: string;
  } | null>(() => {
    const baseCreatedAfter = searchParams.get('contextBaseCreatedAfter');
    const baseCreatedBefore = searchParams.get('contextBaseCreatedBefore');

    if (baseCreatedAfter && baseCreatedBefore) {
      return { createdAfter: baseCreatedAfter, createdBefore: baseCreatedBefore };
    }

    const source = searchParams.get('source');
    const enrollmentId = searchParams.get('enrollmentId');
    const authAttemptId = searchParams.get('authAttemptId');
    const integrationId = searchParams.get('integrationId');

    if ((source === 'enrollment-detail' && enrollmentId)
        || (source === 'auth-attempt-detail' && authAttemptId)
        || (source === 'integration-detail' && integrationId)) {
      return getLast24HoursWindow();
    }

    return null;
  });
  const [dateRange, setDateRange] = useState(() => ({
    from: toDateInputValue(searchParams.get('createdAfter')),
    to: toDateInputValue(searchParams.get('createdBefore')),
  }));
  const [contextualDateRange, setContextualDateRange] = useState<{
    createdAfter: string;
    createdBefore: string;
  } | null>(() => {
    const createdAfter = searchParams.get('createdAfter');
    const createdBefore = searchParams.get('createdBefore');
    const source = searchParams.get('source');
    const enrollmentId = searchParams.get('enrollmentId');
    const authAttemptId = searchParams.get('authAttemptId');
    const integrationId = searchParams.get('integrationId');

    if (createdAfter && createdBefore) {
      return { createdAfter, createdBefore };
    }
    if ((source === 'enrollment-detail' && enrollmentId)
        || (source === 'auth-attempt-detail' && authAttemptId)
        || (source === 'integration-detail' && integrationId)) {
      return getLast24HoursWindow();
    }
    return null;
  });
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const anchorAuditLogId = contextAnchorAuditLogId ? Number(contextAnchorAuditLogId) : null;
  const isAuditLogContextMode = anchorAuditLogId !== null;
  const isEntityContextSource = contextSource === 'enrollment-detail'
    || contextSource === 'auth-attempt-detail'
    || contextSource === 'integration-detail';
  const isExpandedEntityContext = contextSource === 'context-expanded';
  const hasEntityContext = Boolean(enrollmentFilter || authAttemptFilter || integrationFilter);
  const canExpandEntityContext = !isAuditLogContextMode
    && isEntityContextSource
    && hasEntityContext
    && contextualDateRange !== null;
  const canRestoreExpandedContext = !isAuditLogContextMode
    && isExpandedEntityContext
    && Boolean(contextEntityType && contextEntityId);
  const expandedContextEntityId = Number.parseInt(contextEntityId, 10);
  const [integritySession, setIntegritySession] = useState<IntegrityInvestigationSession | null>(
    () => loadIntegrityInvestigationSession(),
  );

  useEffect(() => {
    setIntegritySession(loadIntegrityInvestigationSession());
  }, [searchParams]);

  const handleDetailEntryIntegrityResolved = useCallback(
    (auditLogId: number, report: IntegrityReport) => {
      const violation = report.entryViolations?.items?.[0] ?? null;
      const hasViolation =
        violation != null || (report.invalidEntries ?? 0) > 0 || report.intact === false;
      setIntegritySession((current) => {
        if (!hasViolation && !current) {
          return current;
        }
        return mergeSingleEntryVerificationIntoSession(
          current,
          auditLogId,
          violation,
          violation?.createdAt,
        );
      });
    },
    [],
  );

  const listApiDateParams = contextualDateRange
    ? {
        createdAfter: contextualDateRange.createdAfter,
        createdBefore: contextualDateRange.createdBefore,
      }
    : dateRange.from && dateRange.to
      ? dateRangeToApiParams(dateRange.from, dateRange.to, effectiveTimeZoneId)
      : { createdAfter: undefined as string | undefined, createdBefore: undefined as string | undefined };

  const eventFilterParams = auditEventFilterToApiParams(eventFilter);

  useEffect(() => {
    const next = new URLSearchParams();

    if (eventFilter) {
      next.set('eventType', eventFilter);
    }
    if (eventStatusFilter) {
      next.set('eventStatus', eventStatusFilter);
    }
    if (apiNameFilter) {
      next.set('apiName', apiNameFilter);
    }
    if (enrollmentFilter) {
      next.set('enrollmentId', enrollmentFilter);
    }
    if (contextAnchorAuditLogId) {
      next.set('anchorAuditLogId', contextAnchorAuditLogId);
      next.set('beforeCount', String(contextBeforeCount));
      next.set('afterCount', String(contextAfterCount));
    }
    if (authAttemptFilter) {
      next.set('authAttemptId', authAttemptFilter);
    }
    if (integrationFilter) {
      next.set('integrationId', integrationFilter);
    }
    if (listApiDateParams.createdAfter && listApiDateParams.createdBefore) {
      next.set('createdAfter', listApiDateParams.createdAfter);
      next.set('createdBefore', listApiDateParams.createdBefore);
    }
    if (contextSource) {
      next.set('source', contextSource);
    }
    if (contextEntityType) {
      next.set('contextEntityType', contextEntityType);
    }
    if (contextEntityId) {
      next.set('contextEntityId', contextEntityId);
    }
    if (contextBaseDateRange?.createdAfter && contextBaseDateRange.createdBefore) {
      next.set('contextBaseCreatedAfter', contextBaseDateRange.createdAfter);
      next.set('contextBaseCreatedBefore', contextBaseDateRange.createdBefore);
    }
    if (next.toString() !== searchParams.toString()) {
      setSearchParams(next, { replace: true });
    }
  }, [
    apiNameFilter,
    authAttemptFilter,
    contextAfterCount,
    contextAnchorAuditLogId,
    contextBaseDateRange,
    contextBeforeCount,
    contextEntityId,
    contextEntityType,
    contextSource,
    enrollmentFilter,
    eventFilter,
    eventStatusFilter,
    integrationFilter,
    listApiDateParams.createdAfter,
    listApiDateParams.createdBefore,
    searchParams,
    setSearchParams,
  ]);

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<AuditLogResponseDto, AuditLogQueryParams>({
    queryKey: [
      'audit-logs',
      eventFilter,
      eventStatusFilter,
      apiNameFilter,
      enrollmentFilter,
      authAttemptFilter,
      integrationFilter,
      listApiDateParams.createdAfter ?? '',
      listApiDateParams.createdBefore ?? '',
      effectiveTimeZoneId,
    ],
    baseParams: {
      ...eventFilterParams,
      eventStatus: eventStatusFilter || undefined,
      apiName: apiNameFilter || undefined,
      enrollmentId: enrollmentFilter ? Number(enrollmentFilter) : undefined,
      authAttemptId: authAttemptFilter ? Number(authAttemptFilter) : undefined,
      integrationId: integrationFilter ? Number(integrationFilter) : undefined,
      createdAfter: listApiDateParams.createdAfter,
      createdBefore: listApiDateParams.createdBefore,
    },
    fetchPage: (params) => getAuditLogs(params as GetAuditLogsParams) as Promise<PagedModelAuditLogResponseDto>,
    enabled: !isAuditLogContextMode,
  });

  const contextParams: GetAuditLogContextParams | undefined = anchorAuditLogId === null
    ? undefined
    : {
        beforeCount: contextBeforeCount,
        afterCount: contextAfterCount,
      };

  const {
    data: contextResponse,
    isLoading: isContextLoading,
    refetch: refetchContext,
  } = useQuery({
    queryKey: ['audit-log-context', anchorAuditLogId, contextBeforeCount, contextAfterCount],
    queryFn: () => getAuditLogContext(
      anchorAuditLogId!,
      contextParams,
    ) as Promise<AuditLogContextResponseDto>,
    enabled: isAuditLogContextMode,
  });

  const activeData = isAuditLogContextMode ? (contextResponse?.items ?? []) : data;
  const activeIsLoading = isAuditLogContextMode ? isContextLoading : isLoading;
  const activeRefetch = isAuditLogContextMode ? refetchContext : refetch;

  const selectedLog = selectedIndex !== null ? activeData[selectedIndex] ?? null : null;
  const showRowNav = activeData.length > 1;
  const hasPrev = selectedIndex !== null && selectedIndex > 0;
  const hasNext = selectedIndex !== null && selectedIndex < activeData.length - 1;
  const showEndOfPageHint =
    !isAuditLogContextMode &&
    selectedIndex !== null &&
    activeData.length > 0 &&
    selectedIndex === activeData.length - 1 &&
    !pagination.isLast;

  const goPrevLog = useCallback(() => {
    setSelectedIndex((i) => (i !== null && i > 0 ? i - 1 : i));
  }, []);

  const goNextLog = useCallback(() => {
    setSelectedIndex((i) => {
      if (i === null) return i;
      return i < activeData.length - 1 ? i + 1 : i;
    });
  }, [activeData.length]);

  const expandEntityContext = useCallback(() => {
    if (contextualDateRange) {
      setContextBaseDateRange(contextualDateRange);
    }

    if (enrollmentFilter) {
      setContextEntityType('enrollment');
      setContextEntityId(enrollmentFilter);
    } else if (authAttemptFilter) {
      setContextEntityType('authAttempt');
      setContextEntityId(authAttemptFilter);
    } else if (integrationFilter) {
      setContextEntityType('integration');
      setContextEntityId(integrationFilter);
    }

    setEnrollmentFilter('');
    setAuthAttemptFilter('');
    setIntegrationFilter('');
    setContextSource('context-expanded');
    setContextualDateRange(getLast48HoursWindow());
    setDateRange({ from: '', to: '' });
    setSelectedIndex(null);
  }, [authAttemptFilter, contextualDateRange, enrollmentFilter, integrationFilter]);

  const restoreEntityContext = useCallback(() => {
    setEnrollmentFilter(contextEntityType === 'enrollment' ? contextEntityId : '');
    setAuthAttemptFilter(contextEntityType === 'authAttempt' ? contextEntityId : '');
    setIntegrationFilter(contextEntityType === 'integration' ? contextEntityId : '');

    if (contextEntityType === 'enrollment') {
      setContextSource('enrollment-detail');
    } else if (contextEntityType === 'authAttempt') {
      setContextSource('auth-attempt-detail');
    } else if (contextEntityType === 'integration') {
      setContextSource('integration-detail');
    } else {
      setContextSource('');
    }

    setContextualDateRange(getLast24HoursWindow());
    setDateRange({ from: '', to: '' });
    setSelectedIndex(null);
  }, [contextEntityId, contextEntityType]);

  const isWithinBaseContextRange = useCallback((createdAt?: string | null) => {
    if (!contextBaseDateRange || !createdAt) {
      return false;
    }

    const createdAtMs = new Date(createdAt).getTime();
    const baseStartMs = new Date(contextBaseDateRange.createdAfter).getTime();
    const baseEndMs = new Date(contextBaseDateRange.createdBefore).getTime();

    if (Number.isNaN(createdAtMs) || Number.isNaN(baseStartMs) || Number.isNaN(baseEndMs)) {
      return false;
    }

    return createdAtMs >= baseStartMs && createdAtMs <= baseEndMs;
  }, [contextBaseDateRange]);

  const matchesFocusedContext = useCallback((row: AuditLogResponseDto) => {
    if (!canRestoreExpandedContext || !Number.isInteger(expandedContextEntityId) || expandedContextEntityId <= 0) {
      return false;
    }

    if (!isWithinBaseContextRange(row.createdAt)) {
      return false;
    }

    if (contextEntityType === 'enrollment') {
      return row.enrollmentId === expandedContextEntityId;
    }
    if (contextEntityType === 'authAttempt') {
      return row.authAttemptId === expandedContextEntityId;
    }
    if (contextEntityType === 'integration') {
      return row.integrationId === expandedContextEntityId;
    }
    return false;
  }, [canRestoreExpandedContext, contextEntityType, expandedContextEntityId, isWithinBaseContextRange]);

  const clearAuditLogContext = useCallback(() => {
    setContextAnchorAuditLogId('');
    setContextBeforeCount(10);
    setContextAfterCount(10);
    if (contextSource === 'audit-log-context') {
      setContextSource('');
    }
  }, [contextSource]);

  const loadMoreBefore = useCallback(() => {
    setContextBeforeCount((value) => Math.min(50, value + 10));
  }, []);

  const loadMoreAfter = useCallback(() => {
    setContextAfterCount((value) => Math.min(50, value + 10));
  }, []);

  const columns: ColumnDef<AuditLogResponseDto>[] = [
    {
      header: t('list.columns.id'),
      key: 'auditLogId',
      className: 'w-14',
      sortKey: 'auditLogId',
      render: (r) => (
        <span className="inline-flex items-center gap-2">
          <span className="font-mono text-xs">{r.auditLogId}</span>
          {isAuditLogContextMode && r.auditLogId === anchorAuditLogId && (
            <Badge variant="warning">{t('list.anchorBadge')}</Badge>
          )}
          {!isAuditLogContextMode && matchesFocusedContext(r) && (
            <Badge variant="warning">{t('list.focusedContextBadge')}</Badge>
          )}
        </span>
      ),
    },
    {
      header: t('list.columns.event'),
      headerTooltip: t('list.columnTooltips.event'),
      key: 'eventType',
      sortKey: 'eventType',
      render: (r) => (
        <span className="font-mono text-xs">
          {getAuditEventTypeLabel(r.eventType ?? undefined, t)}
        </span>
      ),
    },
    {
      header: t('list.columns.status'),
      headerTooltip: t('list.columnTooltips.status'),
      key: 'eventStatus',
      sortKey: 'eventStatus',
      render: (r) => <EventStatusBadge status={r.eventStatus} />,
    },
    {
      header: t('list.columns.api'),
      headerTooltip: t('list.columnTooltips.api'),
      key: 'apiName',
      render: (r) => r.apiName
        ? <Badge variant="muted">{r.apiName.replace('_API', '')}</Badge>
        : <span className="text-fg-muted">—</span>,
    },
    {
      header: t('list.columns.admin'),
      key: 'adminId',
      render: (r) =>
        r.adminId ? (
          r.adminUsername ? (
            <Link to={adminListDetailHref(r.adminId)} className="text-xs font-medium text-accent hover:underline">
              {r.adminUsername}
            </Link>
          ) : (
            <span className="font-mono text-xs">#{r.adminId}</span>
          )
        ) : (
          <span className="text-fg-muted">—</span>
        ),
    },
    {
      header: t('list.columns.reason'),
      key: 'reason',
      render: (r) =>
        r.reason ? (
          <Tooltip content={r.reason}>
            <span className="text-xs line-clamp-2 max-w-[10rem] inline-block align-top">{r.reason}</span>
          </Tooltip>
        ) : (
          <span className="text-fg-muted">—</span>
        ),
    },
    {
      header: t('list.columns.hmac'),
      headerTooltip: t('list.hmacTooltip'),
      key: 'entryHmac',
      render: (r) => (
        <EntryHmacBadge
          state={resolveEntryHmacDisplayState(
            r.auditLogId,
            Boolean(r.entryHmac),
            integritySession,
          )}
        />
      ),
    },
    {
      header: t('list.columns.time'),
      key: 'createdAt',
      sortKey: 'createdAt',
      className: 'min-w-[12rem]',
      render: (r) =>
        r.createdAt ? (
          <span className="flex flex-col gap-0.5">
            <span className="text-xs text-fg">{formatDateWithTimezone(r.createdAt)}</span>
            <span className="text-[10px] text-fg-muted">{formatRelativeTime(r.createdAt)}</span>
          </span>
        ) : (
          <span className="text-xs text-fg-muted">—</span>
        ),
    },
    {
      header: '',
      key: 'detail',
      render: (r) => (
        <Tooltip content={t('list.viewDetails')}>
          <Button
            variant="ghost"
            size="sm"
            className="p-1"
            onClick={(e) => {
              e.stopPropagation();
              const idx = activeData.findIndex((x) => x.auditLogId === r.auditLogId);
              setSelectedIndex(idx >= 0 ? idx : null);
            }}
          >
            <Info className="size-3.5" />
          </Button>
        </Tooltip>
      ),
    },
  ];

  return (
    <AppShell title={t('list.title')}>
      <div className="space-y-4">
        {isAuditLogContextMode ? (
          <div className="space-y-3 border border-fg/20 bg-fg/[0.03] px-3 py-3 text-sm">
            <div className="flex flex-wrap items-center gap-2">
              <span>{t('list.contextualAroundEvent', { id: contextAnchorAuditLogId })}</span>
              <span className="text-fg-muted">
                {t('list.contextualAroundEventWindow', {
                  before: contextBeforeCount,
                  after: contextAfterCount,
                })}
              </span>
              <button
                type="button"
                className="ml-auto text-xs font-medium text-accent underline hover:text-accent/80"
                onClick={clearAuditLogContext}
              >
                {t('list.exitEventContext')}
              </button>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={loadMoreBefore}
                disabled={!contextResponse?.hasMoreBefore}
              >
                {t('list.loadMoreBefore')}
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={loadMoreAfter}
                disabled={!contextResponse?.hasMoreAfter}
              >
                {t('list.loadMoreAfter')}
              </Button>
              <Button type="button" variant="secondary" size="sm" onClick={() => activeRefetch()}>
                {t('list.refresh')}
              </Button>
            </div>
          </div>
        ) : (hasEntityContext || canRestoreExpandedContext) && (
          <div className="flex flex-wrap items-center gap-2 border border-fg/20 bg-fg/[0.03] px-3 py-2 text-sm">
            {enrollmentFilter && (
              <span>{t('list.contextualEnrollment', { id: enrollmentFilter })}</span>
            )}
            {authAttemptFilter && (
              <span>{t('list.contextualAuthAttempt', { id: authAttemptFilter })}</span>
            )}
            {integrationFilter && (
              <span>{t('list.contextualIntegration', { id: integrationFilter })}</span>
            )}
            {canRestoreExpandedContext && contextEntityType === 'enrollment' && (
              <span>{t('list.expandedContextFromEnrollment', { id: contextEntityId })}</span>
            )}
            {canRestoreExpandedContext && contextEntityType === 'authAttempt' && (
              <span>{t('list.expandedContextFromAuthAttempt', { id: contextEntityId })}</span>
            )}
            {canRestoreExpandedContext && contextEntityType === 'integration' && (
              <span>{t('list.expandedContextFromIntegration', { id: contextEntityId })}</span>
            )}
            {isEntityContextSource && contextualDateRange && (
              <span className="text-fg-muted">{t('list.contextualEnrollmentWindow')}</span>
            )}
            {canRestoreExpandedContext && contextualDateRange && (
              <span className="text-fg-muted">{t('list.expandedContextWindow')}</span>
            )}
            {canRestoreExpandedContext && (
              <span className="text-fg-muted">{t('list.focusedContextHint')}</span>
            )}
            <button
              type="button"
              className="ml-auto text-xs font-medium text-accent underline hover:text-accent/80"
              onClick={() => {
                setEnrollmentFilter('');
                setAuthAttemptFilter('');
                setIntegrationFilter('');
                setContextSource('');
                setContextEntityType('');
                setContextEntityId('');
                setContextBaseDateRange(null);
                setContextualDateRange(null);
                setDateRange({ from: '', to: '' });
              }}
            >
              {t('list.clearContextualFilter')}
            </button>
          </div>
        )}

        {/* Filter bar */}
        {!isAuditLogContextMode && <div className="flex gap-3 items-center flex-wrap">
          <div className="min-w-[15rem] w-64">
            <Select value={eventFilter} onChange={(e) => setEventFilter(e.target.value)}>
              <option value="">{t('list.filterEventTypeAll')}</option>
              {AUDIT_EVENT_TYPE_GROUPS.map((group) => (
                <optgroup key={group.family} label={t(`eventFamily.${group.family}`)}>
                  <option value={group.family}>
                    {t('list.filterAllTypesInFamily', { family: t(`eventFamily.${group.family}`) })}
                  </option>
                  {group.memberKeys.map((val) => (
                    <option key={val} value={val}>
                      {t(`eventType.${val}`)}
                    </option>
                  ))}
                </optgroup>
              ))}
            </Select>
          </div>
          <div className="min-w-[10rem] w-40">
            <Select
              value={eventStatusFilter}
              onChange={(e) => setEventStatusFilter(parseEventStatusFilter(e.target.value))}
            >
              <option value="">{t('list.filterStatusAll')}</option>
              <option value="SUCCESS">{t('list.filterStatusSuccess')}</option>
              <option value="FAILURE">{t('list.filterStatusFailure')}</option>
              <option value="ERROR">{t('list.filterStatusError')}</option>
            </Select>
          </div>
          <div className="w-36">
            <Select
              value={apiNameFilter}
              onChange={(e) => setApiNameFilter(parseApiNameFilter(e.target.value))}
            >
              <option value="">{t('list.filterApiAll')}</option>
              <option value="ADMIN_API">{t('list.filterApiAdmin')}</option>
              <option value="AUTH_API">{t('list.filterApiAuth')}</option>
              <option value="INTEGRATION_API">{t('list.filterApiIntegration')}</option>
            </Select>
          </div>
          <DateRangeFilter
            value={dateRange}
            onChange={(next) => {
              setDateRange(next);
              setContextualDateRange(null);
              setContextSource('');
              setContextEntityType('');
              setContextEntityId('');
              setContextBaseDateRange(null);
            }}
            showClear={true}
            emptyOptionLabel={t('list.dateRangeFull')}
          />
          <div className="ml-auto flex items-center gap-2">
            {canRestoreExpandedContext && (
              <Tooltip content={t('list.restoreContextTooltip')}>
                <Button variant="secondary" size="sm" onClick={restoreEntityContext} className="gap-1.5">
                  {t('list.restoreContext')}
                </Button>
              </Tooltip>
            )}
            {canExpandEntityContext && (
              <Tooltip content={t('list.expandContextTooltip')}>
                <Button variant="secondary" size="sm" onClick={expandEntityContext} className="gap-1.5">
                  {t('list.expandContext')}
                </Button>
              </Tooltip>
            )}
            <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
              <ShieldCheck className="size-3.5" />
              {t('list.refresh')}
            </Button>
          </div>
        </div>}

        <p className="text-xs text-fg-muted italic">
          {isAuditLogContextMode ? t('list.contextualHint') : t('list.hint')}
        </p>

        <div>
          {isAuditLogContextMode ? (
            <DataTable
              columns={columns}
              data={activeData}
              isLoading={activeIsLoading}
              onRowClick={(row) => {
                const idx = activeData.findIndex((r) => r.auditLogId === row.auditLogId);
                setSelectedIndex(idx >= 0 ? idx : null);
              }}
              rowClassName={(row) =>
                row.auditLogId === anchorAuditLogId ? '!bg-accent/10 border-l-4 border-l-accent' : undefined}
              keyExtractor={(r, i) => r.auditLogId ?? i}
              emptyMessage={t('list.emptyMessage')}
            />
          ) : (
            <PaginatedTable
              columns={columns}
              data={data}
              isLoading={isLoading}
              onRowClick={(row) => {
                const idx = data.findIndex((r) => r.auditLogId === row.auditLogId);
                setSelectedIndex(idx >= 0 ? idx : null);
              }}
              keyExtractor={(r, i) => r.auditLogId ?? i}
              emptyMessage={t('list.emptyMessage')}
              currentSort={pagination.sort}
              onSort={pagination.setSort}
              pagination={pagination}
              rowClassName={(row) =>
                matchesFocusedContext(row) ? '!bg-warning/10 border-l-4 border-l-warning' : undefined
              }
            />
          )}
        </div>
      </div>

      <AuditLogDetailDialog
        log={selectedLog}
        onClose={() => setSelectedIndex(null)}
        onPrev={goPrevLog}
        onNext={goNextLog}
        hasPrev={hasPrev}
        hasNext={hasNext}
        showNav={showRowNav}
        showEndOfPageHint={showEndOfPageHint}
        onEntryIntegrityResolved={handleDetailEntryIntegrityResolved}
      />
    </AppShell>
  );
}
