import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { CheckCircle, ExternalLink, Loader2 } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/app-shell';
import { Alert as UiAlert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { EntryHmacBadge } from '@/components/feature/entry-hmac-badge';
import { useToast } from '@/context/use-toast';
import { getGetAlertQueryKey, useGetAlert } from '@/generated/admin-api/alerts/alerts';
import {
  checkChainIntegrity,
  checkIntegrity,
  useReconcileIntegrityRupture,
} from '@/generated/admin-api/audit-logs/audit-logs';
import type {
  AlertResponseDto,
  AlertResponseDtoSeverity,
  AlertResponseDtoStatus,
  ChainIntegrityViolation,
  ChainVerificationReport,
  EntryIntegrityViolation,
  IntegrityReport,
  IntegrityRuptureReconciliationRequestCategory,
  IntegrityRuptureReconciliationResult,
} from '@/generated/admin-api/model';
import { IntegrityRuptureReconciliationRequestCategory as ReconcileCategory } from '@/generated/admin-api/model';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import {
  buildInvestigationSession,
  type CappedViolationListPayload,
  entryViolationDisplayState,
  formatHighlightAuditLogIds,
  isEntryReconcileAckRequired,
  listReconcileRequiredEntryViolations,
  saveIntegrityInvestigationSession,
  clearIntegrityInvestigationSession,
} from '@/lib/integrity-investigation-session';
import { formatDate } from '@/lib/utils';

function InfoPair({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="flex gap-2 text-sm">
      <span className="text-fg-muted shrink-0">{label}:</span>
      <span className={mono ? 'font-mono text-xs break-all' : ''}>{value}</span>
    </div>
  );
}

function EntryViolationContent({ violation }: { violation: EntryIntegrityViolation }) {
  const { t } = useTranslation(['alerts']);
  const displayState = entryViolationDisplayState(violation);
  const status = violation.conciliationStatus ?? 'NONE';
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs">
      <EntryHmacBadge state={displayState} />
      <span className="font-mono">#{violation.auditLogId}</span>
      <span className="text-fg-muted">— {violation.reason ?? '—'}</span>
      {status !== 'NONE' && (
        <span className="text-[10px] font-bold uppercase tracking-wide text-fg-muted">
          {t(`alerts:detail.auditIntegrityRupture.conciliationStatus.${status}`)}
        </span>
      )}
    </div>
  );
}

function EntryViolationLine({ violation }: { violation: EntryIntegrityViolation }) {
  return (
    <li>
      <EntryViolationContent violation={violation} />
    </li>
  );
}

const RECONCILE_CATEGORIES = [
  ReconcileCategory.ACCIDENTAL_DBA_EDIT,
  ReconcileCategory.CORRUPTION,
  ReconcileCategory.INVESTIGATED_BENIGN,
  ReconcileCategory.OTHER,
] as const satisfies readonly IntegrityRuptureReconciliationRequestCategory[];

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-4">
      <dt className="w-40 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">
        {label}
      </dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

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

interface AuditChainGapPayload {
  anchorCheckpointId?: number;
  gapStart?: string;
  estimatedGapEnd?: string;
  estimatedGapMinutes?: number;
  message?: string;
}

interface AuditIntegrityRupturePayload {
  windowStart?: string;
  windowEnd?: string;
  chainStatus?: string;
  violationCount?: number;
  entryHmacViolationCount?: number;
  failBoundary?: string;
  resumeBoundary?: string;
  message?: string;
  entryViolations?: CappedViolationListPayload<EntryIntegrityViolation>;
  chainViolations?: CappedViolationListPayload<ChainIntegrityViolation>;
}

function parsePayload(payload: string | undefined): unknown | null {
  if (!payload) return null;
  try {
    return JSON.parse(payload);
  } catch {
    return null;
  }
}

function isAuditChainGapPayload(value: unknown): value is AuditChainGapPayload {
  return typeof value === 'object' && value !== null;
}

function isAuditIntegrityRupturePayload(value: unknown): value is AuditIntegrityRupturePayload {
  return typeof value === 'object' && value !== null;
}

