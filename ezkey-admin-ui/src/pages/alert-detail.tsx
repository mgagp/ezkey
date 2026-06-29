import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { ExternalLink } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate } from '@/lib/utils';
import { useGetAlert } from '@/generated/admin-api/alerts/alerts';
import type {
  AlertResponseDto,
  AlertResponseDtoSeverity,
  AlertResponseDtoStatus,
} from '@/generated/admin-api/model';

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
            to={`/audit-logs?focusCheckpointId=${payload.anchorCheckpointId}`}
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

  const { data: alert, isLoading } = useGetAlert<AlertResponseDto>(
    Number.isNaN(id) ? 0 : id,
    { query: { enabled: !Number.isNaN(id) } },
  );

  const parsedPayload = useMemo(() => parsePayload(alert?.payload), [alert?.payload]);
  const isGapAlert = alert?.alertType === 'AUDIT_CHAIN_GAP_PENDING';
  const isIntegrityRuptureAlert = alert?.alertType === 'AUDIT_INTEGRITY_RUPTURE';

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
                <div className="space-y-4">
                  <AuditIntegrityRupturePayloadView
                    payload={parsedPayload as AuditIntegrityRupturePayload}
                  />
                </div>
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
    </AppShell>
  );
}
