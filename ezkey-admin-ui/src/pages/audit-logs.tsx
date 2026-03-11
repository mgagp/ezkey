import { useState, useMemo, type ReactNode } from 'react';
import { ShieldCheck, Info, ShieldAlert, Archive, AlertTriangle, CheckCircle, XCircle, ChevronDown, ChevronUp, ListOrdered, ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ContextHelp } from '@/components/ui/context-help';
import { Tooltip } from '@/components/ui/tooltip';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { getApiErrorMessage } from '@/lib/api-client';
import { dateRangeToApiParams } from '@/lib/date-range-presets';
import { AUDIT_CONTEXT_HELP, CHECKPOINT_TYPE_HELP, HMAC_COLUMN_HELP } from '@/lib/help-text';
import { queryKeys } from '@/lib/query-keys';
import { cn, formatDate, formatRelativeTime } from '@/lib/utils';
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

// ── Event type options (from EventType.java enum) ─────────────────────────────

const EVENT_TYPES = [
  ['ADMIN_LOGIN', 'Admin Login'],
  ['ADMIN_LOGOUT', 'Admin Logout'],
  ['ADMIN_PASSWORD_CHANGE', 'Admin Password Change'],
  ['ADMIN_RECOVERY_USE', 'Admin Recovery Use'],
  ['ENROLLMENT_CREATED', 'Enrollment Created'],
  ['ENROLLMENT_DELETED', 'Enrollment Deleted'],
  ['ENROLLMENT_BIND', 'Enrollment Bind'],
  ['ENROLLMENT_VERIFY', 'Enrollment Verify'],
  ['AUTH_ATTEMPT_CREATED', 'Auth Attempt Created'],
  ['AUTH_ATTEMPT_PENDING', 'Auth Attempt Pending'],
  ['AUTH_ATTEMPT_RESPOND', 'Auth Attempt Respond'],
  ['AUTH_ATTEMPT_CANCELLED', 'Auth Attempt Cancelled'],
  ['API_KEY_CREATED', 'API Key Created'],
  ['API_KEY_REVOKED', 'API Key Revoked'],
  ['API_KEY_EXPIRED', 'API Key Expired'],
  ['API_KEY_AUTH_SUCCESS', 'API Key Auth Success'],
  ['API_KEY_AUTH_FAILED', 'API Key Auth Failed'],
  ['API_KEY_IP_BLOCKED', 'API Key IP Blocked'],
  ['SYSTEM_ERROR', 'System Error'],
] as const;

