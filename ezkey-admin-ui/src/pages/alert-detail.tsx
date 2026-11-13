import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { ExternalLink, Loader2 } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { Alert as UiAlert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { EntryHmacBadge } from '@/components/feature/entry-hmac-badge';
import { useGetAlert } from '@/generated/admin-api/alerts/alerts';
import {
  checkChainIntegrity,
  checkIntegrity,
} from '@/generated/admin-api/audit-logs/audit-logs';
import type {
  AlertResponseDto,
  AlertResponseDtoSeverity,
  AlertResponseDtoStatus,
  ChainIntegrityViolation,
  ChainVerificationReport,
  EntryIntegrityViolation,
  IntegrityReport,
} from '@/generated/admin-api/model';
import {
  buildIntegrityDeepLink,
  buildIntegrityReconcileDeepLink,
  buildInvestigationSession,
  type CappedViolationListPayload,
  entryViolationDisplayState,
  saveIntegrityInvestigationSession,
} from '@/lib/integrity-investigation-session';
import { formatDate } from '@/lib/utils';

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
  severity?: AlertResponseDtoSeverity,
): 'default' | 'success' | 'warning' | 'error' | 'muted' {
  if (severity === 'CRITICAL') return 'error';
  if (severity === 'WARNING') return 'warning';
  if (severity === 'INFO') return 'muted';
  return 'default';
}

function statusVariant(
  status?: AlertResponseDtoStatus,
): 'default' | 'success' | 'warning' | 'error' | 'muted' {
  if (status === 'RESOLVED') return 'success';
  if (status === 'OPEN') return 'warning';
  return 'default';
}

function parsePayload(raw?: string | null): unknown {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return null;
  }
}

interface AuditChainGapPayload {
  anchorCheckpointId?: number;
  gapStart?: string;
  estimatedGapEnd?: string;
  estimatedGapMinutes?: number;
  message?: string;
}

function isAuditChainGapPayload(value: unknown): value is AuditChainGapPayload {
  return typeof value === 'object' && value !== null;
}

interface AuditIntegrityRupturePayload {
  windowStart?: string;
  windowEnd?: string;
  failBoundary?: string;
  resumeBoundary?: string;
  chainStatus?: string;
  violationCount?: number;
  entryHmacViolationCount?: number;
  message?: string;
  entryViolations?: CappedViolationListPayload<EntryIntegrityViolation>;
  chainViolations?: CappedViolationListPayload<ChainIntegrityViolation>;
  /** Optional; only forwarded in deep-links when already present on the alert payload. */
  ruptureId?: string | number;
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

function AuditIntegrityRupturePayloadView({ payload }: { payload: AuditIntegrityRupturePayload }) {
  const { t } = useTranslation(['alerts']);
  return (
    <dl className="space-y-3">
      {payload.windowStart && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.windowStart')}>
          <span className="text-xs">{formatDate(payload.windowStart)}</span>
        </InfoRow>
      )}
      {payload.windowEnd && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.windowEnd')}>
          <span className="text-xs">{formatDate(payload.windowEnd)}</span>
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
          <span className="text-xs">{formatDate(payload.failBoundary)}</span>
        </InfoRow>
      )}
      {payload.resumeBoundary && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.resumeBoundary')}>
          <span className="text-xs">{formatDate(payload.resumeBoundary)}</span>
        </InfoRow>
      )}
      {payload.message && (
        <InfoRow label={t('alerts:detail.auditIntegrityRupture.message')}>
          <span className="text-xs">{payload.message}</span>
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
  alertId,
}: {
  payload: AuditIntegrityRupturePayload;
  alertOpen: boolean;
  alertId: number;
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
  const liveEntries = useMemo(
    () => liveEntryReport?.entryViolations?.items ?? [],
    [liveEntryReport],
  );
  const liveChains = liveChainReport?.chainViolations ?? [];

  const investigateHref = useMemo(() => {
    if (!windowFrom || !windowTo) {
      return null;
    }
    return buildIntegrityDeepLink({
      failBoundary: windowFrom,
      resumeBoundary: windowTo,
      alertId,
      highlightAuditLogIds: liveEntries
        .map((v) => v.auditLogId)
        .filter((id): id is number => id != null && id > 0),
    });
  }, [windowFrom, windowTo, alertId, liveEntries]);

  const reconcileHref = useMemo(() => {
    if (!alertOpen) {
      return null;
    }
    return buildIntegrityReconcileDeepLink({
      alertId,
      ruptureId: payload.ruptureId,
    });
  }, [alertId, alertOpen, payload.ruptureId]);

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
                        {v.checkpointId != null && windowFrom && windowTo && (
                          <Link
                            to={buildIntegrityDeepLink({
                              failBoundary: windowFrom,
                              resumeBoundary: windowTo,
                              alertId,
                              focusCheckpointId: v.checkpointId,
                            })}
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

        <div className="flex flex-wrap items-center gap-4">
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
          {reconcileHref && (
            <Link
              to={reconcileHref}
              onClick={handleInvestigateClick}
              className="inline-flex items-center gap-1.5 text-sm font-bold text-accent hover:underline"
            >
              {t('alerts:reconcileDialog.openAction')}
              <ExternalLink className="size-3.5" />
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}

export default function AlertDetailPage() {
  const { t } = useTranslation(['alerts', 'common']);
  const { alertId } = useParams<{ alertId: string }>();
  const id = Number(alertId);

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

  const canDeepLinkReconcile =
    alert?.status === 'OPEN'
    && isIntegrityRuptureAlert
    && !Number.isNaN(id);

  const resolveHref = useMemo(() => {
    if (!canDeepLinkReconcile) {
      return null;
    }
    return buildIntegrityReconcileDeepLink({
      alertId: id,
      ruptureId: rupturePayload?.ruptureId,
    });
  }, [canDeepLinkReconcile, id, rupturePayload?.ruptureId]);

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
                {resolveHref && (
                  <div className="mt-4 pt-4 border-t-2 border-fg/10 space-y-2">
                    <Link
                      to={resolveHref}
                      className="inline-flex items-center justify-center gap-2 px-3 py-1.5 text-xs font-bold tracking-wide bg-accent text-white border-2 border-fg shadow-brutal hover:shadow-brutal-lg hover:-translate-y-px"
                    >
                      {t('alerts:reconcileDialog.openAction')}
                      <ExternalLink className="size-3.5" />
                    </Link>
                    <p className="text-xs text-fg-muted">
                      {t('alerts:reconcileDialog.openHint')}
                    </p>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

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
                  alertId={id}
                />
              )}
              {alert.payload
                && (!isGapAlert || !isAuditChainGapPayload(parsedPayload))
                && (!isIntegrityRuptureAlert || !isAuditIntegrityRupturePayload(parsedPayload)) && (
                <div className="border-2 border-fg/10 bg-bg p-3 overflow-x-auto">
                  <pre className="text-xs font-mono whitespace-pre-wrap break-all">
                    {alert.payload}
                  </pre>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </AppShell>
  );
}
