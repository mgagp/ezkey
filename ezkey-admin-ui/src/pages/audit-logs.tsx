import { useState, useMemo, useCallback, useEffect, type ReactNode } from 'react';
import { useTranslation, Trans } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { ShieldCheck, Info, ShieldAlert, Archive, AlertTriangle, CheckCircle, XCircle, ChevronDown, ChevronUp, ListOrdered, ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { RelatedDetailsButton } from '@/components/feature/related-details-button';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { ContextHelp } from '@/components/ui/context-help';
import { Tooltip } from '@/components/ui/tooltip';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { useDetailNavigation } from '@/hooks/use-detail-navigation';
import { useExpandableRelatedDetails } from '@/hooks/use-expandable-related-details';
import { DetailDialogHeaderNav } from '@/components/ui/detail-dialog-header-nav';
import { getIntegrationName } from '@/hooks/use-integrations';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { dateRangeToApiParams } from '@/lib/date-range-presets';
import { EventStatusBadge } from '@/components/feature/event-status-badge';
import { AUDIT_EVENT_TYPE_GROUPS, auditEventFilterToApiParams } from '@/lib/audit-event-type-family';
import { getAuditEventTypeLabel } from '@/lib/audit-event-type';
import { queryKeys } from '@/lib/query-keys';
import { cn, formatDate, formatDateOnly, formatDateWithTimezone, formatRelativeTime } from '@/lib/utils';
import { useAuth } from '@/context/auth-context';
import { useDisplayTimezone } from '@/context/display-timezone-context';
import { useToast } from '@/context/toast-context';
import {
  checkChainIntegrity,
  checkIntegrity,
  getArchiveEligibility,
  getAuditLogContext,
  getAuditLogs,
  getChainCheckpoints,
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
} from '@/generated/admin-api/model';

type AuditLogQueryParams = GetAuditLogsParams & {
  authAttemptId?: number;
  integrationId?: number;
};
type AuditEventStatusFilter = NonNullable<GetAuditLogsParams['eventStatus']> | '';
type AuditApiNameFilter = NonNullable<GetAuditLogsParams['apiName']> | '';
type ContextEntityType = 'enrollment' | 'authAttempt' | 'integration' | '';

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
}: {
  log: AuditLogResponseDto | null;
  onClose: () => void;
  onPrev: () => void;
  onNext: () => void;
  hasPrev: boolean;
  hasNext: boolean;
  showNav: boolean;
  showEndOfPageHint: boolean;
}) {
  const { t } = useTranslation('audit-logs');
  const { t: tc } = useTranslation('common');

  useDetailNavigation(log !== null && showNav, {
    hasPrev: hasPrev && showNav,
    hasNext: hasNext && showNav,
    onPrev,
    onNext,
  });

  const relatedDetails = useExpandableRelatedDetails({
    adminId: log?.adminId ?? undefined,
    integrationId: log?.integrationId ?? undefined,
    enrollmentId: log?.enrollmentId ?? undefined,
  });

  if (!log) return null;

  function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div className="flex gap-4 min-w-0">
        <dt className="w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">{label}</dt>
        <dd className="min-w-0 flex-1 text-sm break-all">{children}</dd>
      </div>
    );
  }

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
        <div className="flex justify-end items-start gap-2 min-h-[2.25rem]">
          {relatedDetails.hasAnyFk ? (
            <RelatedDetailsButton
              onClick={relatedDetails.expand}
              isExpanded={relatedDetails.isExpanded}
              isLoading={relatedDetails.isLoading}
            />
          ) : (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="invisible pointer-events-none"
              tabIndex={-1}
              aria-hidden
              disabled
            >
              {t('common:detail.moreDetails')}
            </Button>
          )}
        </div>
        <dl className="space-y-2.5">
          <InfoRow label={t('detail.labelId')}><span className="font-mono">{log.auditLogId}</span></InfoRow>
          <InfoRow label={t('detail.labelEventType')}>
            <span className="text-sm">{getAuditEventTypeLabel(log.eventType ?? undefined, t)}</span>
            {log.eventType && (
              <span className="ml-2 font-mono text-xs text-fg-muted">({log.eventType})</span>
            )}
          </InfoRow>
          <InfoRow label={t('detail.labelStatus')}><EventStatusBadge status={log.eventStatus} /></InfoRow>
          <InfoRow label={t('detail.labelApi')}>
            {log.apiName ? (
              <Badge variant="muted">{log.apiName.replace('_API', '')}</Badge>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </InfoRow>
          <InfoRow label={t('detail.labelAdminId')}>
            {log.adminId != null ? (
              <span className="font-mono">#{log.adminId}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </InfoRow>
          {relatedDetails.isExpanded && relatedDetails.admin && (
            <InfoRow label={t('common:detail.relatedAdmin')}>
              <span className="font-medium">
                {relatedDetails.admin.username ?? relatedDetails.admin.adminId} (ID {relatedDetails.admin.adminId})
              </span>
            </InfoRow>
          )}
          <InfoRow label={t('detail.labelIntegration')}>
            {log.integrationId != null ? (
              <span className="font-mono">#{log.integrationId}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </InfoRow>
          {relatedDetails.isExpanded && relatedDetails.integration && (
            <InfoRow label={t('common:detail.relatedIntegration')}>
              <Link
                to={`/integrations/${relatedDetails.integration.id}`}
                className="font-medium text-accent hover:underline"
              >
                {getIntegrationName(relatedDetails.integration)} (ID {relatedDetails.integration.id})
              </Link>
            </InfoRow>
          )}
          <InfoRow label={t('detail.labelEnrollment')}>
            {log.enrollmentId != null ? (
              <span className="font-mono">#{log.enrollmentId}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </InfoRow>
          {relatedDetails.isExpanded && relatedDetails.enrollment && (
            <InfoRow label={t('common:detail.relatedEnrollment')}>
              <Link
                to={`/enrollments/${relatedDetails.enrollment.enrollmentId}`}
                className="font-medium text-accent hover:underline"
              >
                {relatedDetails.enrollment.enrollmentName ?? relatedDetails.enrollment.enrollmentId} (ID {relatedDetails.enrollment.enrollmentId})
              </Link>
            </InfoRow>
          )}
          <InfoRow label={t('detail.labelAuthAttempt')}>
            {log.authAttemptId != null ? (
              <span className="font-mono">#{log.authAttemptId}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </InfoRow>
          <InfoRow label={t('detail.labelIpAddress')}>
            {log.ipAddress ? (
              <span className="font-mono text-xs">{log.ipAddress}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </InfoRow>
          <InfoRow label={t('detail.labelUserAgent')}>
            <pre className="font-mono text-xs leading-snug text-fg-muted bg-fg/5 p-2 border border-fg/10 whitespace-pre-wrap break-words min-h-[3.25rem] max-w-full">
              {log.userAgent?.trim() ? log.userAgent : '—'}
            </pre>
          </InfoRow>
          <InfoRow label={t('detail.labelReason')}>
            <pre className="text-xs bg-fg/5 p-2 overflow-auto max-h-32 whitespace-pre-wrap border border-fg/10">
              {log.reason?.trim() ? log.reason : '—'}
            </pre>
          </InfoRow>
          <InfoRow label={t('detail.labelDetails')}>
            <pre className="text-xs leading-normal bg-fg/5 p-2 overflow-auto max-h-32 min-h-[5.5rem] whitespace-pre-wrap border border-fg/10">
              {log.eventDetails?.trim() ? log.eventDetails : '—'}
            </pre>
          </InfoRow>
          <InfoRow label={t('detail.labelError')}>
            <div className="text-xs bg-fg/5 p-2 overflow-auto max-h-32 border border-fg/10">
              {log.errorMessage ? (
                <span className="text-error">{log.errorMessage}</span>
              ) : (
                <span className="text-fg-muted">—</span>
              )}
            </div>
          </InfoRow>
          <InfoRow label={t('detail.labelCreated')}><span className="text-fg-muted">{formatDate(log.createdAt ?? '')}</span></InfoRow>
          <InfoRow label={t('detail.labelHmacIntegrity')}>
            {log.entryHmac ? (
              <div className="flex items-center gap-1.5">
                <ShieldCheck className="size-3.5 text-success" />
                <span className="text-xs text-success font-bold">{t('detail.chainIntact')}</span>
              </div>
            ) : (
              <span className="text-xs text-fg-muted">{t('detail.notAvailable')}</span>
            )}
          </InfoRow>
          <InfoRow label={t('detail.labelInstance')}>
            {log.instanceId ? (
              <span className="font-mono text-xs text-fg-muted">{log.instanceId}</span>
            ) : (
              <span className="text-fg-muted">—</span>
            )}
          </InfoRow>
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
  const TooltipWrap = ({ content, badge }: { content: string; badge: ReactNode }) => (
    <Tooltip content={content}>{badge}</Tooltip>
  );
  if (type === 'REGULAR') return <TooltipWrap content={t('integrity.helpCheckpointRegular')} badge={<Badge variant="muted">{t('integrity.checkpointTypeRegular')}</Badge>} />;
  if (type === 'ARCHIVE_SEAL') return <TooltipWrap content={t('integrity.helpCheckpointArchiveSeal')} badge={<Badge variant="success">{t('integrity.checkpointTypeSealed')}</Badge>} />;
  if (type === 'GAP_DECLARATION') return <TooltipWrap content={t('integrity.helpCheckpointGapDeclaration')} badge={<Badge variant="warning">{t('integrity.checkpointTypeGap')}</Badge>} />;
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
}: {
  rows: CheckpointRowItem[];
  isLoading: boolean;
  currentSort: string;
  onSort: (s: string) => void;
  /** When set, highlight checkpoint rows immediately before/after this gap (bordering rows). */
  focusGap?: { gapStart: string; gapEnd: string } | null;
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
              return (
                <tr
                  key={r.checkpointId ?? index}
                  className={cn(
                    'border-b border-fg/10 bg-surface even:bg-bg',
                    isBorderingGap && '!bg-warning/25 border-2 border-warning shadow-brutal',
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

function IntegrityPanel() {
  const { t } = useTranslation('audit-logs');
  const { effectiveTimeZoneId } = useDisplayTimezone();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(false);

  // ── Check results ──
  const [chainReport, setChainReport] = useState<ChainVerificationReport | null>(null);
  const [integrityReport, setIntegrityReport] = useState<IntegrityReport | null>(null);
  const [chainReportRange, setChainReportRange] = useState<{ from: string; to: string } | null>(null);
  const [integrityReportRange, setIntegrityReportRange] = useState<{ from: string; to: string } | null>(null);
  const [chainLoading, setChainLoading] = useState(false);
  const [integrityLoading, setIntegrityLoading] = useState(false);

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

  const {
    data: archiveEligibility,
    isLoading: archiveEligibilityLoading,
  } = useQuery({
    queryKey: ['audit-archive-eligibility'],
    queryFn: () => getArchiveEligibility() as Promise<ArchiveEligibilityResult>,
    enabled: expanded,
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

  async function runChainCheck(rangeOverride?: { from: string; to: string }) {
    const range = rangeOverride ?? checkRange;
    setChainLoading(true);
    setChainReport(null);
    setChainReportRange(null);
    setFocusedGap(null);
    try {
      const params =
        range.from && range.to
          ? (() => {
              const { createdAfter, createdBefore } = dateRangeToApiParams(
                range.from,
                range.to,
                effectiveTimeZoneId,
              );
              return { from: createdAfter, to: createdBefore };
            })()
          : { from: undefined as string | undefined, to: undefined as string | undefined };
      const report = await checkChainIntegrity(params) as unknown as ChainVerificationReport;
      setChainReport(report);
      if (range.from && range.to) {
        setChainReportRange({ from: range.from, to: range.to });
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
          ? (() => {
              const { createdAfter, createdBefore } = dateRangeToApiParams(
                checkRange.from,
                checkRange.to,
                effectiveTimeZoneId,
              );
              return { from: createdAfter, to: createdBefore };
            })()
          : { from: undefined as string | undefined, to: undefined as string | undefined };
      const report = await checkIntegrity(params) as unknown as IntegrityReport;
      setIntegrityReport(report);
      if (checkRange.from && checkRange.to) {
        setIntegrityReportRange({ from: checkRange.from, to: checkRange.to });
      }
    } catch (e) {
      toast(getTranslatedApiError(e, t, t('integrity.errorIntegrityCheck')), 'error');
    } finally {
      setIntegrityLoading(false);
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
    const fallback = defaultGapScanRange();
    if (!checkRange.from || !checkRange.to) {
      setCheckRange(fallback);
    }
    void runChainCheck(checkRange.from && checkRange.to ? checkRange : fallback);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expanded]);

  function ReportBadge({ intact, status }: { intact?: boolean; status?: string }) {
    if (intact === true) return <Badge variant="success"><CheckCircle className="size-3 mr-1" />{t('integrity.reportIntact')}</Badge>;
    if (status === 'UNDECLARED_GAP_DETECTED') return <Badge variant="warning"><AlertTriangle className="size-3 mr-1" />{t('integrity.reportUndeclaredGaps')}</Badge>;
    if (intact === false) return <Badge variant="error"><XCircle className="size-3 mr-1" />{t('integrity.reportViolation')}</Badge>;
    return null;
  }

  return (
    <div className="border-2 border-fg/20 bg-main shadow-brutal">
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

            <div className="flex gap-3">
              <Button
                size="sm"
                variant="secondary"
                onClick={() => runChainCheck()}
                disabled={chainLoading || !checkRange.from || !checkRange.to}
                className="gap-1.5"
                title={!checkRange.from || !checkRange.to ? t('integrity.selectDateRangeToRun') : undefined}
              >
                <ShieldCheck className="size-3.5" />
                {chainLoading ? t('integrity.checking') : t('integrity.chainIntegrity')}
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
                {integrityLoading ? t('integrity.checking') : t('integrity.entryIntegrity')}
              </Button>
            </div>

            {/* Chain report */}
            {chainReport && (
              <div className="border-2 border-fg/10 p-3 space-y-2 bg-bg">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs uppercase tracking-wider">{t('integrity.chainVerification')}</span>
                  <ReportBadge intact={chainReport.intact} status={(chainReport as { status?: string }).status} />
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
              </div>
            )}

            {/* Entry integrity report */}
            {integrityReport && (
              <div className="border-2 border-fg/10 p-3 space-y-2 bg-bg">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs uppercase tracking-wider">{t('integrity.entryIntegrityReport')}</span>
                  <ReportBadge intact={integrityReport.intact} />
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

  useEffect(() => {
    if (selectedIndex !== null && (selectedIndex >= activeData.length || activeData.length === 0)) {
      setSelectedIndex(null);
    }
  }, [selectedIndex, activeData.length]);

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
      render: (r) => r.adminId ? <span className="font-mono text-xs">#{r.adminId}</span> : <span className="text-fg-muted">—</span>,
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
      render: (r) =>
          r.entryHmac ? (
            <ShieldCheck className="size-3.5 text-success" />
          ) : (
          <span className="text-fg-muted text-xs">—</span>
        ),
    },
    { header: t('list.columns.time'), key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatRelativeTime(r.createdAt ?? '')}</span> },
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
                matchesFocusedContext(row)
                  ? '!bg-warning/10 border-l-4 border-l-warning'
                  : undefined}
            />
          )}
        </div>
      </div>

      {/* Integrity panel — visible only for GLOBAL_ADMIN */}
      {isGlobalAdmin && <IntegrityPanel />}

      <AuditLogDetailDialog
        log={selectedLog}
        onClose={() => setSelectedIndex(null)}
        onPrev={goPrevLog}
        onNext={goNextLog}
        hasPrev={hasPrev}
        hasNext={hasNext}
        showNav={showRowNav}
        showEndOfPageHint={showEndOfPageHint}
      />
    </AppShell>
  );
}
