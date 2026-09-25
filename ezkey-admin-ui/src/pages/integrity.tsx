import { useState, useMemo, useCallback, useEffect } from 'react';
import { useTranslation, Trans } from 'react-i18next';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { ShieldCheck, Info, ShieldAlert, Archive, AlertTriangle, CheckCircle, XCircle, ChevronDown, ChevronUp, ListOrdered, ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { ContextHelp } from '@/components/ui/context-help';
import { Tooltip } from '@/components/ui/tooltip';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select } from '@/components/ui/select';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import {
  dateRangeToApiParams,
  integrityExclusiveDateRangeToApiParams,
} from '@/lib/date-range-presets';
import { api } from '@/lib/api-client';
import { queryKeys } from '@/lib/query-keys';
import { cn, formatDateOnly, formatDateWithTimezone } from '@/lib/utils';
import {
  loadIntegrityInvestigationSession,
  saveIntegrityInvestigationSession,
  buildInvestigationSession,
  resolveEntryIntegrityReportSummaryState,
  type IntegrityInvestigationSession,
} from '@/lib/integrity-investigation-session';
import {
  INTEGRITY_ATELIER_MODES,
  resolveIntegrityAtelierMode,
  type IntegrityAtelierMode,
} from '@/lib/integrity-atelier-mode';
import {
  isActionableIncidentStatus,
  isChainReportNonGreen,
  shouldAutoOpenRemediateCluster,
  shouldForceTimelineOpen,
  undeclaredGapCountFromReport,
} from '@/lib/integrity-progressive-disclosure';
import { useAuth } from '@/context/use-auth';
import { useDisplayTimezone } from '@/context/use-display-timezone';
import { useToast } from '@/context/use-toast';
import { EntryIntegrityReportBadge } from '@/components/feature/entry-integrity-report-badge';
import { EntryIntegrityViolationLine } from '@/components/feature/entry-integrity-violation-line';
import { IntegrityAsyncJobBanner } from '@/components/feature/integrity-async-job-banner';
import { IntegrityReconcileDialog } from '@/components/feature/integrity-reconcile-dialog';
import { useGetAlert } from '@/generated/admin-api/alerts/alerts';
import {
  getArchiveEligibility,
  getChainCheckpoints,
  getGetIntegrityBootstrapQueryKey,
  getIntegrityBootstrap,
  listLifecycleIncidents,
  useConfirmArchived,
  useDeclareGap,
  useSealArchive,
} from '@/generated/admin-api/audit-logs/audit-logs';
import type {
  AlertResponseDto,
  AuditChainCheckpointResponseDto,
  AuditChainIncidentResponseDto,
  ArchiveConfirmArchivedResult,
  ArchiveEligibilityResult,
  ChainVerificationReport,
  GetChainCheckpointsParams,
  IntegrityBootstrapResponseDto,
  IntegrityReport,
  ArchiveSealResult,
  GapDeclarationResult,
  ListLifecycleIncidentsParams,
  PagedModelAuditChainCheckpointResponseDto,
  PagedModelAuditChainIncidentResponseDto,
  RetroactiveIntegrityValidationRunResponse,
} from '@/generated/admin-api/model';
import {
  getCurrentIntegrityAsyncJob,
  integrityAsyncBusyResumeLine,
  startIntegrityAsyncJob,
  type IntegrityAsyncJobResponse,
} from '@/lib/integrity-async-jobs';

type IntegrityRuntimeProfile = 'base' | 'integrity';

type IntegrityMonitoringTruth = {
  runtimeProfile?: IntegrityRuntimeProfile;
  chainCheckpointsEnabled?: boolean;
  nightlyValidationEnabled?: boolean;
};

function resolveIntegrityMonitoringTruth(
  bootstrap: IntegrityBootstrapResponseDto | undefined,
): IntegrityMonitoringTruth {
  const runtimeProfile = bootstrap?.runtimeProfile;
  const profile: IntegrityRuntimeProfile | undefined =
    runtimeProfile === 'base' || runtimeProfile === 'integrity' ? runtimeProfile : undefined;
  return {
    runtimeProfile: profile,
    chainCheckpointsEnabled: bootstrap?.chainCheckpointsEnabled,
    nightlyValidationEnabled: bootstrap?.nightlyValidationEnabled,
  };
}

/**
 * Inactive badge when either monitoring flag is off (config flags — not derived from profile name).
 * Base naming still requires runtimeProfile === 'base'.
 */
function isScheduledDetectionInactive(truth: IntegrityMonitoringTruth): boolean {
  return (
    truth.chainCheckpointsEnabled === false
    || truth.nightlyValidationEnabled === false
  );
}

/**
 * Present-tense schedule copy is unsafe when either chain or nightly monitoring is off.
 */
