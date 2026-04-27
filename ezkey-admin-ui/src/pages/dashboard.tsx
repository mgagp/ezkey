import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useEffect, useState } from 'react';
import { Activity, AlertTriangle, FileText, Key, Puzzle, RefreshCw, ShieldCheck, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { AppShell } from '@/components/layout/app-shell';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip } from '@/components/ui/tooltip';
import { EventStatusBadge } from '@/components/feature/event-status-badge';
import { getAuditEventTypeLabel } from '@/lib/audit-event-type';
import { formatCountdown, formatRelativeTime } from '@/lib/utils';
import { useAuth } from '@/context/auth-context';
import { useGetOverview } from '@/generated/admin-api/dashboard/dashboard';
import type { DashboardOverviewDto } from '@/generated/admin-api/model';
import { DashboardStatBadgeLink } from '@/components/feature/dashboard-stat-badge-link';
import {
  buildAuthAttemptAuditTrailUrl,
  buildAuthAttemptsDrilldownUrl,
  buildEnrollmentsDrilldownUrl,
  buildIntegrationsDrilldownUrl,
} from '@/lib/dashboard-drilldown-links';

const REFRESH_INTERVAL_OVERVIEW_MS = 60_000;

// ── Shared ────────────────────────────────────────────────────────────────────

function StatNum({ value, isLoading }: { value: number | undefined; isLoading: boolean }) {
  if (isLoading)
    return (
      <span className="inline-block size-5 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
    );
  return <>{value ?? '—'}</>;
}

interface StatCardProps {
  title: string;
  icon: typeof Puzzle;
  isLoading: boolean;
  children: ReactNode;
}
function StatCard({ title, icon: Icon, isLoading, children }: StatCardProps) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>{title}</CardTitle>
          <Icon className={`size-4 ${isLoading ? 'text-fg/20 animate-pulse' : 'text-fg-muted'}`} />
        </div>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

const quickActions = [
  { labelKey: 'newIntegration', descKey: 'newIntegrationDesc', to: '/integrations', icon: Puzzle },
  { labelKey: 'newEnrollment', descKey: 'newEnrollmentDesc', to: '/enrollments', icon: Users },
  { labelKey: 'newAdmin', descKey: 'newAdminDesc', to: '/admins', icon: ShieldCheck },
  { labelKey: 'newApiKey', descKey: 'newApiKeyDesc', to: '/api-keys', icon: Key },
];

function getUpdatedLabelKeyAndParams(
  dataUpdatedAtMs: number
): { key: string; params?: { count: number } } {
  if (dataUpdatedAtMs <= 0) return { key: 'refreshStrip.loading' };
  const diffMs = Date.now() - dataUpdatedAtMs;
  const diffMins = Math.floor(diffMs / 60_000);
  if (diffMins < 1) return { key: 'refreshStrip.updatedJustNow' };
  if (diffMins < 60) return { key: 'refreshStrip.updatedMinutesAgo', params: { count: diffMins } };
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return { key: 'refreshStrip.updatedHoursAgo', params: { count: diffHours } };
  return { key: 'refreshStrip.updatedDaysAgo', params: { count: Math.floor(diffHours / 24) } };
}