function formatEventType(et: string): string {
  return et.split('_').map((w) => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
}

// ── Event status badge ────────────────────────────────────────────────────────

function EventStatusBadge({ status }: { status: AuditLogResponseDto['eventStatus'] }) {
  if (status === 'SUCCESS') return <Badge variant="success">Success</Badge>;
  if (status === 'FAILURE') return <Badge variant="error">Failure</Badge>;
  return <Badge variant="error">Error</Badge>;
}

// ── Detail dialog ─────────────────────────────────────────────────────────────

function AuditLogDetailDialog({ log, onClose }: { log: AuditLogResponseDto | null; onClose: () => void }) {
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
    <Dialog open={log !== null} onClose={onClose} title={`Log #${log.auditLogId}`} size="lg">
      <dl className="space-y-2.5">
        <InfoRow label="ID"><span className="font-mono">{log.auditLogId}</span></InfoRow>
        <InfoRow label="Event Type">
          <span className="font-mono text-xs bg-fg/5 px-1.5 py-0.5">{log.eventType}</span>
        </InfoRow>
        <InfoRow label="Status"><EventStatusBadge status={log.eventStatus} /></InfoRow>
        {log.apiName && <InfoRow label="API"><Badge variant="muted">{log.apiName.replace('_API', '')}</Badge></InfoRow>}
        {log.adminId && <InfoRow label="Admin ID"><span className="font-mono">#{log.adminId}</span></InfoRow>}
        {log.integrationId && <InfoRow label="Integration"><span className="font-mono">#{log.integrationId}</span></InfoRow>}
        {log.enrollmentId && <InfoRow label="Enrollment"><span className="font-mono">#{log.enrollmentId}</span></InfoRow>}
        {log.authAttemptId && <InfoRow label="Auth Attempt"><span className="font-mono">#{log.authAttemptId}</span></InfoRow>}
        {log.ipAddress && <InfoRow label="IP Address"><span className="font-mono text-xs">{log.ipAddress}</span></InfoRow>}
        {log.userAgent && <InfoRow label="User Agent"><span className="text-xs text-fg-muted">{log.userAgent}</span></InfoRow>}
        {log.eventDetails && (
          <InfoRow label="Details">
            <pre className="text-xs bg-fg/5 p-2 overflow-auto max-h-32 whitespace-pre-wrap">{log.eventDetails}</pre>
          </InfoRow>
        )}
        {log.errorMessage && (
          <InfoRow label="Error">
            <span className="text-xs text-error">{log.errorMessage}</span>
          </InfoRow>
        )}
        <InfoRow label="Created"><span className="text-fg-muted">{formatDate(log.createdAt ?? '')}</span></InfoRow>
        <InfoRow label="HMAC Integrity">
          {log.entryHmac ? (
            <div className="flex items-center gap-1.5">
              <ShieldCheck className="size-3.5 text-success" />
              <span className="text-xs text-success font-bold">Chain intact</span>
            </div>
          ) : (
            <span className="text-xs text-fg-muted">Not available</span>
          )}
        </InfoRow>
        {log.instanceId && (
          <InfoRow label="Instance"><span className="font-mono text-xs text-fg-muted">{log.instanceId}</span></InfoRow>
        )}
      </dl>
      <div className="flex justify-end pt-4">
        <Button onClick={onClose}>Close</Button>
      </div>
    </Dialog>
  );
}

// ── Checkpoint type badge and timeline table ───────────────────────────────────

type CheckpointRowItem =
  | { kind: 'checkpoint'; row: AuditChainCheckpointResponseDto }
  | { kind: 'gap'; gapEnd: string; gapStart: string; durationMin: number };

function CheckpointTypeBadge({ type }: { type?: string }) {
  const TooltipWrap = ({ content, badge }: { content: string; badge: ReactNode }) => (
    <Tooltip content={content}>{badge}</Tooltip>
  );
  if (type === 'REGULAR') return <TooltipWrap content={CHECKPOINT_TYPE_HELP.REGULAR} badge={<Badge variant="muted">Regular</Badge>} />;
  if (type === 'ARCHIVE_SEAL') return <TooltipWrap content={CHECKPOINT_TYPE_HELP.ARCHIVE_SEAL} badge={<Badge variant="success">Sealed</Badge>} />;
  if (type === 'GAP_DECLARATION') return <TooltipWrap content={CHECKPOINT_TYPE_HELP.GAP_DECLARATION} badge={<Badge variant="warning">Gap</Badge>} />;
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
}) {
  const [field = '', dir = ''] = currentSort.split(',');
  const handleSort = (sortKey: string) => {
    if (sortKey === field) onSort(`${sortKey},${dir === 'ASC' ? 'DESC' : 'ASC'}`);
    else onSort(`${sortKey},DESC`);
  };
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
            {sortable('checkpointId', 'ID', 'w-20')}
            {sortable('windowStart', 'Window start')}
            {sortable('windowEnd', 'Window end')}
            {sortable('entryCount', 'Entries', 'w-20')}
            {sortable('checkpointType', 'Type', 'w-24')}
            <th className="px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider">Notes</th>
            <th className="px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider w-40">Actions</th>
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
                No checkpoints for the selected filters. Set a date range and refresh.
              </td>
            </tr>
          ) : (
            rows.map((item, index) => {
              if (item.kind === 'gap') {
                return (
                  <tr key={`gap-${item.gapEnd}-${item.gapStart}`} className="bg-warning/10 border-l-4 border-warning">
                    <td colSpan={cols} className="px-3 py-2 text-sm">
                      <span className="font-bold text-warning">Gap:</span>{' '}
                      {formatDate(item.gapEnd)} → {formatDate(item.gapStart)}
                      {item.durationMin > 0 && (
                        <span className="text-fg-muted ml-2">(~{item.durationMin} min)</span>
                      )}
                    </td>
                  </tr>
                );
              }
              const r = item.row;
              const nextIsGap = rows[index + 1]?.kind === 'gap';
              return (
                <tr key={r.checkpointId ?? index} className="border-b border-fg/10 bg-surface even:bg-bg">
                  <td className="px-3 py-2 font-mono text-xs">{r.checkpointId ?? '—'}</td>
                  <td className="px-3 py-2 text-xs text-fg-muted">{r.windowStart ? formatDate(r.windowStart) : '—'}</td>
                  <td className="px-3 py-2 text-xs text-fg-muted">{r.windowEnd ? formatDate(r.windowEnd) : '—'}</td>
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
                        SEAL from
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-xs p-1 h-auto"
                        onClick={(e) => { e.stopPropagation(); if (r.checkpointId != null) onSetSealTo(r.checkpointId); }}
                      >
                        SEAL to
                      </Button>
                      {nextIsGap && r.checkpointId != null && (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-xs p-1 h-auto text-warning"
                          onClick={(e) => { e.stopPropagation(); onSetAnchor(r.checkpointId!); }}
                        >
                          Use as anchor
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
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(false);

  // ── Check results ──
  const [chainReport, setChainReport] = useState<ChainVerificationReport | null>(null);
  const [integrityReport, setIntegrityReport] = useState<IntegrityReport | null>(null);
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

  const checkpointApiParams = useMemo(() => {
    if (!checkpointRange.from || !checkpointRange.to) return {};
    const { createdAfter, createdBefore } = dateRangeToApiParams(checkpointRange.from, checkpointRange.to);
    return {
      windowStartAfter: createdAfter,
      windowStartBefore: createdBefore,
      checkpointType: checkpointTypeFilter || undefined,
    } as GetChainCheckpointsParams;
  }, [checkpointRange.from, checkpointRange.to, checkpointTypeFilter]);

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
  });

  /** Rows to display: checkpoints plus gap rows between consecutive checkpoints where windowEnd < next.windowStart */
  const checkpointRowsWithGaps = useMemo(() => {
    const sorted = [...checkpointData].sort(
      (a, b) => new Date(a.windowStart ?? 0).getTime() - new Date(b.windowStart ?? 0).getTime(),
    );
    const out: Array<{ kind: 'checkpoint'; row: AuditChainCheckpointResponseDto } | { kind: 'gap'; gapEnd: string; gapStart: string; durationMin: number }> = [];
    for (let i = 0; i < sorted.length; i++) {
      out.push({ kind: 'checkpoint', row: sorted[i] });
      const curr = sorted[i];
      const next = sorted[i + 1];
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
  }, [checkpointData]);

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

  async function runChainCheck() {
    setChainLoading(true);
    setChainReport(null);
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
    } catch (e) {
      toast(getApiErrorMessage(e, 'Chain integrity check failed'), 'error');
    } finally {
      setChainLoading(false);
    }
  }

  async function runIntegrityCheck() {
    setIntegrityLoading(true);
    setIntegrityReport(null);
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
    } catch (e) {
      toast(getApiErrorMessage(e, 'Integrity check failed'), 'error');
    } finally {
      setIntegrityLoading(false);
    }
  }

  const sealMutation = useSealArchive({
    mutation: {
      onSuccess: (data) => {
        const result = data as unknown as ArchiveSealResult;
        setSealResult(result);
        toast(`Archive sealed — ${result.checkpointsSealed} checkpoints`, 'success');
        queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs });
        queryClient.invalidateQueries({ queryKey: queryKeys.auditChainCheckpoints });
      },
      onError: (e) => toast(getApiErrorMessage(e, 'Seal failed'), 'error'),
    },
  });

  const gapMutation = useDeclareGap({
    mutation: {
      onSuccess: (data) => {
        setGapResult(data as unknown as GapDeclarationResult);
        toast('Gap declared successfully', 'success');
        queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs });
        queryClient.invalidateQueries({ queryKey: queryKeys.auditChainCheckpoints });
      },
      onError: (e) => toast(getApiErrorMessage(e, 'Gap declaration failed'), 'error'),
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

  function ReportBadge({ intact }: { intact?: boolean }) {
    if (intact === true) return <Badge variant="success"><CheckCircle className="size-3 mr-1" />Intact</Badge>;
    if (intact === false) return <Badge variant="error"><XCircle className="size-3 mr-1" />Violation</Badge>;
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
          <h2 className="font-black text-sm uppercase tracking-wider">Integrity &amp; Lifecycle</h2>
          <span onClick={(e) => e.stopPropagation()}>
            <ContextHelp title={AUDIT_CONTEXT_HELP.integrityLifecycle.title} content={AUDIT_CONTEXT_HELP.integrityLifecycle.content} ariaLabel="Help: Integrity and Lifecycle" />
          </span>
          <Badge variant="muted">Global Admin</Badge>
        </div>
        {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
      </button>

      {expanded && (
        <div className="border-t-2 border-fg/20 p-4 space-y-6">
          {/* ── Verification section ── */}
          <div className="space-y-3">
            <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">Verification</h3>

            {/* Date range filter (range required for verification) */}
            <DateRangeFilter
              value={checkRange}
              onChange={setCheckRange}
              presetWidth="w-44"
              emptyOptionLabel="Select a range"
            />

            <div className="flex gap-3">
              <Button
                size="sm"
                variant="secondary"
                onClick={runChainCheck}
                disabled={chainLoading || !checkRange.from || !checkRange.to}
                className="gap-1.5"
                title={!checkRange.from || !checkRange.to ? 'Select a date range to run verification' : undefined}
              >
                <ShieldCheck className="size-3.5" />
                {chainLoading ? 'Checking…' : 'Chain Integrity'}
              </Button>
              <Button
                size="sm"
                variant="secondary"
                onClick={runIntegrityCheck}
                disabled={integrityLoading || !checkRange.from || !checkRange.to}
                className="gap-1.5"
                title={!checkRange.from || !checkRange.to ? 'Select a date range to run verification' : undefined}
              >
                <ShieldCheck className="size-3.5" />
                {integrityLoading ? 'Checking…' : 'Entry Integrity'}
              </Button>
            </div>

            {/* Chain report */}
            {chainReport && (
              <div className="border-2 border-fg/10 p-3 space-y-2 bg-bg">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs uppercase tracking-wider">Chain Verification</span>
                  <ReportBadge intact={chainReport.intact} />
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  <Stat label="Total" value={chainReport.totalCheckpoints} />
                  <Stat label="Valid" value={chainReport.validCheckpoints} ok />
                  <Stat label="Invalid" value={chainReport.invalidCheckpoints} bad />
                  <Stat label="Archived" value={chainReport.archivedCheckpoints} />
                </div>
                {chainReport.gapDeclaredCheckpoints != null && chainReport.gapDeclaredCheckpoints > 0 && (
                  <p className="text-xs text-fg-muted">Gap-declared checkpoints: {chainReport.gapDeclaredCheckpoints}</p>
                )}
                {chainReport.violations && chainReport.violations.length > 0 && (
                  <div className="mt-2">
                    <p className="text-xs font-bold text-error mb-1">Violations:</p>
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
                  <span className="font-bold text-xs uppercase tracking-wider">Entry Integrity</span>
                  <ReportBadge intact={integrityReport.intact} />
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  <Stat label="Total" value={integrityReport.totalEntries} />
                  <Stat label="Valid" value={integrityReport.validEntries} ok />
                  <Stat label="Invalid" value={integrityReport.invalidEntries} bad />
                  <Stat label="Unsigned" value={integrityReport.unsignedEntries} />
                </div>
              </div>
            )}
          </div>

          {/* ── Lifecycle section ── */}
          <div className="space-y-3">
            <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">Lifecycle Operations</h3>
            <div className="flex gap-3 flex-wrap items-center">
              <Button size="sm" variant="secondary" onClick={() => { resetSealForm(); setSealOpen(true); }} className="gap-1.5">
                <Archive className="size-3.5" />
                Seal Archive
              </Button>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={AUDIT_CONTEXT_HELP.sealArchive.title} content={AUDIT_CONTEXT_HELP.sealArchive.content} ariaLabel="Help: Seal Archive" />
              </span>
              <Button size="sm" variant="secondary" onClick={() => { resetGapForm(); setGapOpen(true); }} className="gap-1.5">
                <AlertTriangle className="size-3.5" />
                Declare Gap
              </Button>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={AUDIT_CONTEXT_HELP.declareGap.title} content={AUDIT_CONTEXT_HELP.declareGap.content} ariaLabel="Help: Declare Gap" />
              </span>
            </div>
          </div>

          {/* ── Checkpoint timeline (nested expandable) ── */}
          <div className="space-y-3">
            <button
              type="button"
              className="flex items-center gap-2 w-full text-left hover:bg-fg/5 p-2 -m-2 transition-colors"
              onClick={() => setTimelineExpanded((v) => !v)}
            >
              <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">Checkpoint timeline</h3>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={AUDIT_CONTEXT_HELP.checkpointTimeline.title} content={AUDIT_CONTEXT_HELP.checkpointTimeline.content} ariaLabel="Help: Checkpoint timeline" />
              </span>
              {timelineExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
            </button>
            {timelineExpanded && (
              <div className="border-2 border-fg/10 bg-bg p-3 space-y-3">
                <div className="flex gap-3 items-center flex-wrap">
                  <DateRangeFilter value={checkpointRange} onChange={setCheckpointRange} presetWidth="w-44" showClear={true} />
                  <div className="w-40">
                    <Select value={checkpointTypeFilter} onChange={(e) => setCheckpointTypeFilter(e.target.value)}>
                      <option value="">All types</option>
                      <option value="REGULAR">Regular</option>
                      <option value="ARCHIVE_SEAL">Archive seal</option>
                      <option value="GAP_DECLARATION">Gap declaration</option>
                    </Select>
                  </div>
                  <Button size="sm" variant="secondary" onClick={() => refetchCheckpoints()} className="gap-1.5">
                    <ListOrdered className="size-3.5" />
                    Refresh
                  </Button>
                </div>
                {(selectedSealFromId != null || selectedSealToId != null || selectedGapAnchorId != null) && (
                  <div className="flex flex-wrap items-center gap-3 p-2 border-2 border-accent/30 bg-surface">
                    <span className="text-xs font-bold uppercase tracking-wider text-fg-muted">Selection</span>
                    {(selectedSealFromId != null || selectedSealToId != null) && (
                      <span className="text-sm">
                        Seal range: From <span className="font-mono font-bold">#{selectedSealFromId ?? '—'}</span>
                        {' · '}
                        To <span className="font-mono font-bold">#{selectedSealToId ?? '—'}</span>
                        <button type="button" onClick={() => { setSelectedSealFromId(null); setSelectedSealToId(null); }} className="ml-2 text-xs text-fg-muted hover:text-fg underline">Clear</button>
                      </span>
                    )}
                    {selectedSealFromId != null || selectedSealToId != null ? (
                      <Button size="sm" className="gap-1.5" onClick={openSealDialogWithSelection}>
                        <Archive className="size-3.5" />
                        Open Seal Archive
                      </Button>
                    ) : null}
                    {selectedGapAnchorId != null && (
                      <>
                        <span className="text-sm">
                          Anchor: <span className="font-mono font-bold">#{selectedGapAnchorId}</span>
                          <button type="button" onClick={() => setSelectedGapAnchorId(null)} className="ml-2 text-xs text-fg-muted hover:text-fg underline">Clear</button>
                        </span>
                        <Button size="sm" variant="secondary" className="gap-1.5" onClick={openGapDialogWithSelection}>
                          <AlertTriangle className="size-3.5" />
                          Declare Gap
                        </Button>
                      </>
                    )}
                  </div>
                )}
                <CheckpointTimelineTable
                  rows={checkpointRowsWithGaps}
                  isLoading={checkpointLoading}
                  currentSort={checkpointPagination.sort}
                  onSort={checkpointPagination.setSort}
                  onSetSealFrom={(id) => setSelectedSealFromId(id)}
                  onSetSealTo={(id) => setSelectedSealToId(id)}
                  onSetAnchor={(id) => setSelectedGapAnchorId(id)}
                />
                <Pagination
                  page={checkpointPagination.page}
                  totalPages={checkpointPagination.totalPages}
                  totalElements={checkpointPagination.totalElements}
                  isFirst={checkpointPagination.isFirst}
                  isLast={checkpointPagination.isLast}
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
      <Dialog open={sealOpen} onClose={() => setSealOpen(false)} title="Seal Archive" size="lg" dismissible={false}>
        {sealResult ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-success">
              <CheckCircle className="size-5" />
              <span className="font-bold">Archive sealed successfully</span>
            </div>
            <dl className="space-y-1.5 text-sm">
              <InfoPair label="Period" value={`${sealResult.periodStart ?? '—'} → ${sealResult.periodEnd ?? '—'}`} />
              <InfoPair label="Checkpoints sealed" value={String(sealResult.checkpointsSealed ?? 0)} />
              <InfoPair label="Seal HMAC" value={sealResult.sealChainHmac ?? '—'} mono />
              <InfoPair label="Audit log ID" value={String(sealResult.auditLogId ?? '—')} />
            </dl>
            <div className="flex justify-end pt-2">
              <Button onClick={() => setSealOpen(false)}>Done</Button>
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
              Seal an archive period before dropping an audit log partition.
              A pre-flight integrity check is mandatory — the API will reject if any violation is detected.
            </p>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="seal-start" className="text-xs">Period Start</Label>
                <Input id="seal-start" type="datetime-local" value={sealPeriodStart} onChange={(e) => setSealPeriodStart(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label htmlFor="seal-end" className="text-xs">Period End</Label>
                <Input id="seal-end" type="datetime-local" value={sealPeriodEnd} onChange={(e) => setSealPeriodEnd(e.target.value)} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="seal-cp-from" className="text-xs">Checkpoint ID From</Label>
                <Input id="seal-cp-from" type="number" placeholder="optional" value={sealCheckpointFrom} onChange={(e) => setSealCheckpointFrom(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label htmlFor="seal-cp-to" className="text-xs">Checkpoint ID To</Label>
                <Input id="seal-cp-to" type="number" placeholder="optional" value={sealCheckpointTo} onChange={(e) => setSealCheckpointTo(e.target.value)} />
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor="seal-just">Justification <span className="text-fg-muted font-normal">(min 10 chars, required)</span></Label>
              <Input id="seal-just" placeholder="Monthly rotation — backing up to cold storage" value={sealJustification} onChange={(e) => setSealJustification(e.target.value)} maxLength={500} />
              {sealJustification.trim().length > 0 && sealJustification.trim().length < 10 && (
                <p className="text-xs text-error">Justification must be at least 10 characters.</p>
              )}
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="secondary" onClick={() => setSealOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={sealMutation.isPending || sealJustification.trim().length < 10}>
                {sealMutation.isPending ? 'Sealing…' : 'Seal Archive'}
              </Button>
            </div>
          </form>
        )}
      </Dialog>

      {/* ── Gap Declaration Dialog ── */}
      <Dialog open={gapOpen} onClose={() => setGapOpen(false)} title="Declare Gap" size="lg" dismissible={false}>
        {gapResult ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-success">
              <CheckCircle className="size-5" />
              <span className="font-bold">Gap declared successfully</span>
            </div>
            <dl className="space-y-1.5 text-sm">
              <InfoPair label="Gap period" value={`${gapResult.gapStart ?? '—'} → ${gapResult.gapEnd ?? '—'}`} />
              <InfoPair label="Gap checkpoint" value={String(gapResult.gapCheckpointId ?? '—')} />
              <InfoPair label="Gap HMAC" value={gapResult.gapChainHmac ?? '—'} mono />
              <InfoPair label="Audit log ID" value={String(gapResult.auditLogId ?? '—')} />
            </dl>
            <div className="flex justify-end pt-2">
              <Button onClick={() => setGapOpen(false)}>Done</Button>
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
              Declare a gap when the system was offline longer than the scheduler lookback window (default 60 min).
              Must be called before the scheduler creates regular checkpoints for the gap period.
            </p>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="gap-start" className="text-xs">Gap Start</Label>
                <Input id="gap-start" type="datetime-local" value={gapStart} onChange={(e) => setGapStart(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label htmlFor="gap-end" className="text-xs">Gap End</Label>
                <Input id="gap-end" type="datetime-local" value={gapEnd} onChange={(e) => setGapEnd(e.target.value)} />
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor="gap-anchor" className="text-xs">Anchor Checkpoint ID</Label>
              <Input id="gap-anchor" type="number" placeholder="optional — last checkpoint before outage" value={gapAnchorId} onChange={(e) => setGapAnchorId(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="gap-just">Justification <span className="text-fg-muted font-normal">(min 10 chars, required)</span></Label>
              <Input id="gap-just" placeholder="Planned maintenance window — DB migration" value={gapJustification} onChange={(e) => setGapJustification(e.target.value)} maxLength={500} />
              {gapJustification.trim().length > 0 && gapJustification.trim().length < 10 && (
                <p className="text-xs text-error">Justification must be at least 10 characters.</p>
              )}
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="secondary" onClick={() => setGapOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={gapMutation.isPending || gapJustification.trim().length < 10}>
                {gapMutation.isPending ? 'Declaring…' : 'Declare Gap'}
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
    { header: 'ID', key: 'auditLogId', className: 'w-14', sortKey: 'auditLogId', render: (r) => <span className="font-mono text-xs">{r.auditLogId}</span> },
    {
      header: 'Event',
      key: 'eventType',
      sortKey: 'eventType',
      render: (r) => (
        <span className="font-mono text-xs">{formatEventType(r.eventType ?? '')}</span>
      ),
    },
    { header: 'Status', key: 'eventStatus', sortKey: 'eventStatus', render: (r) => <EventStatusBadge status={r.eventStatus} /> },
    {
      header: 'API',
      key: 'apiName',
      render: (r) => r.apiName
        ? <Badge variant="muted">{r.apiName.replace('_API', '')}</Badge>
        : <span className="text-fg-muted">—</span>,
    },
    {
      header: 'Admin',
      key: 'adminId',
      render: (r) => r.adminId ? <span className="font-mono text-xs">#{r.adminId}</span> : <span className="text-fg-muted">—</span>,
    },
    {
      header: 'HMAC',
      headerTooltip: HMAC_COLUMN_HELP,
      key: 'entryHmac',
      render: (r) =>
          r.entryHmac ? (
            <ShieldCheck className="size-3.5 text-success" />
          ) : (
          <span className="text-fg-muted text-xs">—</span>
        ),
    },
    { header: 'Time', key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatRelativeTime(r.createdAt ?? '')}</span> },
    {
      header: '',
      key: 'detail',
      render: (r) => (
        <Tooltip content="View details">
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
    <AppShell title="Audit Logs">
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="w-52">
            <Select value={eventTypeFilter} onChange={(e) => setEventTypeFilter(e.target.value)}>
              <option value="">All Event Types</option>
              {EVENT_TYPES.map(([val, label]) => (
                <option key={val} value={val}>{label}</option>
              ))}
            </Select>
          </div>
          <div className="w-32">
            <Select value={eventStatusFilter} onChange={(e) => setEventStatusFilter(e.target.value)}>
              <option value="">All Status</option>
              <option value="SUCCESS">Success</option>
              <option value="FAILURE">Failure</option>
              <option value="ERROR">Error</option>
            </Select>
          </div>
          <div className="w-36">
            <Select value={apiNameFilter} onChange={(e) => setApiNameFilter(e.target.value)}>
              <option value="">All APIs</option>
              <option value="ADMIN_API">Admin API</option>
              <option value="AUTH_API">Auth API</option>
              <option value="M2M_API">M2M API</option>
            </Select>
          </div>
          <DateRangeFilter value={dateRange} onChange={setDateRange} showClear={true} />
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5 ml-auto">
            <ShieldCheck className="size-3.5" />
            Refresh
          </Button>
        </div>

        <p className="text-xs text-fg-muted italic">
          Read-only · Tenant-scoped · <span className="inline-flex items-center gap-1"><ShieldCheck className="size-3 text-success inline" /> HMAC</span> = tamper-evident chain intact for that entry.
        </p>

        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedLog(row)}
            keyExtractor={(r, i) => r.auditLogId ?? i}
            emptyMessage="No audit log entries found for the selected filters."
            currentSort={pagination.sort}
            onSort={pagination.setSort}
          />
          <Pagination
            page={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            isFirst={pagination.isFirst}
            isLast={pagination.isLast}
            onPrevPage={pagination.prevPage}
            onNextPage={pagination.nextPage}
            pageSize={pagination.size}
            onPageSizeChange={pagination.setPageSize}
          />
        </div>
      </div>

      {/* Integrity panel — visible only for GLOBAL_ADMIN */}
      {isGlobalAdmin && <IntegrityPanel />}

      <AuditLogDetailDialog log={selectedLog} onClose={() => setSelectedLog(null)} />
    </AppShell>
  );
}