function shouldUseMonitoringOffCopy(truth: IntegrityMonitoringTruth): boolean {
  return isScheduledDetectionInactive(truth);
}
const AUDIT_CHAIN_INCIDENT_ROOT_CAUSES = [
  'PLANNED_SYSTEM_UPGRADE',
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
  if (type === 'MANIPULATION_CONCILIATION') {
    return (
      <Tooltip content={t('integrity.helpCheckpointManipulationConciliation')}>
        <Badge variant="default">{t('integrity.checkpointTypeConciliation')}</Badge>
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
                  <td className="px-3 py-2 text-xs text-fg-muted max-w-48 truncate" title={r.notes ?? undefined}>
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
  focusCheckpointId = null,
  investigationSource = null,
  reconcileAction = false,
  initialCheckRange = null,
  reconcileAlertId = null,
  reconcileFailBoundary = null,
  reconcileResumeBoundary = null,
  autoOpenReconcile = false,
  monitoringTruth,
}: {
  focusCheckpointId?: number | null;
  /** Deep-link `source` (e.g. integrity-alert) — investigation context may open the timeline. */
  investigationSource?: string | null;
  /** True when deep-link `action=reconcile` (Remediate cluster; not timeline by itself). */
  reconcileAction?: boolean;
  initialCheckRange?: { createdAfter: string; createdBefore: string } | null;
  /** Open AUDIT_INTEGRITY_RUPTURE alert id for Integrity-atelier reconcile. */
  reconcileAlertId?: number | null;
  /** Boundaries resolved from the alert payload (not from URL window params). */
  reconcileFailBoundary?: string | null;
  reconcileResumeBoundary?: string | null;
  /** When true (deep-link `action=reconcile`), open the reconcile dialog once boundaries are ready. */
  autoOpenReconcile?: boolean;
  /** Server truth for runtime profile + integrity monitoring flags (from Integrity bootstrap). */
  monitoringTruth: IntegrityMonitoringTruth;
}) {
  const { t } = useTranslation('audit-logs');
  const { effectiveTimeZoneId } = useDisplayTimezone();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const monitoringOffCopy = shouldUseMonitoringOffCopy(monitoringTruth);
  const disclosureQuery = useMemo(
    () => ({
      action: reconcileAction || autoOpenReconcile ? 'reconcile' : null,
      source: investigationSource,
      focusCheckpointId,
    }),
    [reconcileAction, autoOpenReconcile, investigationSource, focusCheckpointId],
  );
  const forceTimelineOpen = shouldForceTimelineOpen(disclosureQuery, false);
  const forceRemediateFromQuery = shouldAutoOpenRemediateCluster(disclosureQuery, {
    undeclaredGapCount: 0,
    hasActionableIncident: false,
    awaitingConfirmTranche: false,
    chainNonGreen: false,
  });
  const nightlyValidationEnabled = monitoringTruth.nightlyValidationEnabled === true;
  const [timelineExpanded, setTimelineExpanded] = useState(forceTimelineOpen);
  const [prevForceTimelineOpen, setPrevForceTimelineOpen] = useState(forceTimelineOpen);
  const [maintenanceExpanded, setMaintenanceExpanded] = useState(forceRemediateFromQuery);
  const [incidentsExpanded, setIncidentsExpanded] = useState(forceRemediateFromQuery);
  /** Gaps list: collapsed by default; auto-opens with Remediate when gaps exist. */
  const [gapsListExpanded, setGapsListExpanded] = useState(false);

  // Deep-link props: open during render (no post-paint flash) when the query turns on.
  if (forceTimelineOpen !== prevForceTimelineOpen) {
    setPrevForceTimelineOpen(forceTimelineOpen);
    if (forceTimelineOpen) {
      setTimelineExpanded(true);
    }
  }

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
  const [asyncJob, setAsyncJob] = useState<IntegrityAsyncJobResponse | null>(null);
  const asyncSlotRunning = asyncJob?.status === 'RUNNING';

  // ── Date range for integrity checks (shared DateRangeFilter) ──
  const [checkRange, setCheckRange] = useState({ from: '', to: '' });
  const asyncStartsDisabledReason = asyncSlotRunning
    ? t('integrity.asyncJob.startsDisabledTooltip')
    : !checkRange.from || !checkRange.to
      ? t('integrity.selectDateRangeToRun')
      : undefined;

  useEffect(() => {
    if (!initialCheckRange?.createdAfter || !initialCheckRange.createdBefore) {
      return;
    }
    setCheckRange({
      from: toDateInputValue(initialCheckRange.createdAfter),
      to: toDateInputValue(initialCheckRange.createdBefore),
    });
  }, [initialCheckRange?.createdAfter, initialCheckRange?.createdBefore]);

  // ── Dialogs ──
  const [sealOpen, setSealOpen] = useState(false);
  const [gapOpen, setGapOpen] = useState(false);
  const [confirmArchivedOpen, setConfirmArchivedOpen] = useState(false);
  const [reconcileOpen, setReconcileOpen] = useState(false);
  const [sealResult, setSealResult] = useState<ArchiveSealResult | null>(null);
  const [gapResult, setGapResult] = useState<GapDeclarationResult | null>(null);
  const [confirmArchivedResult, setConfirmArchivedResult] =
    useState<ArchiveConfirmArchivedResult | null>(null);

  // ── Seal archive form state ──
  const [sealPeriodStart, setSealPeriodStart] = useState('');
  const [sealPeriodEnd, setSealPeriodEnd] = useState('');
  const [sealCheckpointFrom, setSealCheckpointFrom] = useState('');
  const [sealCheckpointTo, setSealCheckpointTo] = useState('');
  const [sealJustification, setSealJustification] = useState('');

  // ── Confirm archived form state ──
  const [confirmDigest, setConfirmDigest] = useState('');
  const [confirmArchivedAt, setConfirmArchivedAt] = useState('');

  // ── Gap declaration state (driven by selection from the detected-gaps list) ──
  const [selectedGapForDeclaration, setSelectedGapForDeclaration] = useState<
    { gapStart: string; gapEnd: string; gapMinutes: number } | null
  >(null);
  const [gapJustification, setGapJustification] = useState('');

  // ── Checkpoint timeline (nested expandable) ──
  const [checkpointRange, setCheckpointRange] = useState({ from: '', to: '' });
  const [checkpointTypeFilter, setCheckpointTypeFilter] = useState('');
  const [hideEmptyWindows, setHideEmptyWindows] = useState(false);
  /** Focused gap from "Undeclared gaps for consultation" – highlights bordering checkpoints in timeline */
  const [focusedGap, setFocusedGap] = useState<{ gapStart: string; gapEnd: string; gapMinutes: number } | null>(null);

  const [incidentDeclareOpen, setIncidentDeclareOpen] = useState(false);
  const [incidentActive, setIncidentActive] = useState<AuditChainIncidentResponseDto | null>(null);
  const [incidentJustification, setIncidentJustification] = useState('');
  const [incidentRootCause, setIncidentRootCause] =
    useState<(typeof AUDIT_CHAIN_INCIDENT_ROOT_CAUSES)[number]>('UNKNOWN');

  const {
    data: archiveEligibility,
    isLoading: archiveEligibilityLoading,
  } = useQuery({
    queryKey: ['audit-archive-eligibility'],
    queryFn: () => getArchiveEligibility() as Promise<ArchiveEligibilityResult>,
  });

  const {
    data: incidents,
    pagination: incidentsPagination,
    isLoading: incidentsLoading,
    isError: incidentsError,
    refetch: refetchIncidents,
  } = usePaginatedFromOrval<AuditChainIncidentResponseDto, Record<string, never>>({
    queryKey: [...queryKeys.auditChainIncidents],
    baseParams: {},
    fetchPage: (params) =>
      listLifecycleIncidents(params as ListLifecycleIncidentsParams) as Promise<
        PagedModelAuditChainIncidentResponseDto
      >,
    defaultSize: 20,
    defaultSort: 'createdAt,DESC',
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
      api.post<AuditChainIncidentResponseDto>(
        `/api/v1/audit-logs/lifecycle/incidents/${incidentId}/declare`,
        { justification, rootCause },
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...queryKeys.auditChainIncidents] });
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
        entryCountMin: hideEmptyWindows ? 1 : undefined,
      } as GetChainCheckpointsParams;
    }
    const sharedFilters = {
      checkpointType: checkpointTypeFilter || undefined,
      entryCountMin: hideEmptyWindows ? 1 : undefined,
    };
    if (!checkpointRange.from || !checkpointRange.to) {
      return sharedFilters as GetChainCheckpointsParams;
    }
    const { createdAfter, createdBefore } = dateRangeToApiParams(
      checkpointRange.from,
      checkpointRange.to,
      effectiveTimeZoneId,
    );
    return {
      windowStartAfter: createdAfter,
      windowStartBefore: createdBefore,
      ...sharedFilters,
    } as GetChainCheckpointsParams;
  }, [focusedGap, checkpointRange.from, checkpointRange.to, checkpointTypeFilter, hideEmptyWindows, effectiveTimeZoneId]);

  const {
    data: checkpointData,
    pagination: checkpointPagination,
    isLoading: checkpointLoading,
    refetch: refetchCheckpoints,
  } = usePaginatedFromOrval<AuditChainCheckpointResponseDto, GetChainCheckpointsParams>({
    queryKey: [
      ...queryKeys.auditChainCheckpoints,
      checkpointApiParams.windowStartAfter ?? '',
      checkpointApiParams.windowStartBefore ?? '',
      checkpointApiParams.checkpointType ?? '',
      checkpointApiParams.entryCountMin ?? '',
    ],
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
   * UI-side default; a backend lookback/cap is not shipped yet.
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

  async function runChainCheck(_rangeOverride?: { from: string; to: string }) {
    const range = _rangeOverride ?? checkRange;
    if (!range.from || !range.to) {
      return;
    }
    setChainLoading(true);
    setChainReport(null);
    setChainReportRange(null);
    setFocusedGap(null);
    try {
      const { from: createdAfter, to: createdBefore } = integrityRangeToApiParams(range);
      if (!createdAfter || !createdBefore) {
        return;
      }
      await startIntegrityAsyncJob({
        type: 'VERIFY_CHAIN_RANGE',
        from: createdAfter,
        to: createdBefore,
      });
      setChainReportRange({ from: range.from, to: range.to });
      setAsyncJob(await getCurrentIntegrityAsyncJob());
    } catch (e) {
      const resume = integrityAsyncBusyResumeLine(e);
      if (resume) {
        toast(t('integrity.asyncJob.busyToast', { resume }), 'error');
        setAsyncJob(await getCurrentIntegrityAsyncJob());
      } else {
        toast(getTranslatedApiError(e, t, t('integrity.errorChainCheck')), 'error');
      }
    } finally {
      setChainLoading(false);
    }
  }

  async function runIntegrityCheck() {
    if (!checkRange.from || !checkRange.to) {
      return;
    }
    setIntegrityLoading(true);
    setIntegrityReport(null);
    setIntegrityReportRange(null);
    try {
      const { from: createdAfter, to: createdBefore } = integrityRangeToApiParams(checkRange);
      if (!createdAfter || !createdBefore) {
        return;
      }
      await startIntegrityAsyncJob({
        type: 'VERIFY_ENTRY_HMAC_RANGE',
        from: createdAfter,
        to: createdBefore,
      });
      setIntegrityReportRange({ from: checkRange.from, to: checkRange.to });
      setAsyncJob(await getCurrentIntegrityAsyncJob());
    } catch (e) {
      const resume = integrityAsyncBusyResumeLine(e);
      if (resume) {
        toast(t('integrity.asyncJob.busyToast', { resume }), 'error');
        setAsyncJob(await getCurrentIntegrityAsyncJob());
      } else {
        toast(getTranslatedApiError(e, t, t('integrity.errorIntegrityCheck')), 'error');
      }
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
      await startIntegrityAsyncJob({
        type: 'RUN_VALIDATION',
        from: createdAfter,
        to: createdBefore,
        raiseAlert: true,
      });
      setAsyncJob(await getCurrentIntegrityAsyncJob());
    } catch (e) {
      const resume = integrityAsyncBusyResumeLine(e);
      if (resume) {
        toast(t('integrity.asyncJob.busyToast', { resume }), 'error');
        setAsyncJob(await getCurrentIntegrityAsyncJob());
      } else {
        toast(getTranslatedApiError(e, t, t('integrity.validationRun.error')), 'error');
      }
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

  const confirmArchivedMutation = useConfirmArchived({
    mutation: {
      onSuccess: (data) => {
        const result = data as unknown as ArchiveConfirmArchivedResult;
        setConfirmArchivedResult(result);
        toast(
          t('confirmArchivedDialog.toastSuccess', { count: result.checkpointsExported ?? 0 }),
          'success',
        );
        queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs });
        queryClient.invalidateQueries({ queryKey: queryKeys.auditChainCheckpoints });
        queryClient.invalidateQueries({ queryKey: ['audit-archive-eligibility'] });
      },
      onError: (e) =>
        toast(getTranslatedApiError(e, t, t('confirmArchivedDialog.errorFailed')), 'error'),
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

  function resetConfirmArchivedForm() {
    setConfirmDigest('');
    setConfirmArchivedAt('');
    setConfirmArchivedResult(null);
  }

  function openConfirmArchivedDialog() {
    resetConfirmArchivedForm();
    setConfirmArchivedOpen(true);
  }

  const awaitingConfirmTranche =
    archiveEligibility?.confirmationRequired === true
    && archiveEligibility.checkpointIdFrom != null
    && archiveEligibility.checkpointIdTo != null;

  const undeclaredGapCount = undeclaredGapCountFromReport(
    chainReport as { undeclaredGaps?: unknown[] } | null,
  );
  const hasActionableIncident = incidents.some((row) =>
    isActionableIncidentStatus(row.status),
  );
  const chainNonGreen = isChainReportNonGreen(
    chainReport as {
      intact?: boolean;
      status?: string;
      undeclaredGaps?: unknown[];
      invalidCheckpoints?: number;
    } | null,
  );
  const disclosureState = {
    undeclaredGapCount,
    hasActionableIncident,
    awaitingConfirmTranche,
    chainNonGreen,
  };
  const autoOpenRemediate = shouldAutoOpenRemediateCluster(disclosureQuery, disclosureState);

  const [searchParams, setSearchParams] = useSearchParams();
  const modeParam = searchParams.get('mode');
  const derivedMode = resolveIntegrityAtelierMode({
    modeParam,
    query: disclosureQuery,
    state: disclosureState,
    hasFocusedGap: focusedGap != null,
  });
  const [modeOverride, setModeOverride] = useState<IntegrityAtelierMode | null>(null);
  const activeMode = modeOverride ?? derivedMode;

  function selectAtelierMode(next: IntegrityAtelierMode) {
    setModeOverride(next);
    setSearchParams(
      (prev) => {
        const nextParams = new URLSearchParams(prev);
        nextParams.set('mode', next);
        return nextParams;
      },
      { replace: true },
    );
  }

  // Non-green server/UI state (or deep-link): expand Remediate cluster once signals arrive.
  // Does not force the checkpoint timeline open — that stays query/operator-driven.
  useEffect(() => {
    if (!autoOpenRemediate) {
      return;
    }
    setMaintenanceExpanded(true);
    setIncidentsExpanded(true);
    if (undeclaredGapCount > 0) {
      setGapsListExpanded(true);
    }
  }, [autoOpenRemediate, undeclaredGapCount]);

  // Gap locate: keep timeline open when a gap is focused; switch to Verify (investigation job).
  useEffect(() => {
    if (focusedGap == null) {
      return;
    }
    setTimelineExpanded(true);
    setGapsListExpanded(true);
    setMaintenanceExpanded(true);
    setModeOverride('verify');
    setSearchParams(
      (prev) => {
        const nextParams = new URLSearchParams(prev);
        nextParams.set('mode', 'verify');
        return nextParams;
      },
      { replace: true },
    );
  }, [focusedGap, setSearchParams]);

  const canReconcileOnIntegrity =
    reconcileAlertId != null
    && reconcileAlertId > 0
    && Boolean(reconcileFailBoundary && reconcileResumeBoundary);

  const [autoReconcileConsumed, setAutoReconcileConsumed] = useState(false);
  useEffect(() => {
    if (
      !autoOpenReconcile
      || autoReconcileConsumed
      || !canReconcileOnIntegrity
      || !reconcileFailBoundary
      || !reconcileResumeBoundary
    ) {
      return;
    }
    setReconcileOpen(true);
    setAutoReconcileConsumed(true);
  }, [
    autoOpenReconcile,
    autoReconcileConsumed,
    canReconcileOnIntegrity,
    reconcileFailBoundary,
    reconcileResumeBoundary,
  ]);

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

  // Auto-run chain check on page load: populates the gaps list
  // without requiring the operator to click *Run integrity check* first.
  // UX shortcut only — backend discoverability is owned by AuditChainScheduler.
  useEffect(() => {
    if (chainReport || chainLoading) return;

    const effectiveRange =
      checkRange.from && checkRange.to ? checkRange : defaultGapScanRange();

    if (!checkRange.from || !checkRange.to) {
      setCheckRange(effectiveRange);
    }

    void runChainCheck(effectiveRange);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div id="integrity-lifecycle-panel" className="space-y-6">
      <div className="flex items-center gap-2">
        <ShieldAlert className="size-4 text-fg-muted" />
        <p className="text-sm text-fg-muted">{t('integrity.subtitle')}</p>
        <ContextHelp
          title={t('integrity.pageTitle')}
          content={
            <Trans
              i18nKey={
                monitoringOffCopy
                  ? 'audit-logs:help.integrityLifecycle.contentMonitoringOff'
                  : 'audit-logs:help.integrityLifecycle.content'
              }
              components={{ strong: <strong /> }}
            />
          }
          ariaLabel={t('common:help.ariaLabel', { title: t('integrity.pageTitle') })}
        />
      </div>

      <IntegrityAsyncJobBanner job={asyncJob} onJobChange={setAsyncJob} active />

      <div
        role="tablist"
        aria-label={t('integrity.modes.ariaLabel')}
        className="inline-flex border-2 border-fg"
        data-testid="integrity-mode-tabs"
      >
        {INTEGRITY_ATELIER_MODES.map((mode) => {
          const selected = activeMode === mode;
          return (
            <button
              key={mode}
              type="button"
              role="tab"
              aria-selected={selected}
              data-testid={`integrity-mode-${mode}`}
              className={cn(
                'px-3 py-1.5 text-xs font-bold uppercase tracking-wider transition-colors',
                selected ? 'bg-fg text-bg' : 'bg-surface text-fg hover:bg-fg/5',
              )}
              onClick={() => selectAtelierMode(mode)}
            >
              {t(`integrity.modes.${mode}`)}
            </button>
          );
        })}
      </div>

          {/* ── Observe: lifecycle glance ── */}
          {activeMode === 'observe' && (
          <div className="space-y-3" data-testid="integrity-mode-panel-observe">
            <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.lifecycleOverview')}</h3>
            <div className="border-2 border-fg/10 bg-bg p-3 space-y-3">
              <div className="flex items-center justify-between gap-3 flex-wrap">
                <div>
                  <p className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.lifecyclePolicyTitle')}</p>
                  <p className="text-xs text-fg-muted">
                    {t(
                      monitoringOffCopy
                        ? 'integrity.lifecyclePolicyHintMonitoringOff'
                        : 'integrity.lifecyclePolicyHint',
                    )}
                  </p>
                </div>
                {!monitoringOffCopy && (
                  <Badge variant="muted">{t('integrity.policyDriven')}</Badge>
                )}
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
            <p className="text-xs text-fg-muted">{t('integrity.modes.observeHint')}</p>
          </div>
          )}

          {/* ── Verify: detective verification ── */}
          {activeMode === 'verify' && (
          <div className="space-y-6" data-testid="integrity-mode-panel-verify">
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
                onClick={() => void runChainCheck()}
                disabled={chainLoading || asyncSlotRunning || !checkRange.from || !checkRange.to}
                className="gap-1.5"
                title={asyncStartsDisabledReason}
                data-testid="integrity-verify-chain"
              >
                <ShieldCheck className="size-3.5" />
                {chainLoading || (asyncSlotRunning && asyncJob?.type === 'VERIFY_CHAIN_RANGE')
                  ? t('integrity.checking')
                  : t('integrity.verifyChain')}
              </Button>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => void runIntegrityCheck()}
                disabled={integrityLoading || asyncSlotRunning || !checkRange.from || !checkRange.to}
                className="gap-1.5"
                title={asyncStartsDisabledReason}
                data-testid="integrity-verify-entry-range"
              >
                <ShieldCheck className="size-3.5" />
                {integrityLoading || (asyncSlotRunning && asyncJob?.type === 'VERIFY_ENTRY_HMAC_RANGE')
                  ? t('integrity.checking')
                  : t('integrity.verifyEntry')}
              </Button>
              <div className="ml-auto flex items-center gap-2">
                {!nightlyValidationEnabled && (
                  <span
                    className="text-xs text-fg-muted"
                    data-testid="integrity-run-validation-inactive-hint"
                  >
                    {t('integrity.runValidationInactiveHint')}
                  </span>
                )}
                {nightlyValidationEnabled ? (
                  <Button
                    size="sm"
                    onClick={() => void runRetroactiveValidation()}
                    disabled={
                      validationRunLoading
                      || asyncSlotRunning
                      || !checkRange.from
                      || !checkRange.to
                    }
                    className="gap-1.5"
                    title={asyncStartsDisabledReason}
                    data-testid="integrity-run-validation"
                  >
                    <ShieldAlert className="size-3.5" />
                    {validationRunLoading || (asyncSlotRunning && asyncJob?.type === 'RUN_VALIDATION')
                      ? t('integrity.validationRun.running')
                      : t('integrity.runValidation')}
                  </Button>
                ) : (
                  <Tooltip content={t('integrity.runValidationInactiveTooltip')}>
                    <span className="inline-flex">
                      <Button
                        size="sm"
                        onClick={() => void runRetroactiveValidation()}
                        disabled
                        className="gap-1.5"
                        data-testid="integrity-run-validation"
                      >
                        <ShieldAlert className="size-3.5" />
                        {t('integrity.runValidation')}
                      </Button>
                    </span>
                  </Tooltip>
                )}
              </div>
            </div>
            <p className="text-xs text-fg-muted">
              {nightlyValidationEnabled
                ? t('integrity.verifyVsRunHint')
                : t('integrity.verifyVsRunHintInactive')}
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
                  <EntryIntegrityReportBadge report={integrityReport} />
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
                    <p
                      className={cn(
                        'text-xs font-bold mb-1',
                        resolveEntryIntegrityReportSummaryState(integrityReport) === 'allExplained'
                          ? 'text-warning'
                          : 'text-error',
                      )}
                    >
                      {t('integrity.structuredEntryViolations')}
                    </p>
                    <ul className="text-xs space-y-1">
                      {integrityReport.entryViolations.items.map((v) => (
                        <EntryIntegrityViolationLine key={v.auditLogId} violation={v} />
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

          {/* ── Checkpoint timeline (nested expandable) ── */}
          <div className="space-y-3">
            <div className="flex items-center gap-2 p-2 -m-2">
              <button
                type="button"
                className="flex items-center gap-2 text-left hover:bg-fg/5 transition-colors"
                onClick={() => setTimelineExpanded((v) => !v)}
                aria-expanded={timelineExpanded}
                data-testid="integrity-timeline-toggle"
              >
                <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.checkpointTimeline')}</h3>
                {timelineExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
              </button>
              <ContextHelp title={t('integrity.checkpointTimeline')} content={<Trans i18nKey="audit-logs:help.checkpointTimeline.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.checkpointTimeline') })} />
            </div>
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
                      <option value="MANIPULATION_CONCILIATION">{t('integrity.typeManipulationConciliation')}</option>
                    </Select>
                  </div>
                  <label className="flex items-center gap-2 text-xs cursor-pointer select-none">
                    <input
                      type="checkbox"
                      className="size-4 accent-accent"
                      checked={hideEmptyWindows}
                      onChange={(e) => setHideEmptyWindows(e.target.checked)}
                    />
                    {t('integrity.hideEmptyWindows')}
                  </label>
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

          {/* ── Remediate: maintenance, incidents, gaps ── */}
          {activeMode === 'remediate' && (
          <div className="space-y-3" data-testid="integrity-mode-panel-remediate">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  className="flex items-center gap-2 text-left hover:bg-fg/5 p-1 -m-1 transition-colors"
                  onClick={() => setMaintenanceExpanded((v) => !v)}
                  aria-expanded={maintenanceExpanded}
                  data-testid="integrity-maintenance-toggle"
                >
                  <p className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.exceptionalMaintenance')}</p>
                  {awaitingConfirmTranche && (
                    <Badge variant="warning">{t('integrity.confirmationRequired')}</Badge>
                  )}
                  {maintenanceExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
                </button>
                <span onClick={(e) => e.stopPropagation()}>
                  <ContextHelp
                    title={t('integrity.exceptionalMaintenance')}
                    content={
                      <Trans
                        i18nKey={
                          monitoringOffCopy
                            ? 'audit-logs:help.exceptionalMaintenance.contentMonitoringOff'
                            : 'audit-logs:help.exceptionalMaintenance.content'
                        }
                        components={{ strong: <strong /> }}
                      />
                    }
                    ariaLabel={t('common:help.ariaLabel', { title: t('integrity.exceptionalMaintenance') })}
                  />
                </span>
              </div>
              {maintenanceExpanded && (
              <div className="flex gap-3 flex-wrap items-center" data-testid="integrity-maintenance-body">
              <Button size="sm" variant="secondary" onClick={() => { resetSealForm(); setSealOpen(true); }} className="gap-1.5">
                <Archive className="size-3.5" />
                {t('integrity.sealArchive')}
              </Button>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={t('integrity.sealArchive')} content={<Trans i18nKey="audit-logs:help.sealArchive.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.sealArchive') })} />
              </span>
              {awaitingConfirmTranche && (
                <>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={openConfirmArchivedDialog}
                    className="gap-1.5"
                    title={t('integrity.confirmArchivedHint')}
                  >
                    <CheckCircle className="size-3.5" />
                    {t('integrity.confirmArchived')}
                  </Button>
                  <span onClick={(e) => e.stopPropagation()}>
                    <ContextHelp
                      title={t('integrity.confirmArchived')}
                      content={t('integrity.confirmArchivedHint')}
                      ariaLabel={t('common:help.ariaLabel', { title: t('integrity.confirmArchived') })}
                    />
                  </span>
                </>
              )}
              {canReconcileOnIntegrity && (
                <>
                  <Button
                    size="sm"
                    onClick={() => setReconcileOpen(true)}
                    className="gap-1.5"
                    title={t('integrity.reconcileRuptureHint')}
                  >
                    <ShieldAlert className="size-3.5" />
                    {t('integrity.reconcileRupture')}
                  </Button>
                </>
              )}
              <span className="text-xs text-fg-muted italic">{t('integrity.declareGapHint')}</span>
              <span onClick={(e) => e.stopPropagation()}>
                <ContextHelp title={t('integrity.declareGap')} content={<Trans i18nKey="audit-logs:help.declareGap.content" components={{ strong: <strong /> }} />} ariaLabel={t('common:help.ariaLabel', { title: t('integrity.declareGap') })} />
              </span>
            </div>
              )}
            </div>

            {/* Operational heartbeat incidents (distinct from cryptographic gap declarations) */}
            <div className="border-2 border-fg/10 bg-bg p-3 space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    className="flex items-center gap-2 text-left hover:bg-fg/5 p-1 -m-1 transition-colors"
                    onClick={() => setIncidentsExpanded((v) => !v)}
                    aria-expanded={incidentsExpanded}
                    data-testid="integrity-incidents-toggle"
                  >
                    <span className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('integrity.incidents.title')}</span>
                    {hasActionableIncident && (
                      <Badge variant="warning">{t('integrity.disclosure.attention')}</Badge>
                    )}
                    {incidentsExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
                  </button>
                  <span onClick={(e) => e.stopPropagation()}>
                    <ContextHelp
                      title={t('integrity.incidents.title')}
                      content={<Trans i18nKey="audit-logs:help.incidents.content" components={{ strong: <strong /> }} />}
                      ariaLabel={t('common:help.ariaLabel', { title: t('integrity.incidents.title') })}
                    />
                  </span>
                </div>
                {incidentsExpanded && (
                <Button type="button" size="sm" variant="secondary" className="gap-1.5" onClick={() => void refetchIncidents()}>
                  {incidentsLoading ? t('integrity.incidents.refreshing') : t('integrity.incidents.refresh')}
                </Button>
                )}
              </div>
              {incidentsExpanded && (
              <div data-testid="integrity-incidents-body" className="space-y-3">
              {incidentsLoading && (
                <p className="text-xs text-fg-muted">{t('integrity.incidents.loading')}</p>
              )}
              {incidentsError && !incidentsLoading && (
                <div className="space-y-2" data-testid="integrity-incidents-error">
                  <p className="text-xs text-error">{t('integrity.incidents.loadError')}</p>
                  <Button
                    type="button"
                    size="sm"
                    variant="secondary"
                    onClick={() => void refetchIncidents()}
                  >
                    {t('integrity.incidents.retry')}
                  </Button>
                </div>
              )}
              {!incidentsLoading && !incidentsError && incidents.length === 0 && (
                <p className="text-xs text-fg-muted">{t('integrity.incidents.empty')}</p>
              )}
              {!incidentsLoading && !incidentsError && incidents.length > 0 && (
                <>
                <Pagination
                  page={incidentsPagination.page}
                  totalPages={incidentsPagination.totalPages}
                  totalElements={incidentsPagination.totalElements}
                  isFirst={incidentsPagination.isFirst}
                  isLast={incidentsPagination.isLast}
                  onFirstPage={incidentsPagination.firstPage}
                  onLastPage={incidentsPagination.lastPage}
                  onPrevPage={incidentsPagination.prevPage}
                  onNextPage={incidentsPagination.nextPage}
                  pageSize={incidentsPagination.size}
                  onPageSizeChange={incidentsPagination.setPageSize}
                  position="top"
                />
                <ul className="space-y-2 pl-0 list-none">
                  {incidents.map((row) => (
                    <li
                      key={row.incidentId ?? `${row.createdAt ?? 'incident'}-${row.status ?? 'unknown'}`}
                      className="border-2 border-fg/15 p-2 text-xs space-y-1"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-mono font-bold">#{row.incidentId}</span>
                        {row.status && (
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
                        )}
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
                      {row.status === 'RECOVERED_PENDING_DECLARATION' && row.incidentId != null && (
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
                <Pagination
                  page={incidentsPagination.page}
                  totalPages={incidentsPagination.totalPages}
                  totalElements={incidentsPagination.totalElements}
                  isFirst={incidentsPagination.isFirst}
                  isLast={incidentsPagination.isLast}
                  onFirstPage={incidentsPagination.firstPage}
                  onLastPage={incidentsPagination.lastPage}
                  onPrevPage={incidentsPagination.prevPage}
                  onNextPage={incidentsPagination.nextPage}
                  pageSize={incidentsPagination.size}
                  onPageSizeChange={incidentsPagination.setPageSize}
                />
                </>
              )}
              </div>
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
                  aria-expanded={gapsListExpanded}
                  data-testid="integrity-gaps-toggle"
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

      {/* ── Confirm Archived Dialog ── */}
      <Dialog
        open={confirmArchivedOpen}
        onClose={() => setConfirmArchivedOpen(false)}
        title={t('confirmArchivedDialog.title')}
        size="lg"
        dismissible={false}
      >
        {confirmArchivedResult ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-success">
              <CheckCircle className="size-5" />
              <span className="font-bold">{t('confirmArchivedDialog.successTitle')}</span>
            </div>
            <dl className="space-y-1.5 text-sm">
              <InfoPair
                label={t('confirmArchivedDialog.resultPeriod')}
                value={
                  confirmArchivedResult.periodStart && confirmArchivedResult.periodEnd
                    ? `${confirmArchivedResult.periodStart} → ${confirmArchivedResult.periodEnd}`
                    : '—'
                }
              />
              <InfoPair
                label={t('confirmArchivedDialog.resultCheckpointsExported')}
                value={String(confirmArchivedResult.checkpointsExported ?? 0)}
              />
              <InfoPair
                label={t('confirmArchivedDialog.resultDigest')}
                value={confirmArchivedResult.exportBundleDigest ?? '—'}
                mono
              />
              <InfoPair
                label={t('confirmArchivedDialog.resultExportedAt')}
                value={
                  confirmArchivedResult.exportedAt
                    ? formatDateWithTimezone(confirmArchivedResult.exportedAt)
                    : '—'
                }
              />
              <InfoPair
                label={t('confirmArchivedDialog.resultAuditLogId')}
                value={String(confirmArchivedResult.auditLogId ?? '—')}
              />
            </dl>
            <div className="flex justify-end pt-2">
              <Button onClick={() => setConfirmArchivedOpen(false)}>
                {t('confirmArchivedDialog.done')}
              </Button>
            </div>
          </div>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!archiveEligibility?.checkpointIdFrom || !archiveEligibility.checkpointIdTo) {
                return;
              }
              const digest = confirmDigest.trim();
              if (digest.length < 16) {
                toast(t('confirmArchivedDialog.digestTooShort'), 'error');
                return;
              }
              confirmArchivedMutation.mutate({
                data: {
                  checkpointIdFrom: archiveEligibility.checkpointIdFrom,
                  checkpointIdTo: archiveEligibility.checkpointIdTo,
                  exportBundleDigest: digest,
                  archivedAt: confirmArchivedAt
                    ? new Date(confirmArchivedAt).toISOString()
                    : undefined,
                },
              });
            }}
            className="space-y-4"
          >
            <p className="text-xs text-fg-muted">{t('confirmArchivedDialog.intro')}</p>
            {archiveEligibility && (
              <div className="border-2 border-fg/15 bg-bg p-3 space-y-1 text-xs">
                <p className="text-[10px] uppercase tracking-wider text-fg-muted font-bold">
                  {t('confirmArchivedDialog.trancheHeading')}
                </p>
                <p className="font-bold">
                  {archiveEligibility.oldestSealedWindowStart
                    && archiveEligibility.newestSealedWindowEnd
                    ? t('integrity.lifecycleWindowFromTo', {
                        from: formatDateWithTimezone(archiveEligibility.oldestSealedWindowStart),
                        to: formatDateWithTimezone(archiveEligibility.newestSealedWindowEnd),
                      })
                    : t('integrity.noLifecycleWindow')}
                </p>
                <p className="font-mono text-fg-muted">
                  {archiveEligibility.checkpointIdFrom != null
                    && archiveEligibility.checkpointIdTo != null
                    ? t('integrity.lifecycleCheckpointRange', {
                        from: archiveEligibility.checkpointIdFrom,
                        to: archiveEligibility.checkpointIdTo,
                      })
                    : '—'}
                </p>
              </div>
            )}
            <div className="space-y-1">
              <Label htmlFor="confirm-digest" className="text-xs">
                {t('confirmArchivedDialog.exportBundleDigest')}{' '}
                <span className="text-fg-muted font-normal">
                  {t('confirmArchivedDialog.exportBundleDigestHint')}
                </span>
              </Label>
              <Input
                id="confirm-digest"
                value={confirmDigest}
                onChange={(e) => setConfirmDigest(e.target.value)}
                maxLength={88}
                placeholder={t('confirmArchivedDialog.exportBundleDigestPlaceholder')}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="confirm-archived-at" className="text-xs">
                {t('confirmArchivedDialog.archivedAt')}
              </Label>
              <Input
                id="confirm-archived-at"
                type="datetime-local"
                value={confirmArchivedAt}
                onChange={(e) => setConfirmArchivedAt(e.target.value)}
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="secondary"
                onClick={() => setConfirmArchivedOpen(false)}
              >
                {t('confirmArchivedDialog.cancel')}
              </Button>
              <Button
                type="submit"
                disabled={confirmArchivedMutation.isPending || confirmDigest.trim().length < 16}
              >
                {confirmArchivedMutation.isPending
                  ? t('confirmArchivedDialog.submitting')
                  : t('confirmArchivedDialog.submit')}
              </Button>
            </div>
          </form>
        )}
      </Dialog>

      {canReconcileOnIntegrity
        && reconcileAlertId != null
        && reconcileFailBoundary
        && reconcileResumeBoundary && (
        <IntegrityReconcileDialog
          open={reconcileOpen}
          onClose={() => setReconcileOpen(false)}
          alertId={reconcileAlertId}
          failBoundary={reconcileFailBoundary}
          resumeBoundary={reconcileResumeBoundary}
        />
      )}

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
              if (!incidentActive?.incidentId) return;
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

// ── Integrity page (GLOBAL_ADMIN only) ────────────────────────────────────────

function parseAlertPayload(raw?: string | null): unknown {
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return null;
  }
}

function extractRuptureBoundariesFromAlertPayload(
  payload: unknown,
): { failBoundary: string; resumeBoundary: string } | null {
  if (typeof payload !== 'object' || payload === null) {
    return null;
  }
  const record = payload as Record<string, unknown>;
  const fail =
    (typeof record.failBoundary === 'string' && record.failBoundary)
    || (typeof record.windowStart === 'string' && record.windowStart)
    || null;
  const resume =
    (typeof record.resumeBoundary === 'string' && record.resumeBoundary)
    || (typeof record.windowEnd === 'string' && record.windowEnd)
    || null;
  if (!fail || !resume) {
    return null;
  }
  return { failBoundary: fail, resumeBoundary: resume };
}

export default function IntegrityPage() {
  const { t } = useTranslation('audit-logs');
  const { session } = useAuth();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const [searchParams, setSearchParams] = useSearchParams();

  const { data: integrityBootstrap } = useQuery({
    queryKey: getGetIntegrityBootstrapQueryKey(),
    queryFn: () =>
      getIntegrityBootstrap() as Promise<IntegrityBootstrapResponseDto>,
    enabled: isGlobalAdmin,
  });
  const monitoringTruth = useMemo(
    () => resolveIntegrityMonitoringTruth(integrityBootstrap),
    [integrityBootstrap],
  );
  const showMonitoringInactiveBadge =
    monitoringTruth.runtimeProfile === 'base' || isScheduledDetectionInactive(monitoringTruth);

  const focusCheckpointIdParam = useMemo(() => {
    const raw = searchParams.get('focusCheckpointId');
    if (!raw) {
      return null;
    }
    const parsed = Number.parseInt(raw, 10);
    return Number.isFinite(parsed) ? parsed : null;
  }, [searchParams]);

  const reconcileAlertIdParam = useMemo(() => {
    const raw = searchParams.get('alertId');
    if (!raw) {
      return null;
    }
    const parsed = Number.parseInt(raw, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [searchParams]);

  const autoOpenReconcile = searchParams.get('action') === 'reconcile';

  const { data: reconcileAlert } = useGetAlert<AlertResponseDto>(
    reconcileAlertIdParam ?? 0,
    {
      query: {
        enabled:
          isGlobalAdmin
          && autoOpenReconcile
          && reconcileAlertIdParam != null
          && reconcileAlertIdParam > 0,
      },
    },
  );

  const reconcileBoundaries = useMemo(() => {
    if (!autoOpenReconcile || !reconcileAlertIdParam) {
      return null;
    }
    if (reconcileAlert?.alertType !== 'AUDIT_INTEGRITY_RUPTURE') {
      return null;
    }
    return extractRuptureBoundariesFromAlertPayload(parseAlertPayload(reconcileAlert.payload));
  }, [autoOpenReconcile, reconcileAlertIdParam, reconcileAlert]);

  const initialCheckRange = useMemo(() => {
    const createdAfter = searchParams.get('createdAfter');
    const createdBefore = searchParams.get('createdBefore');
    if (createdAfter && createdBefore) {
      return { createdAfter, createdBefore };
    }
    // Frozen reconcile deep-link has no window params — seed the check range from the alert.
    if (reconcileBoundaries) {
      return {
        createdAfter: reconcileBoundaries.failBoundary,
        createdBefore: reconcileBoundaries.resumeBoundary,
      };
    }
    return null;
  }, [searchParams, reconcileBoundaries]);

  const [integritySession, setIntegritySession] = useState<IntegrityInvestigationSession | null>(
    () => loadIntegrityInvestigationSession(),
  );
  const [showAffectedOnly, setShowAffectedOnly] = useState(false);

  useEffect(() => {
    setIntegritySession(loadIntegrityInvestigationSession());
  }, [searchParams]);

  const isIntegrityAlertContext =
    searchParams.get('source') === 'integrity-alert'
    || (autoOpenReconcile && reconcileAlertIdParam != null);

  const clearIntegrityInvestigationContext = useCallback(() => {
    setShowAffectedOnly(false);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('source');
      next.delete('highlightAuditLogIds');
      next.delete('focusCheckpointId');
      next.delete('alertId');
      next.delete('action');
      next.delete('ruptureId');
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  if (!isGlobalAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <AppShell title={t('integrity.pageTitle')}>
      <div className="space-y-4">
        {showMonitoringInactiveBadge && (
          <div
            className="border-2 border-fg/20 bg-fg/[0.03] px-3 py-2 text-sm flex flex-wrap items-center gap-2"
            data-testid="integrity-monitoring-inactive-badge"
            role="status"
          >
            <Badge variant="muted">
              {monitoringTruth.runtimeProfile === 'base'
                ? t('integrity.monitoringInactiveBadge.base')
                : t('integrity.monitoringInactiveBadge.generic')}
            </Badge>
          </div>
        )}
        {isIntegrityAlertContext && (
          <div className="border-2 border-fg/20 bg-fg/[0.03] px-3 py-2 text-sm flex flex-wrap items-center gap-2">
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
        {showAffectedOnly && integritySession ? (
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-bold">{t('integrity.investigation.affectedOnlyTitle')}</span>
              <Button type="button" size="sm" variant="secondary" onClick={() => setShowAffectedOnly(false)}>
                {t('integrity.investigation.backToPanel')}
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
        ) : (
          <>
            {isIntegrityAlertContext && integritySession && integritySession.entryViolations.length > 0 && (
              <div className="flex flex-wrap items-center gap-2">
                <Button type="button" size="sm" variant="secondary" onClick={() => setShowAffectedOnly(true)}>
                  {t('integrity.investigation.showAffectedOnly')}
                </Button>
              </div>
            )}
            <IntegrityPanel
              focusCheckpointId={focusCheckpointIdParam}
              investigationSource={searchParams.get('source')}
              reconcileAction={autoOpenReconcile}
              initialCheckRange={initialCheckRange}
              reconcileAlertId={autoOpenReconcile ? reconcileAlertIdParam : null}
              reconcileFailBoundary={reconcileBoundaries?.failBoundary ?? null}
              reconcileResumeBoundary={reconcileBoundaries?.resumeBoundary ?? null}
              autoOpenReconcile={autoOpenReconcile}
              monitoringTruth={monitoringTruth}
            />
          </>
        )}
      </div>
    </AppShell>
  );
}
