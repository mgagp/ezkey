import { useState, useMemo, type ReactNode } from 'react';
import { useTranslation, Trans } from 'react-i18next';
import { Link } from 'react-router-dom';
import { ShieldCheck, Info, ShieldAlert, Archive, AlertTriangle, CheckCircle, XCircle, ChevronDown, ChevronUp, ListOrdered, ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ContextHelp } from '@/components/ui/context-help';
import { Tooltip } from '@/components/ui/tooltip';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { useExpandableRelatedDetails } from '@/hooks/use-expandable-related-details';
import { getIntegrationName } from '@/hooks/use-integrations';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { getApiErrorMessage } from '@/lib/api-client';
import { dateRangeToApiParams } from '@/lib/date-range-presets';
import { EventStatusBadge } from '@/components/feature/event-status-badge';
import { EVENT_TYPE_KEYS, getAuditEventTypeLabel } from '@/lib/audit-event-type';
import { queryKeys } from '@/lib/query-keys';
import { cn, formatDate, formatDateOnly, formatDateWithTimezone, formatRelativeTime } from '@/lib/utils';
import { useAuth } from '@/context/auth-context';
import { useToast } from '@/context/toast-context';
import {
  checkChainIntegrity,
  checkIntegrity,
  getAuditLogs,
  getChainCheckpoints,
  useDeclareGap,
  useSealArchive,
} from '@/generated/admin-api/audit-logs/audit-logs';
import type {
  AuditChainCheckpointResponseDto,
  AuditLogResponseDto,
  ChainVerificationReport,
  GetAuditLogsParams,
  GetChainCheckpointsParams,
  IntegrityReport,
  ArchiveSealResult,
  GapDeclarationResult,
  PagedModelAuditLogResponseDto,
  PagedModelAuditChainCheckpointResponseDto,
} from '@/generated/admin-api/model';

// ── Detail dialog ─────────────────────────────────────────────────────────────

