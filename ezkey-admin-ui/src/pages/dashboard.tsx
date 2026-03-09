import { Link } from 'react-router-dom';
import { AlertTriangle, FileText, Key, Puzzle, ShieldCheck, TrendingUp, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { AppShell } from '@/components/layout/app-shell';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip } from '@/components/ui/tooltip';
import { DASHBOARD_AUDIT_CHAIN_HELP } from '@/lib/help-text';
import { formatRelativeTime } from '@/lib/utils';
import { useAuth } from '@/context/auth-context';
import { useGetOverview } from '@/generated/admin-api/dashboard/dashboard';
import { useGetPendingCount } from '@/generated/admin-api/auth-attempts/auth-attempts';
import type { DashboardOverviewDto } from '@/generated/admin-api/model';

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
  { label: 'New Integration', desc: 'Register an application', to: '/integrations', icon: Puzzle },
  { label: 'New Enrollment', desc: 'Enroll a user device', to: '/enrollments', icon: Users },
  { label: 'New Admin', desc: 'Add a tenant admin', to: '/admins', icon: ShieldCheck },
  { label: 'New API Key', desc: 'Create M2M credentials', to: '/api-keys', icon: Key },
];

// ── Dashboard page ─────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const { session } = useAuth();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';

  const { data: overview, isLoading: overviewLoading } = useGetOverview<DashboardOverviewDto>({
    query: { staleTime: 60_000, refetchInterval: 60_000 },
  });

  const { data: pendingResponse, isLoading: pendingLoading } = useGetPendingCount<{ count: number }>({
    query: { staleTime: 10_000, refetchInterval: 10_000 },
  });

  const intTotal = overview?.integrations?.total;
  const intActive = overview?.integrations?.active;
  const intInactive = overview?.integrations?.inactive;

  const enrTotal = overview?.enrollments?.total;
  const enrVerified = overview?.enrollments?.verified;
  const enrBound = overview?.enrollments?.bound;
  const enrCreated = overview?.enrollments?.created;

  const authTotal = overview?.auth24h?.total;
  const authAccepted = overview?.auth24h?.accepted;
  const authRejected = overview?.auth24h?.rejected;
  const failureRate = overview?.auth24h?.failureRatePct;
  const authPending = pendingResponse?.count;

  const recentLogs = overview?.recentActivity ?? [];
  const alerts = overview?.alerts ?? [];

  return (
    <AppShell title="Dashboard">
      <div className="space-y-6">

        {/* Audit chain alerts (Global Admin only) */}
        {isGlobalAdmin && alerts.length > 0 && (
          <Card className="border-2 border-error/50 bg-error/5">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2 text-error">
                  <AlertTriangle className="size-5" />
                  Audit chain alerts
                </CardTitle>
              </div>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-fg-muted mb-3">
                <Tooltip content={DASHBOARD_AUDIT_CHAIN_HELP.UNDECLARED_GAPS}>
                  <span className="underline decoration-dotted cursor-help">Undeclared gap(s)</span>
                </Tooltip>
                {' '}detected. Declare the gap in Audit Logs to restore chain continuity.
              </p>
              <ul className="space-y-2">
                {alerts.map((alert) => (
                  <li
                    key={alert.auditLogId}
                    className="flex flex-wrap items-baseline gap-2 text-sm border-b border-fg/10 pb-2 last:border-0 last:pb-0"
                  >
                    <Badge variant="error">
                      {(alert.eventType ?? '').replaceAll('_', ' ')}
                    </Badge>
                    <span className="text-fg-muted shrink-0">
                      {formatRelativeTime(alert.createdAt ?? '')}
                    </span>
                    {alert.eventDetails?.anchorCheckpointId != null && (
                      <span className="text-fg">
                        <Tooltip content={DASHBOARD_AUDIT_CHAIN_HELP.ANCHOR_CHECKPOINT}>
                          <span className="underline decoration-dotted cursor-help">Anchor checkpoint</span>
                        </Tooltip>
                        : {alert.eventDetails.anchorCheckpointId}
                        {alert.eventDetails.estimatedGapMinutes != null &&
                          ` · ~${alert.eventDetails.estimatedGapMinutes} min gap`}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
              <Link
                to="/audit-logs"
                className="mt-3 inline-block text-sm font-bold text-accent hover:underline"
              >
                Open Audit Logs →
              </Link>
            </CardContent>
          </Card>
        )}

        {/* Stats Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">

          <StatCard title="Integrations" icon={Puzzle} isLoading={overviewLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={intTotal} isLoading={overviewLoading} />
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Badge variant="success"><StatNum value={intActive} isLoading={overviewLoading} /> active</Badge>
              <Badge variant="muted"><StatNum value={intInactive} isLoading={overviewLoading} /> inactive</Badge>
            </div>
          </StatCard>

          <StatCard title="Enrollments" icon={Users} isLoading={overviewLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={enrTotal} isLoading={overviewLoading} />
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Badge variant="success"><StatNum value={enrVerified} isLoading={overviewLoading} /> verified</Badge>
              <Badge variant="warning"><StatNum value={enrBound} isLoading={overviewLoading} /> bound</Badge>
              <Badge variant="muted"><StatNum value={enrCreated} isLoading={overviewLoading} /> created</Badge>
            </div>
          </StatCard>

          <StatCard title="Auth Attempts (24h)" icon={ShieldCheck} isLoading={overviewLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={authTotal} isLoading={overviewLoading} />
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Badge variant="success"><StatNum value={authAccepted} isLoading={overviewLoading} /> accepted</Badge>
              <Badge variant="error"><StatNum value={authRejected} isLoading={overviewLoading} /> rejected</Badge>
              {(authPending ?? 0) > 0 && (
                <Badge variant="warning"><StatNum value={authPending} isLoading={pendingLoading} /> pending</Badge>
              )}
            </div>
          </StatCard>

          <StatCard title="Failure Rate (24h)" icon={TrendingUp} isLoading={overviewLoading}>
            <p className={`text-4xl font-black ${
              failureRate === undefined ? 'text-fg'
              : failureRate > 20 ? 'text-error'
              : failureRate > 10 ? 'text-warning'
              : 'text-success'
            }`}>
              {failureRate !== undefined ? `${failureRate}%` : <StatNum value={undefined} isLoading={overviewLoading} />}
            </p>
            <p className="text-xs text-fg-muted mt-2 font-medium">
              {failureRate === undefined ? '' : failureRate <= 5 ? 'Healthy' : failureRate <= 15 ? 'Monitor closely' : 'Action required'}
            </p>
          </StatCard>
        </div>

        {/* Activity + Quick Actions */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

          {/* Recent Activity */}
          <div className="lg:col-span-2">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Recent Activity</CardTitle>
                  <FileText className="size-4 text-fg-muted" />
                </div>
              </CardHeader>
              <CardContent>
                {overviewLoading ? (
                  <div className="py-6 flex justify-center">
                    <span className="size-5 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
                  </div>
                ) : recentLogs.length === 0 ? (
                  <p className="text-sm text-fg-muted italic py-4 text-center">No recent activity.</p>
                ) : (
                  <div>
                    {recentLogs.map((log, i) => (
                      <div
                        key={log.auditLogId ?? `activity-${i}`}
                        className="flex items-center gap-3 py-2.5 border-b border-fg/10 last:border-0"
                      >
                        <Badge
                          variant={
                            log.eventStatus === 'SUCCESS' ? 'success'
                            : log.eventStatus === 'FAILURE' ? 'error'
                            : 'muted'
                          }
                        >
                          {(log.eventType ?? '').replaceAll('_', ' ')}
                        </Badge>
                        <span className="text-xs text-fg-muted flex-1 truncate min-w-0">
                          {log.eventAction ?? log.apiName ?? (log.adminId ? `Admin ${log.adminId}` : '—')}
                        </span>
                        <span className="text-[10px] text-fg-muted font-mono shrink-0">
                          {formatRelativeTime(log.createdAt ?? '')}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Quick Actions */}
          <Card>
            <CardHeader>
              <CardTitle>Quick Actions</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {quickActions.map(({ label, desc, to, icon: Icon }) => (
                  <Link
                    key={to}
                    to={to}
                    className="flex items-start gap-3 p-2.5 border-2 border-fg/20 hover:border-fg hover:shadow-brutal bg-bg transition-all duration-100 group"
                  >
                    <Icon className="size-4 text-fg-muted mt-0.5 shrink-0 group-hover:text-accent transition-colors" />
                    <div>
                      <p className="text-sm font-bold text-fg leading-tight">{label}</p>
                      <p className="text-xs text-fg-muted">{desc}</p>
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