// ── Dashboard page ─────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const { t } = useTranslation(['dashboard', 'layout', 'audit-logs', 'alerts']);
  const { session } = useAuth();
  const navigate = useNavigate();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';

  const {
    data: overview,
    isLoading: overviewLoading,
    dataUpdatedAt: overviewUpdatedAt,
    refetch: overviewRefetch,
    isFetching: overviewFetching,
  } = useGetOverview<DashboardOverviewDto>({
    query: {
      staleTime: REFRESH_INTERVAL_OVERVIEW_MS,
      refetchInterval: REFRESH_INTERVAL_OVERVIEW_MS,
    },
  });

  const [secondsUntilNext, setSecondsUntilNext] = useState(0);
  useEffect(() => {
    if (overviewUpdatedAt == null || overviewUpdatedAt <= 0) return;
    const compute = () => {
      const next = Math.max(
        0,
        Math.floor(
          (overviewUpdatedAt + REFRESH_INTERVAL_OVERVIEW_MS - Date.now()) / 1000
        )
      );
      setSecondsUntilNext(next);
    };
    compute();
    const id = setInterval(compute, 1000);
    return () => clearInterval(id);
  }, [overviewUpdatedAt]);

  const intTotal = overview?.integrations?.total;
  const intActive = overview?.integrations?.active;
  const intRetired = overview?.integrations?.retired;

  const enrVerified = overview?.enrollments?.verified;
  const enrInProgress = overview?.enrollments?.inProgress;
  const enrSuspended = overview?.enrollments?.suspended;
  const enrExpired = overview?.enrollments?.expired;
  const enrIncidents = overview?.enrollments?.incidents;

  const authTotal = overview?.auth24h?.total;
  const authAccepted = overview?.auth24h?.accepted;
  const authRejected = overview?.auth24h?.rejected;
  const authPending24h = overview?.auth24h?.pending;
  const authRead24h = overview?.auth24h?.readCount;
  const authInvalid = overview?.auth24h?.invalid;
  const successRate = overview?.auth24h?.successRatePct;
  const terminalTotal = overview?.auth24h?.terminalTotal;
  const invalidCount = overview?.auth24h?.invalid;
  const expiredCount = overview?.auth24h?.expired;
  const rejectedDenyCount = overview?.auth24h?.rejected;

  const recentLogs = overview?.recentActivity ?? [];
  const alerts = overview?.alerts ?? [];

  const updatedLabel = getUpdatedLabelKeyAndParams(overviewUpdatedAt ?? 0);
  const updatedText =
    updatedLabel.params != null
      ? t(`dashboard:${updatedLabel.key}`, updatedLabel.params)
      : t(`dashboard:${updatedLabel.key}`);

  return (
    <AppShell title={t('layout:nav.dashboard')}>
      <div className="space-y-6">

        {/* Dashboard refresh strip */}
        <div className="flex items-center gap-2 text-xs text-fg-muted">
          <span>{updatedText}</span>
          <span aria-hidden>·</span>
          <span>
            {overviewUpdatedAt && overviewUpdatedAt > 0
              ? t('dashboard:refreshStrip.nextIn', { countdown: formatCountdown(secondsUntilNext) })
              : '—'}
          </span>
          <Tooltip content={t('dashboard:refreshStrip.refreshTooltip')}>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              aria-label={t('dashboard:refreshStrip.refreshTooltip')}
              disabled={overviewFetching}
              onClick={() => {
                overviewRefetch();
              }}
              className="size-8 p-0 shrink-0"
            >
              {overviewFetching ? (
                <span className="inline-block size-3.5 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
              ) : (
                <RefreshCw className="size-3.5" />
              )}
            </Button>
          </Tooltip>
        </div>

        {/* Audit chain alerts (Global Admin only) */}
        {isGlobalAdmin && alerts.length > 0 && (
          <Card className="border-2 border-error/50 bg-error/5">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2 text-error">
                  <AlertTriangle className="size-5" />
                  {t('dashboard:auditChain.title')}
                </CardTitle>
              </div>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-fg-muted mb-3">
                <Tooltip content={t('dashboard:auditChain.help.undeclaredGaps')}>
                  <span className="underline decoration-dotted cursor-help">{t('dashboard:auditChain.undeclaredGapsLabel')}</span>
                </Tooltip>
                {' '}{t('dashboard:auditChain.detectedMessage')}
              </p>
              <ul className="space-y-2">
                {alerts.map((alert) => {
                  let parsed: { anchorCheckpointId?: number; estimatedGapMinutes?: number } | null = null;
                  if (alert.payload) {
                    try { parsed = JSON.parse(alert.payload); } catch { parsed = null; }
                  }
                  return (
                    <li
                      key={alert.alertId}
                      className="flex flex-wrap items-baseline gap-2 text-sm border-b border-fg/10 pb-2 last:border-0 last:pb-0 cursor-pointer hover:bg-fg/5 -mx-1 px-1 rounded-sm"
                      onClick={() => { navigate(`/alerts/${alert.alertId}`); }}
                    >
                      <Badge variant="error">
                        {alert.alertType
                          ? t(`alerts:type.${alert.alertType}`, { defaultValue: alert.alertType })
                          : '—'}
                      </Badge>
                      <span className="text-fg-muted shrink-0">
                        {formatRelativeTime(alert.createdAt ?? '')}
                      </span>
                      {parsed?.anchorCheckpointId != null && (
                        <span className="text-fg">
                          <Tooltip content={t('dashboard:auditChain.help.anchorCheckpoint')}>
                            <span className="underline decoration-dotted cursor-help">{t('dashboard:auditChain.anchorCheckpointLabel')}</span>
                          </Tooltip>
                          : {parsed.anchorCheckpointId}
                          {parsed.estimatedGapMinutes != null &&
                            ` · ${t('dashboard:auditChain.gapMinutes', { count: parsed.estimatedGapMinutes })}`}
                        </span>
                      )}
                    </li>
                  );
                })}
              </ul>
              <Link
                to="/alerts"
                className="mt-3 inline-block text-sm font-bold text-accent hover:underline"
              >
                {t('dashboard:auditChain.openAlerts')}
              </Link>
            </CardContent>
          </Card>
        )}

        {/* Stats Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">

          <StatCard title={t('dashboard:stats.integrations')} icon={Puzzle} isLoading={overviewLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={intTotal} isLoading={overviewLoading} />
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <DashboardStatBadgeLink
                to={buildIntegrationsDrilldownUrl('active')}
                variant="success"
                ariaLabel={t('dashboard:drilldown.integrationsActive')}
              >
                <StatNum value={intActive} isLoading={overviewLoading} /> {t('dashboard:stats.active')}
              </DashboardStatBadgeLink>
              <DashboardStatBadgeLink
                to={buildIntegrationsDrilldownUrl('retired')}
                variant="muted"
                ariaLabel={t('dashboard:drilldown.integrationsRetired')}
              >
                <StatNum value={intRetired} isLoading={overviewLoading} /> {t('dashboard:stats.retired')}
              </DashboardStatBadgeLink>
            </div>
          </StatCard>

          <StatCard title={t('dashboard:stats.enrollments')} icon={Users} isLoading={overviewLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={enrVerified} isLoading={overviewLoading} />
            </p>
            <p className="text-xs text-fg-muted mt-2 font-medium">
              {t('dashboard:stats.enrollmentsHint')}
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Tooltip content={t('dashboard:stats.enrollmentInProgressHelp')}>
                <DashboardStatBadgeLink
                  to={buildEnrollmentsDrilldownUrl({ bucket: 'inProgress' })}
                  variant={(enrInProgress ?? 0) > 0 ? 'warning' : 'muted'}
                  ariaLabel={t('dashboard:drilldown.enrollmentsInProgress')}
                >
                  <StatNum value={enrInProgress} isLoading={overviewLoading} /> {t('dashboard:stats.enrollmentInProgress')}
                </DashboardStatBadgeLink>
              </Tooltip>
              <Tooltip content={t('dashboard:stats.enrollmentSuspendedHelp')}>
                <DashboardStatBadgeLink
                  to={buildEnrollmentsDrilldownUrl({ status: 'VERIFIED', active: false })}
                  variant={(enrSuspended ?? 0) > 0 ? 'warning' : 'muted'}
                  ariaLabel={t('dashboard:drilldown.enrollmentsSuspended')}
                >
                  <StatNum value={enrSuspended} isLoading={overviewLoading} /> {t('dashboard:stats.enrollmentSuspended')}
                </DashboardStatBadgeLink>
              </Tooltip>
              <DashboardStatBadgeLink
                to={buildEnrollmentsDrilldownUrl({ status: 'EXPIRED' })}
                variant={(enrExpired ?? 0) > 0 ? 'warning' : 'muted'}
                ariaLabel={t('dashboard:drilldown.enrollmentsExpired')}
              >
                <StatNum value={enrExpired} isLoading={overviewLoading} /> {t('dashboard:stats.enrollmentExpired')}
              </DashboardStatBadgeLink>
              <DashboardStatBadgeLink
                to={buildEnrollmentsDrilldownUrl({ bucket: 'incidents' })}
                variant={(enrIncidents ?? 0) > 0 ? 'error' : 'muted'}
                ariaLabel={t('dashboard:drilldown.enrollmentsIncidents')}
              >
                <StatNum value={enrIncidents} isLoading={overviewLoading} /> {t('dashboard:stats.enrollmentIncidents')}
              </DashboardStatBadgeLink>
            </div>
          </StatCard>

          <StatCard title={t('dashboard:stats.authAttempts24h')} icon={ShieldCheck} isLoading={overviewLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={authTotal} isLoading={overviewLoading} />
            </p>
            <p className="text-xs text-fg-muted mt-2 font-medium">
              {t('dashboard:stats.authAttempts24hHint')}
            </p>
            <p className="text-xs mt-1">
              <Link
                to={buildAuthAttemptAuditTrailUrl()}
                className="font-bold text-accent hover:underline"
              >
                {t('dashboard:stats.viewAuditTrail')}
              </Link>
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Tooltip content={t('dashboard:stats.pendingHelp')}>
                <DashboardStatBadgeLink
                  to={buildAuthAttemptsDrilldownUrl({ status: 'PENDING', rolling24h: true })}
                  variant={(authPending24h ?? 0) > 0 ? 'warning' : 'muted'}
                  ariaLabel={t('dashboard:drilldown.authPending')}
                >
                  <StatNum value={authPending24h} isLoading={overviewLoading} /> {t('dashboard:stats.pending')}
                </DashboardStatBadgeLink>
              </Tooltip>
              <Tooltip content={t('dashboard:stats.readHelp')}>
                <DashboardStatBadgeLink
                  to={buildAuthAttemptsDrilldownUrl({ status: 'READ', rolling24h: true })}
                  variant={(authRead24h ?? 0) > 0 ? 'warning' : 'muted'}
                  ariaLabel={t('dashboard:drilldown.authRead')}
                >
                  <StatNum value={authRead24h} isLoading={overviewLoading} /> {t('dashboard:stats.read')}
                </DashboardStatBadgeLink>
              </Tooltip>
              <DashboardStatBadgeLink
                to={buildAuthAttemptsDrilldownUrl({ status: 'ACCEPTED', rolling24h: true })}
                variant="success"
                ariaLabel={t('dashboard:drilldown.authAccepted')}
              >
                <StatNum value={authAccepted} isLoading={overviewLoading} /> {t('dashboard:stats.accepted')}
              </DashboardStatBadgeLink>
              <DashboardStatBadgeLink
                to={buildAuthAttemptsDrilldownUrl({ status: 'REJECTED', rolling24h: true })}
                variant="error"
                ariaLabel={t('dashboard:drilldown.authRejected')}
              >
                <StatNum value={authRejected} isLoading={overviewLoading} /> {t('dashboard:stats.rejected')}
              </DashboardStatBadgeLink>
              <DashboardStatBadgeLink
                to={buildAuthAttemptsDrilldownUrl({ status: 'INVALID', rolling24h: true })}
                variant={(authInvalid ?? 0) > 0 ? 'error' : 'muted'}
                ariaLabel={t('dashboard:drilldown.authInvalid')}
              >
                <StatNum value={authInvalid} isLoading={overviewLoading} /> {t('dashboard:stats.invalid')}
              </DashboardStatBadgeLink>
              <DashboardStatBadgeLink
                to={buildAuthAttemptsDrilldownUrl({ status: 'EXPIRED', rolling24h: true })}
                variant={(expiredCount ?? 0) > 0 ? 'warning' : 'muted'}
                ariaLabel={t('dashboard:drilldown.authExpired')}
              >
                <StatNum value={expiredCount} isLoading={overviewLoading} /> {t('dashboard:stats.expired')}
              </DashboardStatBadgeLink>
            </div>
          </StatCard>

          <StatCard title={t('dashboard:stats.authHealth24h')} icon={Activity} isLoading={overviewLoading}>
            <p className={`text-4xl font-black ${
              successRate == null ? 'text-fg'
              : successRate < 70 ? 'text-error'
              : successRate < 90 ? 'text-warning'
              : 'text-success'
            }`}>
              {successRate != null
                ? `${successRate}%`
                : <StatNum value={undefined} isLoading={overviewLoading} />}
            </p>
            <p className="text-xs text-fg-muted mt-2 font-medium">
              {successRate == null && !overviewLoading
                ? t('dashboard:stats.noTerminalOutcomes')
                : terminalTotal != null && terminalTotal > 0
                  ? t('dashboard:stats.terminalOutcomesHint', { count: terminalTotal })
                  : ''}
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <DashboardStatBadgeLink
                to={buildAuthAttemptsDrilldownUrl({ status: 'INVALID', rolling24h: true })}
                variant={(invalidCount ?? 0) > 0 ? 'error' : 'muted'}
                ariaLabel={t('dashboard:drilldown.authInvalid')}
              >
                <StatNum value={invalidCount} isLoading={overviewLoading} /> {t('dashboard:stats.invalid')}
              </DashboardStatBadgeLink>
              <DashboardStatBadgeLink
                to={buildAuthAttemptsDrilldownUrl({ status: 'EXPIRED', rolling24h: true })}
                variant={(expiredCount ?? 0) > 0 ? 'warning' : 'muted'}
                ariaLabel={t('dashboard:drilldown.authExpired')}
              >
                <StatNum value={expiredCount} isLoading={overviewLoading} /> {t('dashboard:stats.expired')}
              </DashboardStatBadgeLink>
              <DashboardStatBadgeLink
                to={buildAuthAttemptsDrilldownUrl({ status: 'REJECTED', rolling24h: true })}
                variant={(rejectedDenyCount ?? 0) > 0 ? 'warning' : 'muted'}
                ariaLabel={t('dashboard:drilldown.authRejected')}
              >
                <StatNum value={rejectedDenyCount} isLoading={overviewLoading} /> {t('dashboard:stats.denied')}
              </DashboardStatBadgeLink>
            </div>
          </StatCard>
        </div>

        {/* Activity + Quick Actions */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

          {/* Recent Activity */}
          <div className="lg:col-span-2">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>{t('dashboard:recentActivity.title')}</CardTitle>
                  <FileText className="size-4 text-fg-muted" />
                </div>
              </CardHeader>
              <CardContent>
                {overviewLoading ? (
                  <div className="py-6 flex justify-center">
                    <span className="size-5 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
                  </div>
                ) : recentLogs.length === 0 ? (
                  <p className="text-sm text-fg-muted italic py-4 text-center">{t('dashboard:recentActivity.empty')}</p>
                ) : (
                  <>
                    <div className="w-full overflow-x-auto border-2 border-fg">
                      <table className="w-full text-sm border-collapse">
                        <thead>
                          <tr className="bg-fg text-surface">
                            <th className="px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider">
                              {t('audit-logs:list.columns.event')}
                            </th>
                            <th className="px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider">
                              {t('audit-logs:list.columns.status')}
                            </th>
                            <th className="px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider">
                              {t('audit-logs:list.columns.api')}
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {recentLogs.map((log, i) => (
                            <tr
                              key={log.auditLogId ?? `activity-${i}`}
                              className="border-b border-fg/10 bg-surface even:bg-bg last:border-0"
                            >
                              <td className="px-3 py-2">
                                <span className="font-mono text-xs">
                                  {getAuditEventTypeLabel(log.eventType ?? undefined, t)}
                                </span>
                              </td>
                              <td className="px-3 py-2">
                                <EventStatusBadge status={log.eventStatus as 'SUCCESS' | 'FAILURE' | 'ERROR'} />
                              </td>
                              <td className="px-3 py-2">
                                {log.apiName ? (
                                  <Badge variant="muted">{log.apiName.replace('_API', '')}</Badge>
                                ) : (
                                  <span className="text-fg-muted">—</span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    <Link
                      to="/audit-logs"
                      className="mt-3 inline-block text-sm font-bold text-accent hover:underline"
                    >
                      {t('dashboard:recentActivity.viewAll')}
                    </Link>
                  </>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Quick Actions */}
          <Card>
            <CardHeader>
              <CardTitle>{t('dashboard:quickActions.title')}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {quickActions.map(({ labelKey, descKey, to, icon: Icon }) => (
                  <Link
                    key={to}
                    to={to}
                    className="flex items-start gap-3 p-2.5 border-2 border-fg/20 hover:border-fg hover:shadow-brutal bg-bg transition-all duration-100 group"
                  >
                    <Icon className="size-4 text-fg-muted mt-0.5 shrink-0 group-hover:text-accent transition-colors" />
                    <div>
                      <p className="text-sm font-bold text-fg leading-tight">{t(`dashboard:quickActions.${labelKey}`)}</p>
                      <p className="text-xs text-fg-muted">{t(`dashboard:quickActions.${descKey}`)}</p>
                    </div>
                  </Link>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </AppShell>
  );
}