function AuditChainGapPayloadView({ payload }: { payload: AuditChainGapPayload }) {
  const { t } = useTranslation(['alerts']);
  return (
    <dl className="space-y-3">
      {payload.anchorCheckpointId != null && (
        <InfoRow label={t('alerts:detail.auditChainGap.anchorCheckpointId')}>
          <span className="font-mono">{payload.anchorCheckpointId}</span>
          {' '}
          <Link
            to={`/integrity?focusCheckpointId=${payload.anchorCheckpointId}`}
            className="ml-2 inline-flex items-center gap-1 text-xs font-bold text-accent hover:underline"
          >
            {t('alerts:detail.auditChainGap.viewAuditLogs')}
            <ExternalLink className="size-3" />
          </Link>
        </InfoRow>
      )}
      {payload.gapStart && (
        <InfoRow label={t('alerts:detail.auditChainGap.gapStart')}>
          <span className="text-xs text-fg-muted">{formatDate(payload.gapStart)}</span>
        </InfoRow>
      )}
      {payload.estimatedGapEnd && (
        <InfoRow label={t('alerts:detail.auditChainGap.estimatedGapEnd')}>
          <span className="text-xs text-fg-muted">{formatDate(payload.estimatedGapEnd)}</span>
        </InfoRow>
      )}
      {payload.estimatedGapMinutes != null && (
        <InfoRow label={t('alerts:detail.auditChainGap.estimatedGapMinutes')}>
          <span className="text-xs font-mono">{payload.estimatedGapMinutes}</span>
        </InfoRow>
      )}
      {payload.message && (
        <InfoRow label={t('alerts:detail.auditChainGap.message')}>
          <span className="text-sm">{payload.message}</span>
        </InfoRow>
      )}
    </dl>
  );
}

function ViolationTruncationNote({
  truncated,
  totalCount,
  returnedCount,
}: {
  truncated?: boolean;
  totalCount?: number;
  returnedCount?: number;
}) {
  const { t } = useTranslation(['alerts']);
  if (!truncated) {
    return null;
  }
  return (
    <p className="text-xs text-fg-muted">
      {t('alerts:detail.auditIntegrityRupture.truncatedList', {
        returned: returnedCount ?? 0,
        total: totalCount ?? 0,
      })}
    </p>
  );
}

