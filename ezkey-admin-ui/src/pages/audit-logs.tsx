import { useState, useMemo, useCallback, useEffect } from 'react';
import { useTranslation, Trans } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { ShieldCheck, Info, ShieldAlert, Archive, AlertTriangle, CheckCircle, XCircle, ChevronDown, ChevronUp, ListOrdered, ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { ContextHelp } from '@/components/ui/context-help';
import { Tooltip } from '@/components/ui/tooltip';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { Dialog } from '@/components/ui/dialog';
import { DetailInfoRow } from '@/components/ui/detail-info-row';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select } from '@/components/ui/select';
import { useDetailNavigation } from '@/hooks/use-detail-navigation';
import { DetailDialogHeaderNav } from '@/components/ui/detail-dialog-header-nav';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import {
  dateRangeToApiParams,
  integrityExclusiveDateRangeToApiParams,
} from '@/lib/date-range-presets';
import { EventStatusBadge } from '@/components/feature/event-status-badge';
import { AUDIT_EVENT_TYPE_GROUPS, auditEventFilterToApiParams } from '@/lib/audit-event-type-family';
import { api } from '@/lib/api-client';
import { getAuditEventTypeLabel } from '@/lib/audit-event-type';
import { queryKeys } from '@/lib/query-keys';
import { adminListDetailHref } from '@/lib/list-detail-navigation';
import { cn, formatDateOnly, formatDateWithTimezone, formatRelativeTime } from '@/lib/utils';
import {
  loadIntegrityInvestigationSession,
  mergeSingleEntryVerificationIntoSession,
  parseHighlightAuditLogIds,
  resolveEntryHmacDisplayState,
  resolveIntegrityReportEntryDisplayState,
  saveIntegrityInvestigationSession,
  buildInvestigationSession,
  type IntegrityInvestigationSession,
  type EntryHmacDisplayState,
} from '@/lib/integrity-investigation-session';
import { useAuth } from '@/context/use-auth';
import { useDisplayTimezone } from '@/context/use-display-timezone';
import { useToast } from '@/context/use-toast';
import { EntryHmacBadge } from '@/components/feature/entry-hmac-badge';
import {
  checkChainIntegrity,
  checkIntegrity,
  checkSingleEntryIntegrity,
  getArchiveEligibility,
  getAuditLogContext,
  getAuditLogs,
  getChainCheckpoints,
  runRetroactiveIntegrityValidation,
  useDeclareGap,
  useSealArchive,
} from '@/generated/admin-api/audit-logs/audit-logs';
import type {
  AuditLogContextResponseDto,
  AuditChainCheckpointResponseDto,
  ArchiveEligibilityResult,
  AuditLogResponseDto,
  ChainVerificationReport,
  GetAuditLogContextParams,
  GetAuditLogsParams,
  GetChainCheckpointsParams,
  IntegrityReport,
  ArchiveSealResult,
  GapDeclarationResult,
  PagedModelAuditLogResponseDto,
  PagedModelAuditChainCheckpointResponseDto,
  RetroactiveIntegrityValidationRunResponse,
} from '@/generated/admin-api/model';

type AuditLogQueryParams = GetAuditLogsParams & {
  authAttemptId?: number;
  integrationId?: number;
};
type AuditEventStatusFilter = NonNullable<GetAuditLogsParams['eventStatus']> | '';
type AuditApiNameFilter = NonNullable<GetAuditLogsParams['apiName']> | '';
type ContextEntityType = 'enrollment' | 'authAttempt' | 'integration' | '';

/** Operational heartbeat incident row (Admin API lifecycle). */
type AuditChainIncidentRow = {
  incidentId: number;
  status: 'IN_PROGRESS' | 'RECOVERED_PENDING_DECLARATION' | 'CLOSED';
  anchorCheckpointId: number | null;
  staleSince: string | null;
  degradedSince: string | null;
  recoveredAt: string | null;
  justification: string | null;
  rootCause: string | null;
  declaredAt: string | null;
  declaredByAdminId: number | null;
  createdAt: string | null;
};

const AUDIT_CHAIN_INCIDENT_ROOT_CAUSES = [
  'ADMIN_API_DOWN',
  'SCHEDULER_FAILURE',
  'DB_UNAVAILABLE',
  'NETWORK_PARTITION',
  'MISCONFIGURATION',
  'UNKNOWN',
] as const;

function ReportBadge({
  intact,
  status,
  intactLabel,
  undeclaredGapsLabel,
  violationLabel,
}: {
  intact?: boolean;
  status?: string;
  intactLabel: string;
  undeclaredGapsLabel: string;
  violationLabel: string;
}) {
  if (intact === true) {
    return (
      <Badge variant="success">
        <CheckCircle className="size-3 mr-1" />
        {intactLabel}
      </Badge>
    );
  }
  if (status === 'UNDECLARED_GAP_DETECTED') {
    return (
      <Badge variant="warning">
        <AlertTriangle className="size-3 mr-1" />
        {undeclaredGapsLabel}
      </Badge>
    );
  }
  if (intact === false) {
    return (
      <Badge variant="error">
        <XCircle className="size-3 mr-1" />
        {violationLabel}
      </Badge>
    );
  }
  return null;
}

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

// ── Checkpoint type badge and timeline table ───────────────────────────────────

type CheckpointRowItem =
  | { kind: 'checkpoint'; row: AuditChainCheckpointResponseDto }
  | { kind: 'gap'; gapEnd: string; gapStart: string; durationMin: number };

function CheckpointTypeBadge({ type }: { type?: string }) {
  const { t } = useTranslation('audit-logs');
  if (type === 'REGULAR') {
    return (
      <Tooltip content={t('integrity.helpCheckpointRegular')}>
        <Badge variant="muted">{t('integrity.checkpointTypeRegular')}</Badge>
      </Tooltip>
    );
  }
  if (type === 'ARCHIVE_SEAL') {
    return (
      <Tooltip content={t('integrity.helpCheckpointArchiveSeal')}>
        <Badge variant="success">{t('integrity.checkpointTypeSealed')}</Badge>
      </Tooltip>
    );
  }
  if (type === 'GAP_DECLARATION') {
    return (
      <Tooltip content={t('integrity.helpCheckpointGapDeclaration')}>
        <Badge variant="warning">{t('integrity.checkpointTypeGap')}</Badge>
      </Tooltip>
    );
  }
  return <span className="text-fg-muted">—</span>;
}

function CheckpointLifecycleStateBadge({ state }: { state?: string }) {
  const { t } = useTranslation('audit-logs');

  if (state === 'ACTIVE') return <Badge variant="muted">{t('integrity.lifecycleStateActive')}</Badge>;
  if (state === 'SEALED') return <Badge variant="success">{t('integrity.lifecycleStateSealed')}</Badge>;
  if (state === 'EXPORTED') return <Badge variant="muted">{t('integrity.lifecycleStateExported')}</Badge>;
  if (state === 'PURGEABLE') return <Badge variant="warning">{t('integrity.lifecycleStatePurgeable')}</Badge>;
  if (state === 'PURGED') return <Badge variant="error">{t('integrity.lifecycleStatePurged')}</Badge>;

  return <span className="text-fg-muted">—</span>;
}