function AuditLogDetailDialog({ log, onClose }: { log: AuditLogResponseDto | null; onClose: () => void }) {
  const { t } = useTranslation('audit-logs');

  const relatedDetails = useExpandableRelatedDetails({
    adminId: log?.adminId ?? undefined,
    integrationId: log?.integrationId ?? undefined,
    enrollmentId: log?.enrollmentId ?? undefined,
  });

  if (!log) return null;

  function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div className="flex gap-4">
        <dt className="w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">{label}</dt>
        <dd className="text-sm break-all">{children}</dd>
      </div>
    );
  }

  return (
    <Dialog open={log !== null} onClose={onClose} title={t('detail.title', { id: log.auditLogId })} size="lg">
      <div className="space-y-4">
        {relatedDetails.hasAnyFk && (
          <div className="flex justify-end">
            <Button
              variant="secondary"
              size="sm"
              onClick={relatedDetails.expand}
              disabled={relatedDetails.isExpanded && relatedDetails.isLoading}
            >
              {relatedDetails.isExpanded && relatedDetails.isLoading
                ? t('common:buttons.loading')
                : t('common:detail.moreDetails')}
            </Button>
          </div>
        )}
        <dl className="space-y-2.5">
          <InfoRow label={t('detail.labelId')}><span className="font-mono">{log.auditLogId}</span></InfoRow>
          <InfoRow label={t('detail.labelEventType')}>
            <span className="text-sm">{getAuditEventTypeLabel(log.eventType ?? undefined, t)}</span>
            {log.eventType && (
              <span className="ml-2 font-mono text-xs text-fg-muted">({log.eventType})</span>
            )}
          </InfoRow>
          <InfoRow label={t('detail.labelStatus')}><EventStatusBadge status={log.eventStatus} /></InfoRow>
          {log.apiName && <InfoRow label={t('detail.labelApi')}><Badge variant="muted">{log.apiName.replace('_API', '')}</Badge></InfoRow>}
          {log.adminId && <InfoRow label={t('detail.labelAdminId')}><span className="font-mono">#{log.adminId}</span></InfoRow>}
          {relatedDetails.isExpanded && relatedDetails.admin && (
            <InfoRow label={t('common:detail.relatedAdmin')}>
              <span className="font-medium">
                {relatedDetails.admin.username ?? relatedDetails.admin.adminId} (ID {relatedDetails.admin.adminId})
              </span>
            </InfoRow>
          )}
          {log.integrationId && <InfoRow label={t('detail.labelIntegration')}><span className="font-mono">#{log.integrationId}</span></InfoRow>}
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
          {log.enrollmentId && <InfoRow label={t('detail.labelEnrollment')}><span className="font-mono">#{log.enrollmentId}</span></InfoRow>}
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
          {log.authAttemptId && <InfoRow label={t('detail.labelAuthAttempt')}><span className="font-mono">#{log.authAttemptId}</span></InfoRow>}
        {log.ipAddress && <InfoRow label={t('detail.labelIpAddress')}><span className="font-mono text-xs">{log.ipAddress}</span></InfoRow>}
        {log.userAgent && <InfoRow label={t('detail.labelUserAgent')}><span className="text-xs text-fg-muted">{log.userAgent}</span></InfoRow>}
        {log.reason && (
          <InfoRow label={t('detail.labelReason')}>
            <pre className="text-xs bg-fg/5 p-2 overflow-auto max-h-32 whitespace-pre-wrap">{log.reason}</pre>
          </InfoRow>
        )}
        {log.eventDetails && (
          <InfoRow label={t('detail.labelDetails')}>
            <pre className="text-xs bg-fg/5 p-2 overflow-auto max-h-32 whitespace-pre-wrap">{log.eventDetails}</pre>
          </InfoRow>
        )}
        {log.errorMessage && (
          <InfoRow label={t('detail.labelError')}>
            <span className="text-xs text-error">{log.errorMessage}</span>
          </InfoRow>
        )}
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
          {log.instanceId && (
            <InfoRow label={t('detail.labelInstance')}><span className="font-mono text-xs text-fg-muted">{log.instanceId}</span></InfoRow>
          )}
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

function CheckpointTimelineTable({
  rows,
  isLoading,
  currentSort,
  onSort,
  onSetSealFrom,
  onSetSealTo,
  onSetAnchor,
  focusGap,
}: {
  rows: CheckpointRowItem[];
  isLoading: boolean;
  currentSort: string;
  onSort: (s: string) => void;
  /** Set checkpoint as SEAL "from" (selection only; does not open dialog). */
  onSetSealFrom: (id: number) => void;
  /** Set checkpoint as SEAL "to" (selection only; does not open dialog). */
  onSetSealTo: (id: number) => void;
  /** Set checkpoint as Declare Gap anchor (selection only; does not open dialog). */
  onSetAnchor: (id: number) => void;
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
            <th className="px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider">{t('integrity.timelineColNotes')}</th>
            <th className="px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider w-40">{t('integrity.timelineColActions')}</th>
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
              const nextIsGap = rows[index + 1]?.kind === 'gap';
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
                  <td className="px-3 py-2 text-xs text-fg-muted max-w-32 truncate" title={r.notes ?? undefined}>
                    {r.notes ?? '—'}
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-xs p-1 h-auto"
                        onClick={(e) => { e.stopPropagation(); if (r.checkpointId != null) onSetSealFrom(r.checkpointId); }}
                      >
                        {t('integrity.sealFrom')}
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-xs p-1 h-auto"
                        onClick={(e) => { e.stopPropagation(); if (r.checkpointId != null) onSetSealTo(r.checkpointId); }}
                      >
                        {t('integrity.sealTo')}
                      </Button>
                      {nextIsGap && r.checkpointId != null && (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-xs p-1 h-auto text-warning"
                          onClick={(e) => { e.stopPropagation(); onSetAnchor(r.checkpointId!); }}
                        >
                          {t('integrity.useAsAnchor')}
                        </Button>
                      )}
                    </div>
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

  // ── Gap declaration form state ──
  const [gapStart, setGapStart] = useState('');
  const [gapEnd, setGapEnd] = useState('');
  const [gapAnchorId, setGapAnchorId] = useState('');
  const [gapJustification, setGapJustification] = useState('');

  // ── Checkpoint timeline (nested expandable) ──
  const [timelineExpanded, setTimelineExpanded] = useState(false);
  const [checkpointRange, setCheckpointRange] = useState({ from: '', to: '' });
  const [checkpointTypeFilter, setCheckpointTypeFilter] = useState('');
  /** Selected checkpoints from the timeline table (selection only; dialog opens via explicit button). */
  const [selectedSealFromId, setSelectedSealFromId] = useState<number | null>(null);
  const [selectedSealToId, setSelectedSealToId] = useState<number | null>(null);
  const [selectedGapAnchorId, setSelectedGapAnchorId] = useState<number | null>(null);
  /** Focused gap from "Undeclared gaps for consultation" – highlights bordering checkpoints in timeline */
  const [focusedGap, setFocusedGap] = useState<{ gapStart: string; gapEnd: string; gapMinutes: number } | null>(null);
  const [gapsListExpanded, setGapsListExpanded] = useState(true);

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
    const { createdAfter, createdBefore } = dateRangeToApiParams(checkpointRange.from, checkpointRange.to);
    return {
      windowStartAfter: createdAfter,
      windowStartBefore: createdBefore,
      checkpointType: checkpointTypeFilter || undefined,
    } as GetChainCheckpointsParams;
  }, [focusedGap, checkpointRange.from, checkpointRange.to, checkpointTypeFilter]);

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

  /** Opens Seal Archive dialog with current selection (from timeline) or empty form. */
  function openSealDialogWithSelection() {
    setSealCheckpointFrom(selectedSealFromId != null ? String(selectedSealFromId) : '');
    setSealCheckpointTo(selectedSealToId != null ? String(selectedSealToId) : '');
    setSealResult(null);
    setSealOpen(true);
  }

  /** Opens Declare Gap dialog with current anchor selection (from timeline) or empty form. */
  function openGapDialogWithSelection() {
    setGapAnchorId(selectedGapAnchorId != null ? String(selectedGapAnchorId) : '');
    setGapResult(null);
    setGapOpen(true);
  }

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

  async function runChainCheck() {
    setChainLoading(true);
    setChainReport(null);
    setChainReportRange(null);
    setFocusedGap(null);
    try {
      const params =
        checkRange.from && checkRange.to
          ? (() => {
              const { createdAfter, createdBefore } = dateRangeToApiParams(checkRange.from, checkRange.to);
              return { from: createdAfter, to: createdBefore };
            })()
          : { from: undefined as string | undefined, to: undefined as string | undefined };
      const report = await checkChainIntegrity(params) as unknown as ChainVerificationReport;
      setChainReport(report);
      if (checkRange.from && checkRange.to) {
        setChainReportRange({ from: checkRange.from, to: checkRange.to });
      }
    } catch (e) {
      toast(getApiErrorMessage(e, t('integrity.errorChainCheck')), 'error');
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
              const { createdAfter, createdBefore } = dateRangeToApiParams(checkRange.from, checkRange.to);
              return { from: createdAfter, to: createdBefore };
            })()
          : { from: undefined as string | undefined, to: undefined as string | undefined };
      const report = await checkIntegrity(params) as unknown as IntegrityReport;
      setIntegrityReport(report);
      if (checkRange.from && checkRange.to) {
        setIntegrityReportRange({ from: checkRange.from, to: checkRange.to });
      }
    } catch (e) {
      toast(getApiErrorMessage(e, t('integrity.errorIntegrityCheck')), 'error');
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
      },
      onError: (e) => toast(getApiErrorMessage(e, t('integrity.errorSeal')), 'error'),
    },
  });

  const gapMutation = useDeclareGap({
    mutation: {
      onSuccess: (data) => {
        setGapResult(data as unknown as GapDeclarationResult);
        toast(t('integrity.toastGapSuccess'), 'success');
        queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs });
        queryClient.invalidateQueries({ queryKey: queryKeys.auditChainCheckpoints });
      },
      onError: (e) => toast(getApiErrorMessage(e, t('integrity.errorGap')), 'error'),
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
    setGapStart('');
    setGapEnd('');
    setGapAnchorId('');
    setGapJustification('');
    setGapResult(null);
  }

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
                onClick={runChainCheck}
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
            <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.lifecycleOperations')}</h3>
            <div className="flex gap-3 flex-wrap items-center">
              <Button size="sm" variant="secondary" onClick={() => { resetSealForm(); setSealOpen(true); }} className="gap-1.5">
                <Archive className="size-3.5" />
                {t('integrity.sealArchive')}
              </Button>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={t('integrity.sealArchive')} content={<Trans i18nKey="audit-logs:help.sealArchive.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.sealArchive') })} />
              </span>
              <Button size="sm" variant="secondary" onClick={() => { resetGapForm(); setGapOpen(true); }} className="gap-1.5">
                <AlertTriangle className="size-3.5" />
                {t('integrity.declareGap')}
              </Button>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={t('integrity.declareGap')} content={<Trans i18nKey="audit-logs:help.declareGap.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.declareGap') })} />
              </span>
            </div>

            {/* Undeclared gaps for consultation (from last chain verification) */}
            {((chainReport as { undeclaredGaps?: Array<{ gapStart: string; gapEnd: string; gapMinutes: number }> } | null)?.undeclaredGaps?.length ?? 0) > 0 && (
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
                        <li key={`${g.gapStart}-${g.gapEnd}`}>
                          <button
                            type="button"
                            onClick={() => (isFocused ? setFocusedGap(null) : focusGapAndNavigate(g))}
                            className={cn(
                              'w-full text-left text-xs p-2 border-2 transition-colors',
                              isFocused ? 'border-warning bg-warning/10 font-bold' : 'border-fg/20 hover:border-warning/50 hover:bg-warning/5',
                            )}
                          >
                            <span className="text-fg-muted">{t('integrity.gapLabel', { n: idx + 1 })}</span>{' '}
                            {formatDateWithTimezone(g.gapStart)} → {formatDateWithTimezone(g.gapEnd)}
                            <span className="text-fg-muted ml-2">(~{Number(g.gapMinutes).toLocaleString()} min)</span>
                            {isFocused && <span className="ml-2 text-warning font-bold">· {t('integrity.focus')}</span>}
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}
                {focusedGap !== null && (
                  <div className="flex items-center gap-2 flex-wrap text-xs p-2 border-2 border-warning/50 bg-warning/5">
                    <span className="font-bold text-warning">{t('integrity.focus')}:</span>
                    <span>{formatDateWithTimezone(focusedGap.gapStart)} → {formatDateWithTimezone(focusedGap.gapEnd)} (~{Number(focusedGap.gapMinutes).toLocaleString()} min)</span>
                    <Button type="button" variant="ghost" size="sm" className="text-xs h-7" onClick={() => setFocusedGap(null)}>{t('integrity.clearFocus')}</Button>
                  </div>
                )}
              </div>
            )}
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
                {(selectedSealFromId != null || selectedSealToId != null || selectedGapAnchorId != null) && (
                  <div className="flex flex-wrap items-center gap-3 p-2 border-2 border-accent/30 bg-surface">
                    <span className="text-xs font-bold uppercase tracking-wider text-fg-muted">{t('integrity.selection')}</span>
                    {(selectedSealFromId != null || selectedSealToId != null) && (
                      <span className="text-sm">
                        {t('integrity.sealRangeLabel')}: {t('integrity.sealRangeFrom')} <span className="font-mono font-bold">#{selectedSealFromId ?? '—'}</span>
                        {' · '}
                        {t('integrity.sealRangeTo')} <span className="font-mono font-bold">#{selectedSealToId ?? '—'}</span>
                        <button type="button" onClick={() => { setSelectedSealFromId(null); setSelectedSealToId(null); }} className="ml-2 text-xs text-fg-muted hover:text-fg underline">{t('integrity.clear')}</button>
                      </span>
                    )}
                    {selectedSealFromId != null || selectedSealToId != null ? (
                      <Button size="sm" className="gap-1.5" onClick={openSealDialogWithSelection}>
                        <Archive className="size-3.5" />
                        {t('integrity.openSealArchive')}
                      </Button>
                    ) : null}
                    {selectedGapAnchorId != null && (
                      <>
                        <span className="text-sm">
                          {t('integrity.anchor')}: <span className="font-mono font-bold">#{selectedGapAnchorId}</span>
                          <button type="button" onClick={() => setSelectedGapAnchorId(null)} className="ml-2 text-xs text-fg-muted hover:text-fg underline">{t('integrity.clear')}</button>
                        </span>
                        <Button size="sm" variant="secondary" className="gap-1.5" onClick={openGapDialogWithSelection}>
                          <AlertTriangle className="size-3.5" />
                          {t('integrity.declareGap')}
                        </Button>
                      </>
                    )}
                  </div>
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
                  onSetSealFrom={(id) => setSelectedSealFromId(id)}
                  onSetSealTo={(id) => setSelectedSealToId(id)}
                  onSetAnchor={(id) => setSelectedGapAnchorId(id)}
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
                  justification: sealJustification,
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
            <div className="space-y-1">
              <Label htmlFor="seal-just">{t('sealDialog.justification')} <span className="text-fg-muted font-normal">{t('sealDialog.justificationHint')}</span></Label>
              <Input id="seal-just" placeholder={t('sealDialog.justificationPlaceholder')} value={sealJustification} onChange={(e) => setSealJustification(e.target.value)} maxLength={500} />
              {sealJustification.trim().length > 0 && sealJustification.trim().length < 10 && (
                <p className="text-xs text-error">{t('sealDialog.justificationMinError')}</p>
              )}
            </div>
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
      <Dialog open={gapOpen} onClose={() => setGapOpen(false)} title={t('gapDialog.title')} size="lg" dismissible={false}>
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
              <Button onClick={() => setGapOpen(false)}>{t('gapDialog.done')}</Button>
            </div>
          </div>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              gapMutation.mutate({
                data: {
                  gapStart: gapStart ? new Date(gapStart).toISOString() : undefined,
                  gapEnd: gapEnd ? new Date(gapEnd).toISOString() : undefined,
                  anchorCheckpointId: gapAnchorId ? Number(gapAnchorId) : undefined,
                  justification: gapJustification,
                },
              });
            }}
            className="space-y-4"
          >
            <p className="text-xs text-fg-muted">
              {t('gapDialog.intro')}
            </p>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="gap-start" className="text-xs">{t('gapDialog.gapStart')}</Label>
                <Input id="gap-start" type="datetime-local" value={gapStart} onChange={(e) => setGapStart(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label htmlFor="gap-end" className="text-xs">{t('gapDialog.gapEnd')}</Label>
                <Input id="gap-end" type="datetime-local" value={gapEnd} onChange={(e) => setGapEnd(e.target.value)} />
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor="gap-anchor" className="text-xs">{t('gapDialog.anchorCheckpointId')}</Label>
              <Input id="gap-anchor" type="number" placeholder={t('gapDialog.anchorPlaceholder')} value={gapAnchorId} onChange={(e) => setGapAnchorId(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="gap-just">{t('gapDialog.justification')} <span className="text-fg-muted font-normal">{t('gapDialog.justificationHint')}</span></Label>
              <Input id="gap-just" placeholder={t('gapDialog.justificationPlaceholder')} value={gapJustification} onChange={(e) => setGapJustification(e.target.value)} maxLength={500} />
              {gapJustification.trim().length > 0 && gapJustification.trim().length < 10 && (
                <p className="text-xs text-error">{t('gapDialog.justificationMinError')}</p>
              )}
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="secondary" onClick={() => setGapOpen(false)}>{t('gapDialog.cancel')}</Button>
              <Button type="submit" disabled={gapMutation.isPending || gapJustification.trim().length < 10}>
                {gapMutation.isPending ? t('gapDialog.submitting') : t('gapDialog.submit')}
              </Button>
            </div>
          </form>
        )}
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
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const [eventTypeFilter, setEventTypeFilter] = useState('');
  const [eventStatusFilter, setEventStatusFilter] = useState('');
  const [apiNameFilter, setApiNameFilter] = useState('');
  const [dateRange, setDateRange] = useState({ from: '', to: '' });
  const [selectedLog, setSelectedLog] = useState<AuditLogResponseDto | null>(null);

  const listApiDateParams =
    dateRange.from && dateRange.to
      ? dateRangeToApiParams(dateRange.from, dateRange.to)
      : { createdAfter: undefined as string | undefined, createdBefore: undefined as string | undefined };

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<AuditLogResponseDto, {
    eventType?: string;
    eventStatus?: string;
    apiName?: string;
    createdAfter?: string;
    createdBefore?: string;
  }>({
    queryKey: ['audit-logs', eventTypeFilter, eventStatusFilter, apiNameFilter, dateRange.from, dateRange.to],
    baseParams: {
      eventType: eventTypeFilter || undefined,
      eventStatus: eventStatusFilter || undefined,
      apiName: apiNameFilter || undefined,
      createdAfter: listApiDateParams.createdAfter,
      createdBefore: listApiDateParams.createdBefore,
    },
    fetchPage: (params) => getAuditLogs(params as GetAuditLogsParams) as Promise<PagedModelAuditLogResponseDto>,
  });

  const columns: ColumnDef<AuditLogResponseDto>[] = [
    { header: t('list.columns.id'), key: 'auditLogId', className: 'w-14', sortKey: 'auditLogId', render: (r) => <span className="font-mono text-xs">{r.auditLogId}</span> },
    {
      header: t('list.columns.event'),
      key: 'eventType',
      sortKey: 'eventType',
      render: (r) => (
        <span className="font-mono text-xs">
          {getAuditEventTypeLabel(r.eventType ?? undefined, t)}
        </span>
      ),
    },
    { header: t('list.columns.status'), key: 'eventStatus', sortKey: 'eventStatus', render: (r) => <EventStatusBadge status={r.eventStatus} /> },
    {
      header: t('list.columns.api'),
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
            onClick={(e) => { e.stopPropagation(); setSelectedLog(r); }}
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

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="min-w-[15rem] w-64">
            <Select value={eventTypeFilter} onChange={(e) => setEventTypeFilter(e.target.value)}>
              <option value="">{t('list.filterEventTypeAll')}</option>
              {EVENT_TYPE_KEYS.map((val) => (
                <option key={val} value={val}>{t(`eventType.${val}`)}</option>
              ))}
            </Select>
          </div>
          <div className="min-w-[10rem] w-40">
            <Select value={eventStatusFilter} onChange={(e) => setEventStatusFilter(e.target.value)}>
              <option value="">{t('list.filterStatusAll')}</option>
              <option value="SUCCESS">{t('list.filterStatusSuccess')}</option>
              <option value="FAILURE">{t('list.filterStatusFailure')}</option>
              <option value="ERROR">{t('list.filterStatusError')}</option>
            </Select>
          </div>
          <div className="w-36">
            <Select value={apiNameFilter} onChange={(e) => setApiNameFilter(e.target.value)}>
              <option value="">{t('list.filterApiAll')}</option>
              <option value="ADMIN_API">{t('list.filterApiAdmin')}</option>
              <option value="AUTH_API">{t('list.filterApiAuth')}</option>
              <option value="INTEGRATION_API">{t('list.filterApiIntegration')}</option>
            </Select>
          </div>
          <DateRangeFilter value={dateRange} onChange={setDateRange} showClear={true} emptyOptionLabel={t('list.dateRangeFull')} />
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5 ml-auto">
            <ShieldCheck className="size-3.5" />
            {t('list.refresh')}
          </Button>
        </div>

        <p className="text-xs text-fg-muted italic">
          {t('list.hint')}
        </p>

        <div>
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedLog(row)}
            keyExtractor={(r, i) => r.auditLogId ?? i}
            emptyMessage={t('list.emptyMessage')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>
      </div>

      {/* Integrity panel — visible only for GLOBAL_ADMIN */}
      {isGlobalAdmin && <IntegrityPanel />}

      <AuditLogDetailDialog log={selectedLog} onClose={() => setSelectedLog(null)} />
    </AppShell>
  );
}