function IntegrityRuptureInvestigationSection({
  payload,
  alertOpen,
}: {
  payload: AuditIntegrityRupturePayload;
  alertOpen: boolean;
}) {
  const { t } = useTranslation(['alerts']);
  const [liveEntryReport, setLiveEntryReport] = useState<IntegrityReport | null>(null);
  const [liveChainReport, setLiveChainReport] = useState<ChainVerificationReport | null>(null);
  const [liveLoading, setLiveLoading] = useState(false);
  const [liveError, setLiveError] = useState<string | null>(null);

  const windowFrom = payload.failBoundary ?? payload.windowStart;
  const windowTo = payload.resumeBoundary ?? payload.windowEnd;

  useEffect(() => {
    if (!alertOpen || !windowFrom || !windowTo) {
      return;
    }
    let cancelled = false;
    setLiveLoading(true);
    setLiveError(null);
    void (async () => {
      try {
        const [entryReport, chainReport] = await Promise.all([
          checkIntegrity({ from: windowFrom, to: windowTo }) as unknown as Promise<IntegrityReport>,
          checkChainIntegrity({ from: windowFrom, to: windowTo }) as unknown as Promise<ChainVerificationReport>,
        ]);
        if (cancelled) {
          return;
        }
        setLiveEntryReport(entryReport);
        setLiveChainReport(chainReport);
      } catch (e) {
        if (!cancelled) {
          setLiveError(
            e instanceof Error ? e.message : t('alerts:detail.auditIntegrityRupture.liveVerifyFailed'),
          );
        }
      } finally {
        if (!cancelled) {
          setLiveLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [alertOpen, windowFrom, windowTo, t]);

  const snapshotEntries = payload.entryViolations?.items ?? [];
  const snapshotChains = payload.chainViolations?.items ?? [];
  const liveEntries = liveEntryReport?.entryViolations?.items ?? [];
  const liveChains = liveChainReport?.chainViolations ?? [];

  const investigateHref = useMemo(() => {
    if (!windowFrom || !windowTo) {
      return null;
    }
    const params = new URLSearchParams();
    params.set('source', 'integrity-alert');
    params.set('createdAfter', windowFrom);
    params.set('createdBefore', windowTo);
    const highlightIds = liveEntries
      .map((v) => v.auditLogId)
      .filter((id): id is number => id != null && id > 0);
    if (highlightIds.length > 0) {
      params.set('highlightAuditLogIds', formatHighlightAuditLogIds(highlightIds));
    }
    return `/integrity?${params.toString()}`;
  }, [windowFrom, windowTo, liveEntries]);

  const handleInvestigateClick = () => {
    if (!windowFrom || !windowTo) {
      return;
    }
    saveIntegrityInvestigationSession(
      buildInvestigationSession({
        source: 'integrity-alert',
        windowFrom,
        windowTo,
        entryViolations: liveEntries.length > 0 ? liveEntries : snapshotEntries,
        chainViolations: liveChains.length > 0 ? liveChains : snapshotChains,
        highlightAuditLogIds: liveEntries
          .map((v) => v.auditLogId)
          .filter((id): id is number => id != null && id > 0),
      }),
    );
  };

  return (
    <div className="space-y-4">
      <AuditIntegrityRupturePayloadView payload={payload} />

      <div className="border-t-2 border-fg/10 pt-4 space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-bold text-sm uppercase tracking-wider">
            {t('alerts:detail.auditIntegrityRupture.investigationHeading')}
          </h3>
          {liveLoading && (
            <span className="inline-flex items-center gap-1 text-xs text-fg-muted">
              <Loader2 className="size-3 animate-spin" />
              {t('alerts:detail.auditIntegrityRupture.liveVerifying')}
            </span>
          )}
        </div>

        {liveError && (
          <UiAlert variant="error">{liveError}</UiAlert>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="border-2 border-fg/10 p-3 space-y-2">
            <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted">
              {t('alerts:detail.auditIntegrityRupture.atDetection')}
            </p>
            <p className="text-xs font-bold">{t('alerts:detail.auditIntegrityRupture.entrySection')}</p>
            {snapshotEntries.length === 0 ? (
              <p className="text-xs text-fg-muted">{t('alerts:detail.auditIntegrityRupture.noEntryViolations')}</p>
            ) : (
              <ul className="text-xs space-y-1">
                {snapshotEntries.map((v) => (
                  <li key={v.auditLogId} className="font-mono">
                    #{v.auditLogId} — {v.reason ?? '—'}
                  </li>
                ))}
              </ul>
            )}
            <ViolationTruncationNote
              truncated={payload.entryViolations?.truncated}
              totalCount={payload.entryViolations?.totalCount}
              returnedCount={payload.entryViolations?.returnedCount}
            />
            <p className="text-xs font-bold pt-2">{t('alerts:detail.auditIntegrityRupture.chainSection')}</p>
            {snapshotChains.length === 0 ? (
              <p className="text-xs text-fg-muted">{t('alerts:detail.auditIntegrityRupture.noChainViolations')}</p>
            ) : (
              <ul className="text-xs space-y-1">
                {snapshotChains.map((v) => (
                  <li key={`${v.checkpointId}-${v.violationType}`}>
                    <span className="font-mono">#{v.checkpointId}</span> — {v.violationType}
                  </li>
                ))}
              </ul>
            )}
            <ViolationTruncationNote
              truncated={payload.chainViolations?.truncated}
              totalCount={payload.chainViolations?.totalCount}
              returnedCount={payload.chainViolations?.returnedCount}
            />
          </div>

          <div className="border-2 border-fg/10 p-3 space-y-2">
            <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted">
              {t('alerts:detail.auditIntegrityRupture.verifiedNow')}
            </p>
            {!liveLoading && !liveEntryReport && !liveChainReport && !liveError && (
              <p className="text-xs text-fg-muted">{t('alerts:detail.auditIntegrityRupture.livePending')}</p>
            )}
            {liveEntryReport && (
              <>
                <p className="text-xs font-bold">{t('alerts:detail.auditIntegrityRupture.entrySection')}</p>
                {liveEntries.length === 0 ? (
                  <p className="text-xs text-success">{t('alerts:detail.auditIntegrityRupture.noEntryViolations')}</p>
                ) : (
                  <ul className="text-xs space-y-1.5">
                    {liveEntries.map((v) => (
                      <EntryViolationLine key={v.auditLogId} violation={v} />
                    ))}
                  </ul>
                )}
              </>
            )}
            {liveChainReport && (
              <>
                <p className="text-xs font-bold pt-2">{t('alerts:detail.auditIntegrityRupture.chainSection')}</p>
                {liveChains.length === 0 ? (
                  <p className="text-xs text-success">{t('alerts:detail.auditIntegrityRupture.noChainViolations')}</p>
                ) : (
                  <ul className="text-xs space-y-1">
                    {liveChains.map((v) => (
                      <li key={`${v.checkpointId}-${v.violationType}`} className="text-error">
                        <span className="font-mono">#{v.checkpointId}</span> — {v.violationType}
                        {v.checkpointId != null && (
                          <Link
                            to={`/integrity?focusCheckpointId=${v.checkpointId}&createdAfter=${encodeURIComponent(windowFrom ?? '')}&createdBefore=${encodeURIComponent(windowTo ?? '')}`}
                            className="ml-2 text-accent underline text-[10px]"
                          >
                            {t('alerts:detail.auditIntegrityRupture.viewCheckpoint')}
                          </Link>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </div>
        </div>

        {investigateHref && (
          <Link
            to={investigateHref}
            onClick={handleInvestigateClick}
            className="inline-flex items-center gap-1.5 text-sm font-bold text-accent hover:underline"
          >
            {t('alerts:detail.auditIntegrityRupture.investigateCta')}
            <ExternalLink className="size-3.5" />
          </Link>
        )}
      </div>
    </div>
  );
}

function AuditIntegrityRupturePayloadView({ payload }: { payload: AuditIntegrityRupturePayload }) {
  const { t } = useTranslation(['alerts']);
  return (
    <dl className="space-y-3">
      {payload.windowStart && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.windowStart')}>
          <span className="text-xs text-fg-muted">{formatDate(payload.windowStart)}</span>
        </InfoRow>
      )}
      {payload.windowEnd && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.windowEnd')}>
          <span className="text-xs text-fg-muted">{formatDate(payload.windowEnd)}</span>
        </InfoRow>
      )}
      {payload.chainStatus && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.chainStatus')}>
          <span className="font-mono text-xs">{payload.chainStatus}</span>
        </InfoRow>
      )}
      {payload.violationCount != null && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.violationCount')}>
          <span className="font-mono text-xs">{payload.violationCount}</span>
        </InfoRow>
      )}
      {payload.entryHmacViolationCount != null && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.entryHmacViolationCount')}>
          <span className="font-mono text-xs">{payload.entryHmacViolationCount}</span>
        </InfoRow>
      )}
      {payload.failBoundary && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.failBoundary')}>
          <span className="text-xs text-fg-muted">{formatDate(payload.failBoundary)}</span>
        </InfoRow>
      )}
      {payload.resumeBoundary && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.resumeBoundary')}>
          <span className="text-xs text-fg-muted">{formatDate(payload.resumeBoundary)}</span>
        </InfoRow>
      )}
      {payload.message && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.message')}>
          <span className="text-sm">{payload.message}</span>
        </InfoRow>
      )}
    </dl>
  );
}

export default function AlertDetailPage() {
  const { t } = useTranslation(['alerts', 'common']);
  const { alertId } = useParams<{ alertId: string }>();
  const id = Number(alertId);
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [reconcileOpen, setReconcileOpen] = useState(false);
  const [reconcileJustification, setReconcileJustification] = useState('');
  const [reconcileCategory, setReconcileCategory] =
    useState<IntegrityRuptureReconciliationRequestCategory>(
      ReconcileCategory.INVESTIGATED_BENIGN,
    );
  const [reconcileTicketRef, setReconcileTicketRef] = useState('');
  const [reconcileResult, setReconcileResult] =
    useState<IntegrityRuptureReconciliationResult | null>(null);
  const [reconcileLiveEntries, setReconcileLiveEntries] = useState<EntryIntegrityViolation[]>([]);
  const [reconcileLiveLoading, setReconcileLiveLoading] = useState(false);
  const [reconcileLiveError, setReconcileLiveError] = useState<string | null>(null);
  const [checkedAckIds, setCheckedAckIds] = useState<number[]>([]);
  const [ackValidationError, setAckValidationError] = useState(false);

  const { data: alert, isLoading } = useGetAlert<AlertResponseDto>(
    Number.isNaN(id) ? 0 : id,
    { query: { enabled: !Number.isNaN(id) } },
  );

  const parsedPayload = useMemo(() => parsePayload(alert?.payload), [alert?.payload]);
  const isGapAlert = alert?.alertType === 'AUDIT_CHAIN_GAP_PENDING';
  const isIntegrityRuptureAlert = alert?.alertType === 'AUDIT_INTEGRITY_RUPTURE';

  const rupturePayload = useMemo(() => {
    if (!isIntegrityRuptureAlert || !isAuditIntegrityRupturePayload(parsedPayload)) {
      return null;
    }
    return parsedPayload;
  }, [isIntegrityRuptureAlert, parsedPayload]);

  const failBoundary = rupturePayload?.failBoundary ?? rupturePayload?.windowStart;
  const resumeBoundary = rupturePayload?.resumeBoundary ?? rupturePayload?.windowEnd;
  const canReconcile =
    alert?.status === 'OPEN'
    && isIntegrityRuptureAlert
    && Boolean(failBoundary && resumeBoundary);

  const requiredAckViolations = useMemo(
    () => listReconcileRequiredEntryViolations(reconcileLiveEntries),
    [reconcileLiveEntries],
  );
  const requiredAckIds = useMemo(
    () =>
      requiredAckViolations
        .map((v) => v.auditLogId)
        .filter((id): id is number => id != null && id > 0)
        .sort((a, b) => a - b),
    [requiredAckViolations],
  );
  const allRequiredAckChecked =
    requiredAckIds.length === 0
    || requiredAckIds.every((id) => checkedAckIds.includes(id));

  useEffect(() => {
    if (!reconcileOpen || !failBoundary || !resumeBoundary) {
      return;
    }
    let cancelled = false;
    setReconcileLiveLoading(true);
    setReconcileLiveError(null);
    setCheckedAckIds([]);
    setAckValidationError(false);
    void (async () => {
      try {
        const report = (await checkIntegrity({
          from: failBoundary,
          to: resumeBoundary,
        })) as unknown as IntegrityReport;
        if (cancelled) {
          return;
        }
        setReconcileLiveEntries(report.entryViolations?.items ?? []);
      } catch (e) {
        if (!cancelled) {
          setReconcileLiveError(
            e instanceof Error ? e.message : t('alerts:reconcileDialog.liveVerifyFailed'),
          );
          setReconcileLiveEntries([]);
        }
      } finally {
        if (!cancelled) {
          setReconcileLiveLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [reconcileOpen, failBoundary, resumeBoundary, t]);

  const reconcileMutation = useReconcileIntegrityRupture({
    mutation: {
      onSuccess: async (data) => {
        const result = data as unknown as IntegrityRuptureReconciliationResult;
        setReconcileResult(result);
        clearIntegrityInvestigationSession();
        toast(t('alerts:reconcileDialog.toastSuccess'), 'success');
        if (!Number.isNaN(id)) {
          await queryClient.invalidateQueries({ queryKey: getGetAlertQueryKey(id) });
        }
        await queryClient.invalidateQueries({ queryKey: ['/api/v1/alerts'] });
      },
      onError: (error) => {
        toast(
          getTranslatedApiError(error, t, t('alerts:reconcileDialog.errorFailed')),
          'error',
        );
      },
    },
  });

  const closeReconcileDialog = () => {
    setReconcileOpen(false);
    setReconcileJustification('');
    setReconcileCategory(ReconcileCategory.INVESTIGATED_BENIGN);
    setReconcileTicketRef('');
    setReconcileResult(null);
    setReconcileLiveEntries([]);
    setReconcileLiveError(null);
    setCheckedAckIds([]);
    setAckValidationError(false);
    reconcileMutation.reset();
  };

  const openReconcileDialog = () => {
    setReconcileResult(null);
    setReconcileLiveEntries([]);
    setReconcileLiveError(null);
    setCheckedAckIds([]);
    setAckValidationError(false);
    setReconcileOpen(true);
  };

  const toggleAckEntry = (auditLogId: number, checked: boolean) => {
    setCheckedAckIds((prev) => {
      if (checked) {
        return prev.includes(auditLogId) ? prev : [...prev, auditLogId].sort((a, b) => a - b);
      }
      return prev.filter((id) => id !== auditLogId);
    });
    setAckValidationError(false);
  };

  return (
    <AppShell
      title={isLoading ? t('alerts:detail.fallbackTitle') : t('alerts:detail.title', { id: alert?.alertId ?? '?' })}
      breadcrumb={[{ label: t('alerts:detail.breadcrumb'), path: '/alerts' }]}
    >
      {isLoading && (
        <div className="flex items-center justify-center h-32">
          <span className="size-5 border-2 border-fg border-t-transparent rounded-full animate-spin" />
        </div>
      )}

      {alert && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* Details card */}
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>{t('alerts:detail.cardDetails')}</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label={t('alerts:detail.labelId')}>
                    <span className="font-mono">{alert.alertId}</span>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelType')}>
                    <span className="font-medium">
                      {alert.alertType
                        ? t(`alerts:type.${alert.alertType}`, { defaultValue: alert.alertType })
                        : '—'}
                    </span>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelSeverity')}>
                    <Badge variant={severityVariant(alert.severity)}>
                      {alert.severity
                        ? t(`alerts:severity.${alert.severity}`, { defaultValue: alert.severity })
                        : '—'}
                    </Badge>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelStatus')}>
                    <Badge variant={statusVariant(alert.status)}>
                      {alert.status
                        ? t(`alerts:status.${alert.status}`, { defaultValue: alert.status })
                        : '—'}
                    </Badge>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelDedupeKey')}>
                    <span className="font-mono text-xs break-all">{alert.dedupeKey ?? '—'}</span>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelOccurrences')}>
                    <span className="font-mono text-xs">{alert.occurrenceCount ?? 1}</span>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelCreatedAt')}>
                    <span className="text-xs text-fg-muted">
                      {alert.createdAt ? formatDate(alert.createdAt) : '—'}
                    </span>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelLastSeenAt')}>
                    <span className="text-xs text-fg-muted">
                      {alert.lastSeenAt ? formatDate(alert.lastSeenAt) : '—'}
                    </span>
                  </InfoRow>
                </dl>
              </CardContent>
            </Card>

            {/* Resolution card */}
            <Card>
              <CardHeader>
                <CardTitle>{t('alerts:detail.cardResolution')}</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label={t('alerts:detail.labelResolvedAt')}>
                    <span className="text-xs text-fg-muted">
                      {alert.resolvedAt ? formatDate(alert.resolvedAt) : '—'}
                    </span>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelResolvedBy')}>
                    <span className="font-mono text-xs">
                      {alert.resolvedByAdminId != null ? `#${alert.resolvedByAdminId}` : '—'}
                    </span>
                  </InfoRow>
                  <InfoRow label={t('alerts:detail.labelResolutionReason')}>
                    <span className="text-xs">
                      {alert.resolutionReason
                        ? t(`alerts:resolutionReason.${alert.resolutionReason}`, {
                            defaultValue: alert.resolutionReason,
                          })
                        : '—'}
                    </span>
                  </InfoRow>
                </dl>
                {canReconcile && (
                  <div className="mt-4 pt-4 border-t-2 border-fg/10">
                    <Button type="button" size="sm" onClick={openReconcileDialog}>
                      {t('alerts:reconcileDialog.openAction')}
                    </Button>
                    <p className="mt-2 text-xs text-fg-muted">
                      {t('alerts:reconcileDialog.openHint')}
                    </p>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Payload card */}
          <Card>
            <CardHeader>
              <CardTitle>{t('alerts:detail.cardPayload')}</CardTitle>
            </CardHeader>
            <CardContent>
              {!alert.payload && (
                <p className="text-sm text-fg-muted">{t('alerts:detail.payloadEmpty')}</p>
              )}
              {alert.payload && isGapAlert && isAuditChainGapPayload(parsedPayload) && (
                <div className="space-y-4">
                  <AuditChainGapPayloadView payload={parsedPayload as AuditChainGapPayload} />
                </div>
              )}
              {alert.payload
                && isIntegrityRuptureAlert
                && isAuditIntegrityRupturePayload(parsedPayload) && (
                <IntegrityRuptureInvestigationSection
                  payload={parsedPayload as AuditIntegrityRupturePayload}
                  alertOpen={alert.status === 'OPEN'}
                />
              )}
              {alert.payload
                && (!isGapAlert || !isAuditChainGapPayload(parsedPayload))
                && (!isIntegrityRuptureAlert || !isAuditIntegrityRupturePayload(parsedPayload)) && (
                <div>
                  <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted mb-2">
                    {t('alerts:detail.payloadRawHeading')}
                  </p>
                  <pre className="text-xs bg-fg/5 border-2 border-fg/10 p-3 overflow-auto whitespace-pre-wrap break-all">
                    {alert.payload}
                  </pre>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      <Dialog
        open={reconcileOpen}
        onClose={closeReconcileDialog}
        title={t('alerts:reconcileDialog.title')}
        size="md"
        dismissible={false}
      >
        {reconcileResult ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-success">
              <CheckCircle className="size-5" />
              <span className="font-bold">{t('alerts:reconcileDialog.successTitle')}</span>
            </div>
            <dl className="space-y-1.5 text-sm">
              <InfoPair
                label={t('alerts:reconcileDialog.resultBoundaries')}
                value={
                  reconcileResult.failBoundary && reconcileResult.resumeBoundary
                    ? `${formatDate(reconcileResult.failBoundary)} → ${formatDate(reconcileResult.resumeBoundary)}`
                    : '—'
                }
              />
              <InfoPair
                label={t('alerts:reconcileDialog.resultCheckpointId')}
                value={String(reconcileResult.conciliationCheckpointId ?? '—')}
              />
              <InfoPair
                label={t('alerts:reconcileDialog.resultChainHmac')}
                value={reconcileResult.conciliationChainHmac ?? '—'}
                mono
              />
              <InfoPair
                label={t('alerts:reconcileDialog.resultAuditLogId')}
                value={String(reconcileResult.auditLogId ?? '—')}
              />
              {(reconcileResult.entryConciliationCount ?? 0) > 0 && (
                <>
                  <InfoPair
                    label={t('alerts:reconcileDialog.resultEntryConciliationCount')}
                    value={String(reconcileResult.entryConciliationCount ?? 0)}
                  />
                  <InfoPair
                    label={t('alerts:reconcileDialog.resultConciliatedEntries')}
                    value={
                      reconcileResult.conciliatedAuditLogIds?.length
                        ? reconcileResult.conciliatedAuditLogIds
                            .map((entryId) => `#${entryId}`)
                            .join(', ')
                        : '—'
                    }
                    mono
                  />
                </>
              )}
            </dl>
            <div className="flex justify-end pt-2">
              <Button type="button" onClick={closeReconcileDialog}>
                {t('alerts:reconcileDialog.done')}
              </Button>
            </div>
          </div>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!canReconcile || !failBoundary || !resumeBoundary || Number.isNaN(id)) {
                return;
              }
              if (!allRequiredAckChecked) {
                setAckValidationError(true);
                return;
              }
              reconcileMutation.mutate({
                data: {
                  alertId: id,
                  failBoundary,
                  resumeBoundary,
                  justification: reconcileJustification.trim(),
                  category: reconcileCategory,
                  externalTicketReference: reconcileTicketRef.trim() || undefined,
                  acknowledgedAuditLogIds:
                    requiredAckIds.length > 0 ? requiredAckIds : undefined,
                },
              });
            }}
            className="space-y-4"
          >
            <p className="text-xs text-fg-muted">{t('alerts:reconcileDialog.intro')}</p>
            {failBoundary && resumeBoundary && (
              <div className="border-2 border-warning/40 bg-warning/5 p-3 space-y-1">
                <p className="text-[10px] uppercase tracking-wider text-fg-muted font-bold">
                  {t('alerts:reconcileDialog.boundariesHeading')}
                </p>
                <p className="text-sm font-bold">
                  {formatDate(failBoundary)} → {formatDate(resumeBoundary)}
                </p>
              </div>
            )}
            <div className="space-y-2 border-2 border-fg/10 p-3">
              <p className="text-[10px] uppercase tracking-wider text-fg-muted font-bold">
                {t('alerts:reconcileDialog.entryAckHeading')}
              </p>
              <p className="text-xs text-fg-muted">{t('alerts:reconcileDialog.entryAckHint')}</p>
              {reconcileLiveLoading && (
                <p className="inline-flex items-center gap-1 text-xs text-fg-muted">
                  <Loader2 className="size-3 animate-spin" />
                  {t('alerts:reconcileDialog.liveLoading')}
                </p>
              )}
              {reconcileLiveError && <UiAlert variant="error">{reconcileLiveError}</UiAlert>}
              {!reconcileLiveLoading && !reconcileLiveError && reconcileLiveEntries.length === 0 && (
                <p className="text-xs text-success">{t('alerts:reconcileDialog.entryAckNone')}</p>
              )}
              {!reconcileLiveLoading && reconcileLiveEntries.length > 0 && (
                <ul className="space-y-2">
                  {reconcileLiveEntries.map((violation) => {
                    const auditLogId = violation.auditLogId;
                    if (auditLogId == null) {
                      return null;
                    }
                    const needsAck = isEntryReconcileAckRequired(violation);
                    return (
                      <li
                        key={auditLogId}
                        className="flex items-start gap-2 border-b border-fg/10 pb-2 last:border-0 last:pb-0"
                      >
                        {needsAck ? (
                          <input
                            type="checkbox"
                            id={`reconcile-ack-${auditLogId}`}
                            checked={checkedAckIds.includes(auditLogId)}
                            onChange={(ev) => toggleAckEntry(auditLogId, ev.target.checked)}
                            className="mt-0.5 size-4 border-2 border-fg"
                          />
                        ) : (
                          <span className="mt-0.5 size-4 shrink-0" aria-hidden />
                        )}
                        <label
                          htmlFor={needsAck ? `reconcile-ack-${auditLogId}` : undefined}
                          className="flex-1 cursor-pointer"
                        >
                          <EntryViolationContent violation={violation} />
                          {!needsAck && (
                            <p className="text-[10px] text-fg-muted mt-0.5">
                              {t('alerts:reconcileDialog.entryAckExplainedNote')}
                            </p>
                          )}
                        </label>
                      </li>
                    );
                  })}
                </ul>
              )}
              {ackValidationError && (
                <UiAlert variant="error">{t('alerts:reconcileDialog.entryAckValidation')}</UiAlert>
              )}
            </div>
            <div className="space-y-1">
              <Label htmlFor="reconcile-category" className="text-xs">
                {t('alerts:reconcileDialog.categoryLabel')}
              </Label>
              <Select
                id="reconcile-category"
                value={reconcileCategory}
                onChange={(e) =>
                  setReconcileCategory(
                    e.target.value as IntegrityRuptureReconciliationRequestCategory,
                  )}
              >
                {RECONCILE_CATEGORIES.map((category) => (
                  <option key={category} value={category}>
                    {t(`alerts:reconcileDialog.category.${category}`)}
                  </option>
                ))}
              </Select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="reconcile-ticket" className="text-xs">
                {t('alerts:reconcileDialog.ticketLabel')}{' '}
                <span className="text-fg-muted font-normal">
                  {t('alerts:reconcileDialog.ticketOptional')}
                </span>
              </Label>
              <Input
                id="reconcile-ticket"
                value={reconcileTicketRef}
                onChange={(e) => setReconcileTicketRef(e.target.value)}
                maxLength={128}
                placeholder={t('alerts:reconcileDialog.ticketPlaceholder')}
              />
            </div>
            <ReasonFieldRow
              presetGroup="audit_chain_justification"
              idPrefix="alert-reconcile"
              inputId="reconcile-just"
              value={reconcileJustification}
              onChange={setReconcileJustification}
              label={
                <>
                  {t('alerts:reconcileDialog.justification')}{' '}
                  <span className="text-fg-muted font-normal">
                    {t('alerts:reconcileDialog.justificationHint')}
                  </span>
                </>
              }
              placeholder={t('alerts:reconcileDialog.justificationPlaceholder')}
              showMinLengthError={
                reconcileJustification.trim().length > 0
                && reconcileJustification.trim().length < 10
              }
              minLengthErrorTone="justification"
            />
            {reconcileMutation.isError && (
              <UiAlert variant="error">
                {getTranslatedApiError(
                  reconcileMutation.error,
                  t,
                  t('alerts:reconcileDialog.errorFailed'),
                )}
              </UiAlert>
            )}
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="secondary" onClick={closeReconcileDialog}>
                {t('alerts:reconcileDialog.cancel')}
              </Button>
              <Button
                type="submit"
                disabled={
                  reconcileMutation.isPending
                  || reconcileJustification.trim().length < 10
                  || reconcileLiveLoading
                  || Boolean(reconcileLiveError)
                }
              >
                {reconcileMutation.isPending
                  ? t('alerts:reconcileDialog.submitting')
                  : t('alerts:reconcileDialog.submit')}
              </Button>
            </div>
          </form>
        )}
      </Dialog>
    </AppShell>
  );
}