function CheckpointTimelineTable({
  rows,
  isLoading,
  currentSort,
  onSort,
  focusGap,
  focusCheckpointId,
}: {
  rows: CheckpointRowItem[];
  isLoading: boolean;
  currentSort: string;
  onSort: (s: string) => void;
  /** When set, highlight checkpoint rows immediately before/after this gap (bordering rows). */
  focusGap?: { gapStart: string; gapEnd: string } | null;
  /** When set, highlight the checkpoint row with this id (integrity / gap deep link). */
  focusCheckpointId?: number | null;
}) {
  const [field = '', dir = ''] = currentSort.split(',');
  const handleSort = (sortKey: string) => {
    if (sortKey === field) onSort(`${sortKey},${dir === 'ASC' ? 'DESC' : 'ASC'}`);
    else onSort(`${sortKey},DESC`);
  };
  const { t } = useTranslation('audit-logs');
  const sortable = (sortKey: string, label: string, colClass?: string) => {
    const isActive = field === sortKey;
    return (
      <th key={sortKey} className={cn('px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider', colClass)}>
        <button
          type="button"
          onClick={() => handleSort(sortKey)}
          className="inline-flex items-center gap-1.5 cursor-pointer select-none hover:bg-white/10 transition-colors"
        >
          {label}
          {isActive ? (dir === 'ASC' ? <ArrowUp className="size-3 opacity-90" /> : <ArrowDown className="size-3 opacity-90" />) : <ArrowUpDown className="size-3 opacity-40" />}
        </button>
      </th>
    );
  };
  const cols = 7;

  return (
    <div className="w-full overflow-x-auto border-2 border-fg">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="bg-fg text-surface">
            {sortable('checkpointId', t('integrity.timelineColId'), 'w-20')}
            {sortable('windowStart', t('integrity.timelineColWindowStart'))}
            {sortable('windowEnd', t('integrity.timelineColWindowEnd'))}
            {sortable('entryCount', t('integrity.timelineColEntries'), 'w-20')}
            {sortable('checkpointType', t('integrity.timelineColType'), 'w-24')}
            {sortable('lifecycleState', t('integrity.timelineColLifecycleState'), 'w-24')}
            <th className="px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider">{t('integrity.timelineColNotes')}</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={cols} className="px-3 py-10 text-center text-fg-muted">
                <span className="inline-block size-5 border-2 border-fg border-t-transparent rounded-full animate-spin" />
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={cols} className="px-3 py-10 text-center text-fg-muted text-sm italic">
                {t('integrity.noCheckpoints')}
              </td>
            </tr>
          ) : (
            rows.map((item, index) => {
              if (item.kind === 'gap') {
                return (
                  <tr key={`gap-${item.gapEnd}-${item.gapStart}`} className="bg-warning/10 border-l-4 border-warning">
                    <td colSpan={cols} className="px-3 py-2 text-sm">
                      <span className="font-bold text-warning">{t('integrity.checkpointTypeGap')}:</span>{' '}
                      {formatDateWithTimezone(item.gapEnd)} → {formatDateWithTimezone(item.gapStart)}
                      {item.durationMin > 0 && (
                        <span className="text-fg-muted ml-2">(~{item.durationMin} min)</span>
                      )}
                    </td>
                  </tr>
                );
              }
              const r = item.row;
              const gapStartMs = focusGap ? new Date(focusGap.gapStart).getTime() : null;
              const gapEndMs = focusGap ? new Date(focusGap.gapEnd).getTime() : null;
              const rowEndMs = r.windowEnd ? new Date(r.windowEnd).getTime() : null;
              const rowStartMs = r.windowStart ? new Date(r.windowStart).getTime() : null;
              const isAnchor = focusGap && gapStartMs != null && rowEndMs === gapStartMs;
              const isAfterGap = focusGap && gapEndMs != null && rowStartMs === gapEndMs;
              const isBorderingGap = isAnchor || isAfterGap;
              const isFocusedCheckpoint =
                focusCheckpointId != null && r.checkpointId === focusCheckpointId;
              return (
                <tr
                  key={r.checkpointId ?? index}
                  className={cn(
                    'border-b border-fg/10 bg-surface even:bg-bg',
                    isBorderingGap && '!bg-warning/25 border-2 border-warning shadow-brutal',
                    isFocusedCheckpoint && '!bg-accent/15 border-l-4 border-l-accent',
                  )}
                >
                  <td className="px-3 py-2 font-mono text-xs">
                    {isBorderingGap ? (
                      <span className="inline-flex items-center gap-1.5">
                        <span className="font-black text-warning">{r.checkpointId ?? '—'}</span>
                        <Badge variant="warning" className="text-[10px] px-1.5 py-0">{isAnchor ? t('integrity.anchorBadge') : t('integrity.afterGapBadge')}</Badge>
                      </span>
                    ) : (
                      r.checkpointId ?? '—'
                    )}
                  </td>
                  <td className={cn('px-3 py-2 text-xs', isBorderingGap ? 'text-fg font-semibold' : 'text-fg-muted')}>{r.windowStart ? formatDateWithTimezone(r.windowStart) : '—'}</td>
                  <td className={cn('px-3 py-2 text-xs', isBorderingGap ? 'text-fg font-semibold' : 'text-fg-muted')}>{r.windowEnd ? formatDateWithTimezone(r.windowEnd) : '—'}</td>
                  <td className="px-3 py-2 font-mono text-xs">{r.entryCount ?? 0}</td>
                  <td className="px-3 py-2">
                    <CheckpointTypeBadge type={r.checkpointType} />
                  </td>
                  <td className="px-3 py-2">
                    <CheckpointLifecycleStateBadge state={r.lifecycleState} />
                  </td>
                  <td className="px-3 py-2 text-xs text-fg-muted max-w-32 truncate" title={r.notes ?? undefined}>
                    {r.notes ?? '—'}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}

// ── Integrity Panel (GLOBAL_ADMIN only) ───────────────────────────────────────

function IntegrityPanel({
  expandFromQuery = false,
  focusCheckpointId = null,
  initialCheckRange = null,
}: {
  expandFromQuery?: boolean;
  focusCheckpointId?: number | null;
  initialCheckRange?: { createdAfter: string; createdBefore: string } | null;
}) {
  const { t } = useTranslation('audit-logs');
  const { effectiveTimeZoneId } = useDisplayTimezone();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(expandFromQuery);

  /** Deep-link from Dashboard (?integrity=1): keep panel open when query requests it. */
  useEffect(() => {
    if (expandFromQuery) setExpanded(true);
  }, [expandFromQuery]);

  /** Scroll integrity section into view after opening from dashboard link. */
  useEffect(() => {
    if (!expanded || !expandFromQuery) return;
    requestAnimationFrame(() => {
      document.getElementById('integrity-lifecycle-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }, [expanded, expandFromQuery]);

  useEffect(() => {
    if (!initialCheckRange?.createdAfter || !initialCheckRange.createdBefore) {
      return;
    }
    setCheckRange({
      from: toDateInputValue(initialCheckRange.createdAfter),
      to: toDateInputValue(initialCheckRange.createdBefore),
    });
  }, [initialCheckRange?.createdAfter, initialCheckRange?.createdBefore]);

  useEffect(() => {
    if (focusCheckpointId != null) {
      setTimelineExpanded(true);
    }
  }, [focusCheckpointId]);

  // ── Check results ──
  const [chainReport, setChainReport] = useState<ChainVerificationReport | null>(null);
  const [integrityReport, setIntegrityReport] = useState<IntegrityReport | null>(null);
  const [chainReportRange, setChainReportRange] = useState<{ from: string; to: string } | null>(null);
  const [integrityReportRange, setIntegrityReportRange] = useState<{ from: string; to: string } | null>(null);
  const [chainLoading, setChainLoading] = useState(false);
  const [integrityLoading, setIntegrityLoading] = useState(false);
  const [validationRunLoading, setValidationRunLoading] = useState(false);
  const [validationRunResult, setValidationRunResult] =
    useState<RetroactiveIntegrityValidationRunResponse | null>(null);

  // ── Date range for integrity checks (shared DateRangeFilter) ──
  const [checkRange, setCheckRange] = useState({ from: '', to: '' });

  // ── Dialogs ──
  const [sealOpen, setSealOpen] = useState(false);
  const [gapOpen, setGapOpen] = useState(false);
  const [sealResult, setSealResult] = useState<ArchiveSealResult | null>(null);
  const [gapResult, setGapResult] = useState<GapDeclarationResult | null>(null);

  // ── Seal archive form state ──
  const [sealPeriodStart, setSealPeriodStart] = useState('');
  const [sealPeriodEnd, setSealPeriodEnd] = useState('');
  const [sealCheckpointFrom, setSealCheckpointFrom] = useState('');
  const [sealCheckpointTo, setSealCheckpointTo] = useState('');
  const [sealJustification, setSealJustification] = useState('');

  // ── Gap declaration state (driven by selection from the detected-gaps list) ──
  const [selectedGapForDeclaration, setSelectedGapForDeclaration] = useState<
    { gapStart: string; gapEnd: string; gapMinutes: number } | null
  >(null);
  const [gapJustification, setGapJustification] = useState('');

  // ── Checkpoint timeline (nested expandable) ──
  const [timelineExpanded, setTimelineExpanded] = useState(false);
  const [checkpointRange, setCheckpointRange] = useState({ from: '', to: '' });
  const [checkpointTypeFilter, setCheckpointTypeFilter] = useState('');
  /** Focused gap from "Undeclared gaps for consultation" – highlights bordering checkpoints in timeline */
  const [focusedGap, setFocusedGap] = useState<{ gapStart: string; gapEnd: string; gapMinutes: number } | null>(null);
  const [gapsListExpanded, setGapsListExpanded] = useState(true);

  const [incidentDeclareOpen, setIncidentDeclareOpen] = useState(false);
  const [incidentActive, setIncidentActive] = useState<AuditChainIncidentRow | null>(null);
  const [incidentJustification, setIncidentJustification] = useState('');
  const [incidentRootCause, setIncidentRootCause] =
    useState<(typeof AUDIT_CHAIN_INCIDENT_ROOT_CAUSES)[number]>('UNKNOWN');

  const {
    data: archiveEligibility,
    isLoading: archiveEligibilityLoading,
  } = useQuery({
    queryKey: ['audit-archive-eligibility'],
    queryFn: () => getArchiveEligibility() as Promise<ArchiveEligibilityResult>,
    enabled: expanded,
  });

  const {
    data: incidentsPage,
    isLoading: incidentsLoading,
    refetch: refetchIncidents,
  } = useQuery({
    queryKey: ['audit-chain-incidents'],
    queryFn: () =>
      api.get<{ content: AuditChainIncidentRow[] }>(
        '/api/v1/audit-logs/lifecycle/incidents?page=0&size=50&sort=createdAt,DESC',
      ),
    enabled: expanded,
  });

  const declareIncidentMutation = useMutation({
    mutationFn: ({
      incidentId,
      justification,
      rootCause,
    }: {
      incidentId: number;
      justification: string;
      rootCause: string;
    }) =>
      api.post<AuditChainIncidentRow>(
        `/api/v1/audit-logs/lifecycle/incidents/${incidentId}/declare`,
        { justification, rootCause },
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['audit-chain-incidents'] });
      setIncidentDeclareOpen(false);
      setIncidentActive(null);
      setIncidentJustification('');
      setIncidentRootCause('UNKNOWN');
      toast(t('integrity.incidents.declareSuccess'), 'success');
    },
    onError: (e: unknown) => {
      toast(getTranslatedApiError(e, t, t('integrity.incidents.declareError')), 'error');
    },
  });

  /** When a gap is focused, request a narrow window (gapStart − 1h to gapEnd + 1h) so page 0 contains the anchor and first checkpoint after the gap. Otherwise use the date-range filter. */
  const checkpointApiParams = useMemo(() => {
    if (focusedGap) {
      const start = new Date(focusedGap.gapStart);
      start.setHours(start.getHours() - 1, start.getMinutes(), start.getSeconds(), 0);
      const end = new Date(focusedGap.gapEnd);
      end.setHours(end.getHours() + 1, end.getMinutes(), end.getSeconds(), 0);
      return {
        windowStartAfter: start.toISOString(),
        windowStartBefore: end.toISOString(),
        checkpointType: checkpointTypeFilter || undefined,
      } as GetChainCheckpointsParams;
    }
    if (!checkpointRange.from || !checkpointRange.to) return {};
    const { createdAfter, createdBefore } = dateRangeToApiParams(
      checkpointRange.from,
      checkpointRange.to,
      effectiveTimeZoneId,
    );
    return {
      windowStartAfter: createdAfter,
      windowStartBefore: createdBefore,
      checkpointType: checkpointTypeFilter || undefined,
    } as GetChainCheckpointsParams;
  }, [focusedGap, checkpointRange.from, checkpointRange.to, checkpointTypeFilter, effectiveTimeZoneId]);

  const {
    data: checkpointData,
    pagination: checkpointPagination,
    isLoading: checkpointLoading,
    refetch: refetchCheckpoints,
  } = usePaginatedFromOrval<AuditChainCheckpointResponseDto, GetChainCheckpointsParams>({
    queryKey: [...queryKeys.auditChainCheckpoints, checkpointApiParams.windowStartAfter ?? '', checkpointApiParams.windowStartBefore ?? '', checkpointApiParams.checkpointType ?? ''],
    baseParams: checkpointApiParams,
    fetchPage: (params) =>
      getChainCheckpoints(params as GetChainCheckpointsParams) as Promise<PagedModelAuditChainCheckpointResponseDto>,
    defaultSize: 20,
    defaultSort: 'windowStart,ASC',
    enabled: timelineExpanded,
    /** When a gap is focused, show loading then correct page 0 instead of keeping previous (wide) data. */
    keepPreviousData: !focusedGap,
  });

  /** Rows to display: checkpoints in API order plus gap rows when sort is chronological. Gap detection is only valid when consecutive rows are in time order (sort by windowStart); otherwise do not insert gap rows to avoid misleading the operator. */
  const checkpointRowsWithGaps = useMemo(() => {
    const sortField = checkpointPagination.sort.split(',')[0] ?? '';
    const isChronologicalSort = sortField === 'windowStart';
    const out: Array<{ kind: 'checkpoint'; row: AuditChainCheckpointResponseDto } | { kind: 'gap'; gapEnd: string; gapStart: string; durationMin: number }> = [];
    for (let i = 0; i < checkpointData.length; i++) {
      out.push({ kind: 'checkpoint', row: checkpointData[i] });
      if (!isChronologicalSort) continue;
      const curr = checkpointData[i];
      const next = checkpointData[i + 1];
      if (next && curr.windowEnd && next.windowStart) {
        const end = new Date(curr.windowEnd).getTime();
        const start = new Date(next.windowStart).getTime();
        if (end < start) {
          out.push({
            kind: 'gap',
            gapEnd: curr.windowEnd,
            gapStart: next.windowStart,
            durationMin: Math.round((start - end) / 60000),
          });
        }
      }
    }
    return out;
  }, [checkpointData, checkpointPagination.sort]);

  /** Format Date to YYYY-MM-DD for checkpoint range filter. */
  function toYYYYMMDD(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  /** Focus a gap and navigate timeline: narrow API window so page 0 contains anchor + first-after gap; set date range to that same window so the filter matches the request; set sort, page 0, expand. */
  function focusGapAndNavigate(g: { gapStart: string; gapEnd: string; gapMinutes: number }) {
    setFocusedGap(g);
    const start = new Date(g.gapStart);
    start.setHours(start.getHours() - 1, start.getMinutes(), start.getSeconds(), 0);
    const end = new Date(g.gapEnd);
    end.setHours(end.getHours() + 1, end.getMinutes(), end.getSeconds(), 0);
    setCheckpointRange({ from: toYYYYMMDD(start), to: toYYYYMMDD(end) });
    checkpointPagination.setSort('windowStart,ASC');
    checkpointPagination.goToPage(0);
    setTimelineExpanded(true);
  }

  /**
   * Compute the default 7-day lookback window used by the auto-run on expand.
   * UI-side default; the backend policy belongs in a future slice (see plan).
   */
  function defaultGapScanRange(): { from: string; to: string } {
    const now = new Date();
    const past = new Date(now);
    past.setDate(past.getDate() - 7);
    return { from: toYYYYMMDD(past), to: toYYYYMMDD(now) };
  }

  function persistInvestigationSession(
    entry: IntegrityReport,
    chain: ChainVerificationReport,
    range: { from: string; to: string },
  ) {
    if (!initialCheckRange?.createdAfter || !initialCheckRange.createdBefore) {
      return;
    }
    const { createdAfter, createdBefore } = dateRangeToApiParams(
      range.from,
      range.to,
      effectiveTimeZoneId,
    );
    saveIntegrityInvestigationSession(
      buildInvestigationSession({
        source: 'integrity-alert',
        windowFrom: createdAfter ?? initialCheckRange.createdAfter,
        windowTo: createdBefore ?? initialCheckRange.createdBefore,
        entryViolations: entry.entryViolations?.items ?? [],
        chainViolations: chain.chainViolations ?? [],
        focusCheckpointId: focusCheckpointId ?? undefined,
      }),
    );
  }

  function integrityRangeToApiParams(range: { from: string; to: string }) {
    const { createdAfter, createdBefore } = integrityExclusiveDateRangeToApiParams(
      range.from,
      range.to,
      effectiveTimeZoneId,
    );
    return { from: createdAfter, to: createdBefore };
  }

  async function runChainCheck(rangeOverride?: { from: string; to: string }) {
    const range = rangeOverride ?? checkRange;
    setChainLoading(true);
    setChainReport(null);
    setChainReportRange(null);
    setFocusedGap(null);
    try {
      const params =
        range.from && range.to
          ? integrityRangeToApiParams(range)
          : { from: undefined as string | undefined, to: undefined as string | undefined };
      const report = await checkChainIntegrity(params) as unknown as ChainVerificationReport;
      setChainReport(report);
      if (range.from && range.to) {
        setChainReportRange({ from: range.from, to: range.to });
      }
      if (initialCheckRange && integrityReport) {
        persistInvestigationSession(integrityReport, report, range);
      }
    } catch (e) {
      toast(getTranslatedApiError(e, t, t('integrity.errorChainCheck')), 'error');
    } finally {
      setChainLoading(false);
    }
  }

  async function runIntegrityCheck() {
    setIntegrityLoading(true);
    setIntegrityReport(null);
    setIntegrityReportRange(null);
    try {
      const params =
        checkRange.from && checkRange.to
          ? integrityRangeToApiParams(checkRange)
          : { from: undefined as string | undefined, to: undefined as string | undefined };
      const report = await checkIntegrity(params) as unknown as IntegrityReport;
      setIntegrityReport(report);
      if (checkRange.from && checkRange.to) {
        setIntegrityReportRange({ from: checkRange.from, to: checkRange.to });
      }
      if (initialCheckRange && chainReport) {
        persistInvestigationSession(report, chainReport, checkRange);
      }
    } catch (e) {
      toast(getTranslatedApiError(e, t, t('integrity.errorIntegrityCheck')), 'error');
    } finally {
      setIntegrityLoading(false);
    }
  }

  async function runRetroactiveValidation() {
    if (!checkRange.from || !checkRange.to) {
      return;
    }
    setValidationRunLoading(true);
    setValidationRunResult(null);
    try {
      const { from: createdAfter, to: createdBefore } = integrityRangeToApiParams(checkRange);
      if (!createdAfter || !createdBefore) {
        return;
      }
      const result = (await runRetroactiveIntegrityValidation({
        from: createdAfter,
        to: createdBefore,
        raiseAlert: true,
      })) as unknown as RetroactiveIntegrityValidationRunResponse;
      setValidationRunResult(result);
      if (result.skipped) {
        toast(
          t('integrity.validationRun.skipped', { reason: result.skipReason ?? '—' }),
          'info',
        );
      } else if (result.alertRaised && result.alertId != null) {
        toast(t('integrity.validationRun.alertRaised', { id: result.alertId }), 'success');
        await queryClient.invalidateQueries({ queryKey: ['/api/v1/alerts'] });
      } else if (result.intact) {
        toast(t('integrity.validationRun.intact'), 'success');
      } else if ((result.entryHmacViolationCount ?? 0) > 0 || (result.chainViolationCount ?? 0) > 0) {
        toast(t('integrity.validationRun.violationsNoAlert'), 'info');
      } else {
        toast(t('integrity.validationRun.completed'), 'info');
      }
    } catch (e) {
      toast(getTranslatedApiError(e, t, t('integrity.validationRun.error')), 'error');
    } finally {
      setValidationRunLoading(false);
    }
  }

  const sealMutation = useSealArchive({
    mutation: {
      onSuccess: (data) => {
        const result = data as unknown as ArchiveSealResult;
        setSealResult(result);
        toast(t('integrity.toastSealSuccess', { count: result.checkpointsSealed }), 'success');
        queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs });
        queryClient.invalidateQueries({ queryKey: queryKeys.auditChainCheckpoints });
        queryClient.invalidateQueries({ queryKey: ['audit-archive-eligibility'] });
      },
      onError: (e) => toast(getTranslatedApiError(e, t, t('integrity.errorSeal')), 'error'),
    },
  });

  const gapMutation = useDeclareGap({
    mutation: {
      onSuccess: (data, variables) => {
        const result = data as unknown as GapDeclarationResult;
        setGapResult(result);
        toast(t('integrity.toastGapSuccess'), 'success');
        queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs });
        queryClient.invalidateQueries({ queryKey: queryKeys.auditChainCheckpoints });
        queryClient.invalidateQueries({ queryKey: ['audit-archive-eligibility'] });
        // Post-declaration refresh sequence (see plan):
        //   1) clear focus if it matched the declared gap,
        //   2) re-run the chain integrity check so the declared gap drops off the list.
        const declaredStart = variables?.data?.gapStart;
        const declaredEnd = variables?.data?.gapEnd;
        if (
          focusedGap !== null &&
          declaredStart === focusedGap.gapStart &&
          declaredEnd === focusedGap.gapEnd
        ) {
          setFocusedGap(null);
        }
        void runChainCheck();
      },
      onError: (e) => toast(getTranslatedApiError(e, t, t('integrity.errorGap')), 'error'),
    },
  });

  function resetSealForm() {
    setSealPeriodStart('');
    setSealPeriodEnd('');
    setSealCheckpointFrom('');
    setSealCheckpointTo('');
    setSealJustification('');
    setSealResult(null);
  }

  function resetGapForm() {
    setSelectedGapForDeclaration(null);
    setGapJustification('');
    setGapResult(null);
  }

  /** Open the declaration dialog for a specific detected gap (timestamps prefilled). */
  function openDeclareDialogFor(g: { gapStart: string; gapEnd: string; gapMinutes: number }) {
    setSelectedGapForDeclaration(g);
    setGapJustification('');
    setGapResult(null);
    setGapOpen(true);
  }

  function closeGapDialog() {
    setGapOpen(false);
    resetGapForm();
  }

  // Auto-run chain check on integrity section expand: populates the gaps list
  // without requiring the operator to click *Run integrity check* first.
  // UX shortcut only — backend discoverability is owned by AuditChainScheduler.
  useEffect(() => {
    if (!expanded) return;
    if (chainReport || chainLoading) return;

    const effectiveRange =
      checkRange.from && checkRange.to ? checkRange : defaultGapScanRange();

    if (!checkRange.from || !checkRange.to) {
      setCheckRange(effectiveRange);
    }

    void runChainCheck(effectiveRange);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expanded]);

  return (
    <div id="integrity-lifecycle-panel" className="border-2 border-fg/20 bg-main shadow-brutal scroll-mt-4">
      {/* Header — always visible */}
      <button
        type="button"
        className="w-full flex items-center justify-between p-4 text-left hover:bg-fg/5 transition-colors"
        onClick={() => setExpanded((v) => !v)}
      >
        <div className="flex items-center gap-2">
          <ShieldAlert className="size-5 text-accent" />
          <h2 className="font-black text-sm uppercase tracking-wider">{t('integrity.title')}</h2>
          <span onClick={(e) => e.stopPropagation()}>
            <ContextHelp title={t('integrity.title')} content={<Trans i18nKey="audit-logs:help.integrityLifecycle.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.title') })} />
          </span>
          <Badge variant="muted">{t('integrity.globalAdmin')}</Badge>
        </div>
        {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
      </button>

      {expanded && (
        <div className="border-t-2 border-fg/20 p-4 space-y-6">
          {/* ── Verification section ── */}
          <div className="space-y-3">
            <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.verification')}</h3>

            {/* Date range filter (range required for verification) */}
            <DateRangeFilter
              value={checkRange}
              onChange={setCheckRange}
              emptyOptionLabel={t('integrity.dateRangeSelect')}
            />

            <div className="flex flex-wrap items-center gap-3">
              <Button
                size="sm"
                variant="secondary"
                onClick={() => runChainCheck()}
                disabled={chainLoading || !checkRange.from || !checkRange.to}
                className="gap-1.5"
                title={!checkRange.from || !checkRange.to ? t('integrity.selectDateRangeToRun') : undefined}
              >
                <ShieldCheck className="size-3.5" />
                {chainLoading ? t('integrity.checking') : t('integrity.verifyChain')}
              </Button>
              <Button
                size="sm"
                variant="secondary"
                onClick={runIntegrityCheck}
                disabled={integrityLoading || !checkRange.from || !checkRange.to}
                className="gap-1.5"
                title={!checkRange.from || !checkRange.to ? t('integrity.selectDateRangeToRun') : undefined}
              >
                <ShieldCheck className="size-3.5" />
                {integrityLoading ? t('integrity.checking') : t('integrity.verifyEntry')}
              </Button>
              <Button
                size="sm"
                onClick={() => void runRetroactiveValidation()}
                disabled={
                  validationRunLoading
                  || !checkRange.from
                  || !checkRange.to
                }
                className="gap-1.5 ml-auto"
                title={
                  !checkRange.from || !checkRange.to
                    ? t('integrity.selectDateRangeToRun')
                    : undefined
                }
              >
                <ShieldAlert className="size-3.5" />
                {validationRunLoading ? t('integrity.validationRun.running') : t('integrity.runValidation')}
              </Button>
            </div>
            <p className="text-xs text-fg-muted">
              {t('integrity.verifyVsRunHint')}
            </p>

            {/* Chain report */}
            {chainReport && (
              <div className="border-2 border-fg/10 p-3 space-y-2 bg-bg">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs uppercase tracking-wider">{t('integrity.chainVerification')}</span>
                  <ReportBadge
                    intact={chainReport.intact}
                    status={(chainReport as { status?: string }).status}
                    intactLabel={t('integrity.reportIntact')}
                    undeclaredGapsLabel={t('integrity.reportUndeclaredGaps')}
                    violationLabel={t('integrity.reportViolation')}
                  />
                </div>
                {chainReportRange && (
                  <p className="text-xs text-fg-muted">
                    {t('integrity.periodFromTo', {
                      from: formatDateOnly(chainReportRange.from),
                      to: formatDateOnly(chainReportRange.to),
                    })}
                  </p>
                )}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  <Stat label={t('integrity.statTotal')} value={chainReport.totalCheckpoints} />
                  <Stat label={t('integrity.statValid')} value={chainReport.validCheckpoints} ok />
                  <Stat label={t('integrity.statInvalid')} value={chainReport.invalidCheckpoints} bad />
                  <Stat label={t('integrity.statArchived')} value={chainReport.archivedCheckpoints} />
                  <Stat label={t('integrity.statUndeclaredGaps')} value={(chainReport as { undeclaredGaps?: unknown[] }).undeclaredGaps?.length ?? 0} bad={((chainReport as { undeclaredGaps?: unknown[] }).undeclaredGaps?.length ?? 0) > 0} />
                </div>
                {chainReport.gapDeclaredCheckpoints != null && chainReport.gapDeclaredCheckpoints > 0 && (
                  <p className="text-xs text-fg-muted">{t('integrity.gapDeclaredCheckpoints', { count: chainReport.gapDeclaredCheckpoints })}</p>
                )}
                {chainReport.violations && chainReport.violations.length > 0 && (
                  <div className="mt-2">
                    <p className="text-xs font-bold text-error mb-1">{t('integrity.violations')}</p>
                    <ul className="text-xs text-error list-disc pl-4 space-y-0.5">
                      {chainReport.violations.map((v, i) => <li key={i}>{v}</li>)}
                    </ul>
                  </div>
                )}
                {chainReport.chainViolations && chainReport.chainViolations.length > 0 && (
                  <div className="mt-2">
                    <p className="text-xs font-bold text-error mb-1">{t('integrity.structuredChainViolations')}</p>
                    <ul className="text-xs text-error space-y-1">
                      {chainReport.chainViolations.map((v) => (
                        <li key={`${v.checkpointId}-${v.violationType}`} className="font-mono">
                          #{v.checkpointId} {v.violationType}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}

            {/* Entry integrity report */}
            {integrityReport && (
              <div className="border-2 border-fg/10 p-3 space-y-2 bg-bg">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs uppercase tracking-wider">{t('integrity.entryIntegrityReport')}</span>
                  <ReportBadge
                    intact={integrityReport.intact}
                    intactLabel={t('integrity.reportIntact')}
                    undeclaredGapsLabel={t('integrity.reportUndeclaredGaps')}
                    violationLabel={t('integrity.reportViolation')}
                  />
                </div>
                {integrityReportRange && (
                  <p className="text-xs text-fg-muted">
                    {t('integrity.periodFromTo', {
                      from: formatDateOnly(integrityReportRange.from),
                      to: formatDateOnly(integrityReportRange.to),
                    })}
                  </p>
                )}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  <Stat label={t('integrity.statTotal')} value={integrityReport.totalEntries} />
                  <Stat label={t('integrity.statValid')} value={integrityReport.validEntries} ok />
                  <Stat label={t('integrity.statInvalid')} value={integrityReport.invalidEntries} bad />
                  <Stat label={t('integrity.statUnsigned')} value={integrityReport.unsignedEntries} />
                </div>
                {integrityReport.entryViolations?.items && integrityReport.entryViolations.items.length > 0 && (
                  <div className="mt-2">
                    <p className="text-xs font-bold text-error mb-1">{t('integrity.structuredEntryViolations')}</p>
                    <ul className="text-xs text-error space-y-1">
                      {integrityReport.entryViolations.items.map((v) => (
                        <li key={v.auditLogId} className="font-mono">
                          #{v.auditLogId} — {v.reason ?? '—'}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}

            {validationRunResult && (
              <div className="border-2 border-fg/10 p-3 space-y-2 bg-bg">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-bold text-xs uppercase tracking-wider">
                    {t('integrity.validationRun.title')}
                  </span>
                  {validationRunResult.skipped ? (
                    <Badge variant="warning">{t('integrity.validationRun.skippedBadge')}</Badge>
                  ) : validationRunResult.intact ? (
                    <Badge variant="success">{t('integrity.reportIntact')}</Badge>
                  ) : (
                    <Badge variant="error">{t('integrity.reportViolation')}</Badge>
                  )}
                </div>
                {validationRunResult.skipReason && (
                  <p className="text-xs text-fg-muted">{validationRunResult.skipReason}</p>
                )}
                {validationRunResult.windowStart && validationRunResult.windowEnd && (
                  <p className="text-xs text-fg-muted font-mono break-all">
                    {t('integrity.validationRun.windowBounds', {
                      from: validationRunResult.windowStart,
                      to: validationRunResult.windowEnd,
                    })}
                  </p>
                )}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  <Stat
                    label={t('integrity.validationRun.entryViolations')}
                    value={validationRunResult.entryHmacViolationCount ?? 0}
                    bad={(validationRunResult.entryHmacViolationCount ?? 0) > 0}
                  />
                  <Stat
                    label={t('integrity.validationRun.entryAlertEligible')}
                    value={validationRunResult.entryAlertEligibleCount ?? 0}
                    bad={(validationRunResult.entryAlertEligibleCount ?? 0) > 0}
                  />
                  <Stat
                    label={t('integrity.validationRun.chainViolations')}
                    value={validationRunResult.chainViolationCount ?? 0}
                    bad={(validationRunResult.chainViolationCount ?? 0) > 0}
                  />
                  {validationRunResult.chainStatus && (
                    <div>
                      <p className="text-[10px] uppercase tracking-wider text-fg-muted">
                        {t('integrity.validationRun.chainStatus')}
                      </p>
                      <p className="font-mono text-xs font-bold">{validationRunResult.chainStatus}</p>
                    </div>
                  )}
                </div>
                {validationRunResult.alertRaised && validationRunResult.alertId != null && (
                  <Link
                    to={`/alerts/${validationRunResult.alertId}`}
                    className="inline-flex items-center gap-1 text-sm font-bold text-accent hover:underline"
                  >
                    {t('integrity.validationRun.viewAlert', { id: validationRunResult.alertId })}
                  </Link>
                )}
              </div>
            )}
          </div>

          {/* ── Lifecycle section ── */}
          <div className="space-y-3">
            <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.lifecycleOverview')}</h3>
            <div className="border-2 border-fg/10 bg-bg p-3 space-y-3">
              <div className="flex items-center justify-between gap-3 flex-wrap">
                <div>
                  <p className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.lifecyclePolicyTitle')}</p>
                  <p className="text-xs text-fg-muted">{t('integrity.lifecyclePolicyHint')}</p>
                </div>
                <Badge variant="muted">{t('integrity.policyDriven')}</Badge>
              </div>

              {archiveEligibilityLoading ? (
                <div className="text-xs text-fg-muted">{t('integrity.loadingLifecycleOverview')}</div>
              ) : archiveEligibility ? (
                <>
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 text-xs">
                    <Stat label={t('integrity.sealedCheckpointCount')} value={archiveEligibility.sealedCheckpointCount ?? 0} />
                    <div>
                      <p className="text-[10px] uppercase tracking-wider text-fg-muted">{t('integrity.externalArchival')}</p>
                      <p className="font-bold text-fg">{archiveEligibility.externalArchivalEnabled ? t('integrity.statusEnabled') : t('integrity.statusDisabled')}</p>
                    </div>
                    <div>
                      <p className="text-[10px] uppercase tracking-wider text-fg-muted">{t('integrity.confirmationRequired')}</p>
                      <p className="font-bold text-fg">{archiveEligibility.confirmationRequired ? t('integrity.statusYes') : t('integrity.statusNo')}</p>
                    </div>
                  </div>

                  <div className="grid gap-2 text-xs text-fg-muted sm:grid-cols-2">
                    <div className="border border-fg/10 p-2">
                      <p className="font-bold uppercase tracking-wider text-[10px] text-fg-muted">{t('integrity.awaitingArchiveWindow')}</p>
                      <p>
                        {archiveEligibility.oldestSealedWindowStart && archiveEligibility.newestSealedWindowEnd
                          ? t('integrity.lifecycleWindowFromTo', {
                              from: formatDateWithTimezone(archiveEligibility.oldestSealedWindowStart),
                              to: formatDateWithTimezone(archiveEligibility.newestSealedWindowEnd),
                            })
                          : t('integrity.noLifecycleWindow')}
                      </p>
                    </div>
                    <div className="border border-fg/10 p-2">
                      <p className="font-bold uppercase tracking-wider text-[10px] text-fg-muted">{t('integrity.awaitingArchiveCheckpointRange')}</p>
                      <p>
                        {archiveEligibility.checkpointIdFrom != null && archiveEligibility.checkpointIdTo != null
                          ? t('integrity.lifecycleCheckpointRange', {
                              from: archiveEligibility.checkpointIdFrom,
                              to: archiveEligibility.checkpointIdTo,
                            })
                          : t('integrity.noLifecycleWindow')}
                      </p>
                    </div>
                  </div>
                </>
              ) : (
                <div className="text-xs text-fg-muted">{t('integrity.noLifecycleOverview')}</div>
              )}
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <p className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.exceptionalMaintenance')}</p>
                <span onClick={(e) => e.stopPropagation()}>
                  <ContextHelp title={t('integrity.exceptionalMaintenance')} content={<Trans i18nKey="audit-logs:help.exceptionalMaintenance.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.exceptionalMaintenance') })} />
                </span>
              </div>
              <div className="flex gap-3 flex-wrap items-center">
              <Button size="sm" variant="secondary" onClick={() => { resetSealForm(); setSealOpen(true); }} className="gap-1.5">
                <Archive className="size-3.5" />
                {t('integrity.sealArchive')}
              </Button>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={t('integrity.sealArchive')} content={<Trans i18nKey="audit-logs:help.sealArchive.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.sealArchive') })} />
              </span>
              <span className="text-xs text-fg-muted italic">{t('integrity.declareGapHint')}</span>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={t('integrity.declareGap')} content={<Trans i18nKey="audit-logs:help.declareGap.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.declareGap') })} />
              </span>
            </div>
            </div>

            {/* Operational heartbeat incidents (distinct from cryptographic gap declarations) */}
            <div className="border-2 border-fg/10 bg-bg p-3 space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.incidents.title')}</span>
                  <span onClick={(e) => e.stopPropagation()}>
                    <ContextHelp
                      title={t('integrity.incidents.title')}
                      content={<Trans i18nKey="audit-logs:help.incidents.content" components={{ strong: <strong /> }} />}
                      ariaLabel={t('common:help.ariaLabel', { title: t('integrity.incidents.title') })}
                    />
                  </span>
                </div>
                <Button type="button" size="sm" variant="secondary" className="gap-1.5" onClick={() => void refetchIncidents()}>
                  {incidentsLoading ? t('integrity.incidents.refreshing') : t('integrity.incidents.refresh')}
                </Button>
              </div>
              {incidentsLoading && (
                <p className="text-xs text-fg-muted">{t('integrity.incidents.loading')}</p>
              )}
              {!incidentsLoading && (incidentsPage?.content?.length ?? 0) === 0 && (
                <p className="text-xs text-fg-muted">{t('integrity.incidents.empty')}</p>
              )}
              {!incidentsLoading && (incidentsPage?.content?.length ?? 0) > 0 && (
                <ul className="space-y-2 pl-0 list-none">
                  {(incidentsPage?.content ?? []).map((row) => (
                    <li key={row.incidentId} className="border-2 border-fg/15 p-2 text-xs space-y-1">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-mono font-bold">#{row.incidentId}</span>
                        <Badge
                          variant={
                            row.status === 'IN_PROGRESS'
                              ? 'warning'
                              : row.status === 'RECOVERED_PENDING_DECLARATION'
                                ? 'warning'
                                : 'default'
                          }
                        >
                          {t(`integrity.incidents.status.${row.status}`)}
                        </Badge>
                      </div>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1 text-fg-muted">
                        {row.anchorCheckpointId != null && (
                          <span>{t('integrity.incidents.anchorCheckpoint', { id: row.anchorCheckpointId })}</span>
                        )}
                        {row.staleSince && (
                          <span>{t('integrity.incidents.staleSince', { ts: formatDateWithTimezone(row.staleSince) })}</span>
                        )}
                        {row.degradedSince && (
                          <span>{t('integrity.incidents.degradedSince', { ts: formatDateWithTimezone(row.degradedSince) })}</span>
                        )}
                        {row.recoveredAt && (
                          <span>{t('integrity.incidents.recoveredAt', { ts: formatDateWithTimezone(row.recoveredAt) })}</span>
                        )}
                      </div>
                      {row.status === 'CLOSED' && row.justification && (
                        <p className="text-xs text-fg pt-1 border-t border-fg/10">{row.justification}</p>
                      )}
                      {row.status === 'RECOVERED_PENDING_DECLARATION' && (
                        <div className="flex justify-end pt-1">
                          <Button
                            type="button"
                            size="sm"
                            variant="secondary"
                            onClick={() => {
                              setIncidentActive(row);
                              setIncidentJustification('');
                              setIncidentRootCause('UNKNOWN');
                              setIncidentDeclareOpen(true);
                            }}
                          >
                            {t('integrity.incidents.declare')}
                          </Button>
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Undeclared gaps for consultation (from last chain verification) */}
            {chainReport && (((chainReport as { undeclaredGaps?: unknown[] }).undeclaredGaps?.length ?? 0) === 0 ? (
              <div className="border-2 border-fg/10 bg-bg p-3">
                <div className="flex items-center gap-2 text-success">
                  <CheckCircle className="size-4" />
                  <span className="text-xs font-bold uppercase tracking-wider">{t('integrity.noUndeclaredGaps')}</span>
                </div>
                <p className="text-xs text-fg-muted mt-1">{t('integrity.noUndeclaredGapsHint')}</p>
              </div>
            ) : (
              <div className="border-2 border-fg/10 bg-bg p-3 space-y-2">
                <button
                  type="button"
                  className="flex items-center gap-2 w-full text-left hover:bg-fg/5 p-1 -m-1 transition-colors"
                  onClick={() => setGapsListExpanded((v) => !v)}
                >
                  <span className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.undeclaredGapsForConsultation')}</span>
                  <Badge variant="warning">{(chainReport as { undeclaredGaps?: unknown[] }).undeclaredGaps?.length ?? 0}</Badge>
                  {gapsListExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
                </button>
                {chainReportRange && (
                  <p className="text-xs text-fg-muted">
                    {t('integrity.fromVerificationPeriod', {
                      from: formatDateOnly(chainReportRange.from),
                      to: formatDateOnly(chainReportRange.to),
                    })}
                  </p>
                )}
                {gapsListExpanded && (
                  <ul className="space-y-2 pl-0 list-none">
                    {((chainReport as { undeclaredGaps?: Array<{ gapStart: string; gapEnd: string; gapMinutes: number }> }).undeclaredGaps ?? []).map((g, idx) => {
                      const isFocused = focusedGap !== null && focusedGap.gapStart === g.gapStart && focusedGap.gapEnd === g.gapEnd;
                      return (
                        <li
                          key={`${g.gapStart}-${g.gapEnd}`}
                          className={cn(
                            'p-2 border-2 transition-colors',
                            isFocused ? 'border-warning bg-warning/10' : 'border-fg/20',
                          )}
                        >
                          <div className="flex items-center justify-between gap-2 flex-wrap">
                            <div className="text-xs">
                              <span className="text-fg-muted">{t('integrity.gapLabel', { n: idx + 1 })}</span>{' '}
                              <span className={isFocused ? 'font-bold' : ''}>
                                {formatDateWithTimezone(g.gapStart)} → {formatDateWithTimezone(g.gapEnd)}
                              </span>
                              <span className="text-fg-muted ml-2">(~{Number(g.gapMinutes).toLocaleString()} min)</span>
                            </div>
                            <div className="flex items-center gap-2">
                              <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                className="text-xs h-7 gap-1.5"
                                onClick={() => (isFocused ? setFocusedGap(null) : focusGapAndNavigate(g))}
                              >
                                <ListOrdered className="size-3.5" />
                                {isFocused
                                  ? t('integrity.gapAction.clearFocus')
                                  : t('integrity.gapAction.locate')}
                              </Button>
                              <Button
                                type="button"
                                size="sm"
                                className="text-xs h-7 gap-1.5"
                                onClick={() => openDeclareDialogFor(g)}
                              >
                                <AlertTriangle className="size-3.5" />
                                {t('integrity.gapAction.declare')}
                              </Button>
                            </div>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            ))}
          </div>

          {/* ── Checkpoint timeline (nested expandable) ── */}
          <div className="space-y-3">
            <button
              type="button"
              className="flex items-center gap-2 w-full text-left hover:bg-fg/5 p-2 -m-2 transition-colors"
              onClick={() => setTimelineExpanded((v) => !v)}
            >
              <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.checkpointTimeline')}</h3>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={t('integrity.checkpointTimeline')} content={<Trans i18nKey="audit-logs:help.checkpointTimeline.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.checkpointTimeline') })} />
              </span>
              {timelineExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
            </button>
            {timelineExpanded && (
              <div className="border-2 border-fg/10 bg-bg p-3 space-y-3">
                <div className="flex gap-3 items-center flex-wrap">
                  <DateRangeFilter value={checkpointRange} onChange={setCheckpointRange} showClear={true} emptyOptionLabel={t('list.dateRangeFull')} />
                  <div className="w-40">
                    <Select value={checkpointTypeFilter} onChange={(e) => setCheckpointTypeFilter(e.target.value)}>
                      <option value="">{t('integrity.allTypes')}</option>
                      <option value="REGULAR">{t('integrity.typeRegular')}</option>
                      <option value="ARCHIVE_SEAL">{t('integrity.typeArchiveSeal')}</option>
                      <option value="GAP_DECLARATION">{t('integrity.typeGapDeclaration')}</option>
                    </Select>
                  </div>
                  <Button size="sm" variant="secondary" onClick={() => refetchCheckpoints()} className="gap-1.5">
                    <ListOrdered className="size-3.5" />
                    {t('list.refresh')}
                  </Button>
                </div>
                {checkpointRange.from && checkpointRange.to && (
                  <p className="text-xs text-fg-muted">
                    {t('integrity.showingCheckpointsFor', {
                      from: formatDateOnly(checkpointRange.from),
                      to: formatDateOnly(checkpointRange.to),
                    })}
                  </p>
                )}
                <div className="flex items-center gap-2">
                  <Info className="size-3.5 text-fg-muted shrink-0" aria-hidden />
                  <p className="text-xs text-fg-muted italic">{t('integrity.timelineHint')}</p>
                </div>
                <Pagination
                  page={checkpointPagination.page}
                  totalPages={checkpointPagination.totalPages}
                  totalElements={checkpointPagination.totalElements}
                  isFirst={checkpointPagination.isFirst}
                  isLast={checkpointPagination.isLast}
                  onFirstPage={checkpointPagination.firstPage}
                  onLastPage={checkpointPagination.lastPage}
                  onPrevPage={checkpointPagination.prevPage}
                  onNextPage={checkpointPagination.nextPage}
                  pageSize={checkpointPagination.size}
                  onPageSizeChange={checkpointPagination.setPageSize}
                  position="top"
                />
                <CheckpointTimelineTable
                  rows={checkpointRowsWithGaps}
                  isLoading={checkpointLoading}
                  currentSort={checkpointPagination.sort}
                  onSort={checkpointPagination.setSort}
                  focusGap={focusedGap}
                  focusCheckpointId={focusCheckpointId}
                />
                <Pagination
                  page={checkpointPagination.page}
                  totalPages={checkpointPagination.totalPages}
                  totalElements={checkpointPagination.totalElements}
                  isFirst={checkpointPagination.isFirst}
                  isLast={checkpointPagination.isLast}
                  onFirstPage={checkpointPagination.firstPage}
                  onLastPage={checkpointPagination.lastPage}
                  onPrevPage={checkpointPagination.prevPage}
                  onNextPage={checkpointPagination.nextPage}
                  pageSize={checkpointPagination.size}
                  onPageSizeChange={checkpointPagination.setPageSize}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Seal Archive Dialog ── */}
      <Dialog open={sealOpen} onClose={() => setSealOpen(false)} title={t('sealDialog.title')} size="lg" dismissible={false}>
        {sealResult ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-success">
              <CheckCircle className="size-5" />
              <span className="font-bold">{t('sealDialog.successTitle')}</span>
            </div>
            <dl className="space-y-1.5 text-sm">
              <InfoPair label={t('sealDialog.resultPeriod')} value={`${sealResult.periodStart ?? '—'} → ${sealResult.periodEnd ?? '—'}`} />
              <InfoPair label={t('sealDialog.resultCheckpointsSealed')} value={String(sealResult.checkpointsSealed ?? 0)} />
              <InfoPair label={t('sealDialog.resultSealHmac')} value={sealResult.sealChainHmac ?? '—'} mono />
              <InfoPair label={t('sealDialog.resultAuditLogId')} value={String(sealResult.auditLogId ?? '—')} />
            </dl>
            <div className="flex justify-end pt-2">
              <Button onClick={() => setSealOpen(false)}>{t('sealDialog.done')}</Button>
            </div>
          </div>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              sealMutation.mutate({
                data: {
                  periodStart: sealPeriodStart ? new Date(sealPeriodStart).toISOString() : undefined,
                  periodEnd: sealPeriodEnd ? new Date(sealPeriodEnd).toISOString() : undefined,
                  checkpointIdFrom: sealCheckpointFrom ? Number(sealCheckpointFrom) : undefined,
                  checkpointIdTo: sealCheckpointTo ? Number(sealCheckpointTo) : undefined,
                  justification: sealJustification.trim(),
                },
              });
            }}
            className="space-y-4"
          >
            <p className="text-xs text-fg-muted">
              {t('sealDialog.intro')}
            </p>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="seal-start" className="text-xs">{t('sealDialog.periodStart')}</Label>
                <Input id="seal-start" type="datetime-local" value={sealPeriodStart} onChange={(e) => setSealPeriodStart(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label htmlFor="seal-end" className="text-xs">{t('sealDialog.periodEnd')}</Label>
                <Input id="seal-end" type="datetime-local" value={sealPeriodEnd} onChange={(e) => setSealPeriodEnd(e.target.value)} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="seal-cp-from" className="text-xs">{t('sealDialog.checkpointIdFrom')}</Label>
                <Input id="seal-cp-from" type="number" placeholder={t('sealDialog.optionalPlaceholder')} value={sealCheckpointFrom} onChange={(e) => setSealCheckpointFrom(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label htmlFor="seal-cp-to" className="text-xs">{t('sealDialog.checkpointIdTo')}</Label>
                <Input id="seal-cp-to" type="number" placeholder={t('sealDialog.optionalPlaceholder')} value={sealCheckpointTo} onChange={(e) => setSealCheckpointTo(e.target.value)} />
              </div>
            </div>
            <ReasonFieldRow
              presetGroup="audit_chain_justification"
              idPrefix="audit-seal"
              inputId="seal-just"
              value={sealJustification}
              onChange={setSealJustification}
              label={
                <>
                  {t('sealDialog.justification')}{' '}
                  <span className="text-fg-muted font-normal">{t('sealDialog.justificationHint')}</span>
                </>
              }
              placeholder={t('sealDialog.justificationPlaceholder')}
              showMinLengthError={
                sealJustification.trim().length > 0 && sealJustification.trim().length < 10
              }
              minLengthErrorTone="justification"
            />
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="secondary" onClick={() => setSealOpen(false)}>{t('sealDialog.cancel')}</Button>
              <Button type="submit" disabled={sealMutation.isPending || sealJustification.trim().length < 10}>
                {sealMutation.isPending ? t('sealDialog.submitting') : t('sealDialog.submit')}
              </Button>
            </div>
          </form>
        )}
      </Dialog>

      {/* ── Gap Declaration Dialog ── */}
      <Dialog open={gapOpen} onClose={closeGapDialog} title={t('gapDialog.title')} size="md" dismissible={false}>
        {gapResult ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-success">
              <CheckCircle className="size-5" />
              <span className="font-bold">{t('gapDialog.successTitle')}</span>
            </div>
            <dl className="space-y-1.5 text-sm">
              <InfoPair label={t('gapDialog.resultGapPeriod')} value={gapResult.gapStart && gapResult.gapEnd ? `${formatDateWithTimezone(gapResult.gapStart)} → ${formatDateWithTimezone(gapResult.gapEnd)}` : '—'} />
              <InfoPair label={t('gapDialog.resultGapCheckpoint')} value={String(gapResult.gapCheckpointId ?? '—')} />
              <InfoPair label={t('gapDialog.resultGapHmac')} value={gapResult.gapChainHmac ?? '—'} mono />
              <InfoPair label={t('gapDialog.resultAuditLogId')} value={String(gapResult.auditLogId ?? '—')} />
            </dl>
            <div className="flex justify-end pt-2">
              <Button onClick={closeGapDialog}>{t('gapDialog.done')}</Button>
            </div>
          </div>
        ) : selectedGapForDeclaration ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!selectedGapForDeclaration) return;
              gapMutation.mutate({
                data: {
                  gapStart: selectedGapForDeclaration.gapStart,
                  gapEnd: selectedGapForDeclaration.gapEnd,
                  justification: gapJustification.trim(),
                },
              });
            }}
            className="space-y-4"
          >
            <p className="text-xs text-fg-muted">{t('gapDialog.intro')}</p>
            <div className="border-2 border-warning/40 bg-warning/5 p-3 space-y-1">
              <p className="text-[10px] uppercase tracking-wider text-fg-muted font-bold">
                {t('gapDialog.detectedPeriod')}
              </p>
              <p className="text-sm font-bold">
                {formatDateWithTimezone(selectedGapForDeclaration.gapStart)} →{' '}
                {formatDateWithTimezone(selectedGapForDeclaration.gapEnd)}
              </p>
              <p className="text-xs text-fg-muted">
                {t('gapDialog.detectedDuration', {
                  minutes: Number(selectedGapForDeclaration.gapMinutes).toLocaleString(),
                })}
              </p>
            </div>
            <ReasonFieldRow
              presetGroup="audit_chain_justification"
              idPrefix="audit-gap"
              inputId="gap-just"
              value={gapJustification}
              onChange={setGapJustification}
              label={
                <>
                  {t('gapDialog.justification')}{' '}
                  <span className="text-fg-muted font-normal">{t('gapDialog.justificationHint')}</span>
                </>
              }
              placeholder={t('gapDialog.justificationPlaceholder')}
              showMinLengthError={
                gapJustification.trim().length > 0 && gapJustification.trim().length < 10
              }
              minLengthErrorTone="justification"
            />
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="secondary" onClick={closeGapDialog}>{t('gapDialog.cancel')}</Button>
              <Button type="submit" disabled={gapMutation.isPending || gapJustification.trim().length < 10}>
                {gapMutation.isPending ? t('gapDialog.submitting') : t('gapDialog.submit')}
              </Button>
            </div>
          </form>
        ) : null}
      </Dialog>

      <Dialog
        open={incidentDeclareOpen}
        onClose={() => {
          setIncidentDeclareOpen(false);
          setIncidentActive(null);
        }}
        title={t('integrity.incidents.declareTitle')}
        size="md"
        dismissible={false}
      >
        {incidentActive ? (
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (!incidentActive) return;
              declareIncidentMutation.mutate({
                incidentId: incidentActive.incidentId,
                justification: incidentJustification.trim(),
                rootCause: incidentRootCause,
              });
            }}
          >
            <p className="text-xs text-fg-muted">{t('integrity.incidents.declareIntro')}</p>
            <div className="space-y-1">
              <Label htmlFor="incident-root-cause" className="text-xs">{t('integrity.incidents.rootCause')}</Label>
              <Select
                id="incident-root-cause"
                value={incidentRootCause}
                onChange={(e) =>
                  setIncidentRootCause(e.target.value as (typeof AUDIT_CHAIN_INCIDENT_ROOT_CAUSES)[number])
                }
              >
                {AUDIT_CHAIN_INCIDENT_ROOT_CAUSES.map((rc) => (
                  <option key={rc} value={rc}>{t(`integrity.incidents.rootCauses.${rc}`)}</option>
                ))}
              </Select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="incident-justification" className="text-xs">{t('integrity.incidents.justification')}</Label>
              <Textarea
                id="incident-justification"
                value={incidentJustification}
                onChange={(e) => setIncidentJustification(e.target.value)}
                rows={4}
                placeholder={t('integrity.incidents.justificationPlaceholder')}
              />
              <p className="text-[10px] text-fg-muted">{t('integrity.incidents.justificationHint')}</p>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  setIncidentDeclareOpen(false);
                  setIncidentActive(null);
                }}
              >
                {t('integrity.incidents.cancel')}
              </Button>
              <Button
                type="submit"
                disabled={
                  declareIncidentMutation.isPending || incidentJustification.trim().length < 10 || incidentJustification.trim().length > 500
                }
              >
                {declareIncidentMutation.isPending ? t('integrity.incidents.submitting') : t('integrity.incidents.submit')}
              </Button>
            </div>
          </form>
        ) : null}
      </Dialog>
    </div>
  );
}

function Stat({ label, value, ok, bad }: { label: string; value?: number; ok?: boolean; bad?: boolean }) {
  const color = bad && value ? 'text-error font-bold' : ok ? 'text-success' : 'text-fg';
  return (
    <div>
      <p className="text-[10px] uppercase tracking-wider text-fg-muted">{label}</p>
      <p className={`font-mono font-bold ${color}`}>{value ?? 0}</p>
    </div>
  );
}

function InfoPair({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex gap-3">
      <dt className="w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted shrink-0">{label}</dt>
      <dd className={`text-sm break-all ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AuditLogsPage() {
  const { t } = useTranslation('audit-logs');
  const { session } = useAuth();
  const { effectiveTimeZoneId } = useDisplayTimezone();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
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
  const highlightAuditLogIds = useMemo(
    () => parseHighlightAuditLogIds(searchParams.get('highlightAuditLogIds')),
    [searchParams],
  );
  const focusCheckpointIdParam = useMemo(() => {
    const raw = searchParams.get('focusCheckpointId');
    if (!raw) {
      return null;
    }
    const parsed = Number.parseInt(raw, 10);
    return Number.isFinite(parsed) ? parsed : null;
  }, [searchParams]);
  const isIntegrityAlertContext = searchParams.get('source') === 'integrity-alert';
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
  const [showAffectedOnly, setShowAffectedOnly] = useState(false);

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
    const integrityFlag = searchParams.get('integrity');
    if (integrityFlag) {
      next.set('integrity', integrityFlag);
    }
    const investigationSource = searchParams.get('source');
    if (investigationSource) {
      next.set('source', investigationSource);
    }
    const highlightParam = searchParams.get('highlightAuditLogIds');
    if (highlightParam) {
      next.set('highlightAuditLogIds', highlightParam);
    }
    const focusCheckpointParam = searchParams.get('focusCheckpointId');
    if (focusCheckpointParam) {
      next.set('focusCheckpointId', focusCheckpointParam);
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

  const clearIntegrityInvestigationContext = useCallback(() => {
    setShowAffectedOnly(false);
    const next = new URLSearchParams(searchParams);
    next.delete('source');
    next.delete('highlightAuditLogIds');
    next.delete('focusCheckpointId');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const violatedEntryIds = integritySession?.violatedEntryIds.length
    ? integritySession.violatedEntryIds
    : highlightAuditLogIds;
  const totalViolationCount = violatedEntryIds.length;
  const violationsOnCurrentPage = data.filter(
    (row) => row.auditLogId != null && violatedEntryIds.includes(row.auditLogId),
  ).length;
  const showOffPageViolationsBanner =
    !isAuditLogContextMode
    && !showAffectedOnly
    && isIntegrityAlertContext
    && totalViolationCount > 0
    && violationsOnCurrentPage < totalViolationCount;

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
          {isIntegrityAlertContext && (
            <div className="mb-3 border-2 border-fg/20 bg-fg/[0.03] px-3 py-2 text-sm flex flex-wrap items-center gap-2">
              <span>{t('integrity.investigation.contextBanner')}</span>
              <button
                type="button"
                className="ml-auto text-xs font-medium text-accent underline hover:text-accent/80"
                onClick={clearIntegrityInvestigationContext}
              >
                {t('integrity.investigation.exitContext')}
              </button>
            </div>
          )}
          {showOffPageViolationsBanner && (
            <div className="mb-3 border-2 border-warning/50 bg-warning/10 px-3 py-2 text-sm flex flex-wrap items-center gap-2">
              <span>
                {t('integrity.investigation.offPageViolations', {
                  offPage: totalViolationCount - violationsOnCurrentPage,
                  total: totalViolationCount,
                })}
              </span>
              <Button type="button" size="sm" variant="secondary" onClick={() => setShowAffectedOnly(true)}>
                {t('integrity.investigation.showAffectedOnly')}
              </Button>
            </div>
          )}
          {showAffectedOnly && integritySession ? (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-bold">{t('integrity.investigation.affectedOnlyTitle')}</span>
                <Button type="button" size="sm" variant="secondary" onClick={() => setShowAffectedOnly(false)}>
                  {t('integrity.investigation.backToList')}
                </Button>
              </div>
              <DataTable
                columns={[
                  {
                    header: t('list.columns.id'),
                    key: 'auditLogId',
                    render: (v) => <span className="font-mono text-xs">#{v.auditLogId}</span>,
                  },
                  {
                    header: t('list.columns.event'),
                    key: 'eventType',
                    render: (v) => <span className="font-mono text-xs">{v.eventType ?? '—'}</span>,
                  },
                  {
                    header: t('integrity.investigation.reasonColumn'),
                    key: 'reason',
                    render: (v) => <span className="text-xs">{v.reason ?? '—'}</span>,
                  },
                  {
                    header: '',
                    key: 'action',
                    render: (v) =>
                      v.auditLogId != null ? (
                        <Link
                          to={`/audit-logs?anchorAuditLogId=${v.auditLogId}&beforeCount=10&afterCount=10&source=audit-log-context`}
                          className="text-xs font-bold text-accent underline"
                        >
                          {t('integrity.investigation.viewAround')}
                        </Link>
                      ) : null,
                  },
                ]}
                data={integritySession.entryViolations}
                isLoading={false}
                keyExtractor={(v, i) => v.auditLogId ?? i}
                emptyMessage={t('integrity.investigation.noAffectedEntries')}
              />
            </div>
          ) : isAuditLogContextMode ? (
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
              rowClassName={(row) => {
                const classes: string[] = [];
                if (row.auditLogId != null && highlightAuditLogIds.includes(row.auditLogId)) {
                  classes.push('!bg-error/10 border-l-4 border-l-error');
                }
                if (matchesFocusedContext(row)) {
                  classes.push('!bg-warning/10 border-l-4 border-l-warning');
                }
                return classes.length > 0 ? classes.join(' ') : undefined;
              }}
            />
          )}
        </div>
      </div>

      {/* Integrity panel — visible only for GLOBAL_ADMIN */}
      {isGlobalAdmin && (
        <IntegrityPanel
          expandFromQuery={searchParams.get('integrity') === '1'}
          focusCheckpointId={focusCheckpointIdParam}
          initialCheckRange={
            listApiDateParams.createdAfter && listApiDateParams.createdBefore
              ? {
                  createdAfter: listApiDateParams.createdAfter,
                  createdBefore: listApiDateParams.createdBefore,
                }
              : null
          }
        />
      )}

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
