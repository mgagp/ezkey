import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import { FileText, Key, Puzzle, ShieldCheck, TrendingUp, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { AppShell } from '@/components/layout/app-shell';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { api } from '@/lib/api-client';
import { formatRelativeTime } from '@/lib/utils';
import type { AuditLogResponseDto } from '@/generated/admin-api/model';
import type { PageResponse } from '@/hooks/use-paginated-orval';

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
  const since24h = useMemo(() => {
    const d = new Date();
    d.setHours(d.getHours() - 24);
    return d.toISOString();
  }, []);

  type StatsPage = PageResponse<object>;

  const results = useQueries({
    queries: [
      // 0 — integrations total
      { queryKey: ['stats', 'int-total'], queryFn: () => api.get<StatsPage>('/api/v1/integrations?size=1'), staleTime: 60_000, refetchInterval: 60_000 },
      // 1 — integrations active
      { queryKey: ['stats', 'int-active'], queryFn: () => api.get<StatsPage>('/api/v1/integrations?size=1&active=true'), staleTime: 60_000, refetchInterval: 60_000 },
      // 2 — enrollments total
      { queryKey: ['stats', 'enr-total'], queryFn: () => api.get<StatsPage>('/api/v1/enrollments?size=1'), staleTime: 60_000, refetchInterval: 60_000 },
      // 3 — enrollments verified
      { queryKey: ['stats', 'enr-verified'], queryFn: () => api.get<StatsPage>('/api/v1/enrollments?size=1&status=VERIFIED'), staleTime: 60_000, refetchInterval: 60_000 },
      // 4 — enrollments bound
      { queryKey: ['stats', 'enr-bound'], queryFn: () => api.get<StatsPage>('/api/v1/enrollments?size=1&status=BOUND'), staleTime: 60_000, refetchInterval: 60_000 },
      // 5 — enrollments created (not yet bound)
      { queryKey: ['stats', 'enr-created'], queryFn: () => api.get<StatsPage>('/api/v1/enrollments?size=1&status=CREATED'), staleTime: 60_000, refetchInterval: 60_000 },
      // 6 — auth attempts total (24h)
      { queryKey: ['stats', 'auth-total', since24h], queryFn: () => api.get<StatsPage>(`/api/v1/auth-attempts?size=1&createdAfter=${encodeURIComponent(since24h)}`), staleTime: 30_000, refetchInterval: 30_000 },
      // 7 — auth attempts accepted (24h)
      { queryKey: ['stats', 'auth-accepted', since24h], queryFn: () => api.get<StatsPage>(`/api/v1/auth-attempts?size=1&status=ACCEPTED&createdAfter=${encodeURIComponent(since24h)}`), staleTime: 30_000, refetchInterval: 30_000 },
      // 8 — auth attempts rejected (24h)
      { queryKey: ['stats', 'auth-rejected', since24h], queryFn: () => api.get<StatsPage>(`/api/v1/auth-attempts?size=1&status=REJECTED&createdAfter=${encodeURIComponent(since24h)}`), staleTime: 30_000, refetchInterval: 30_000 },
      // 9 — pending auth attempts (live, short stale)
      { queryKey: ['stats', 'auth-pending'], queryFn: () => api.get<StatsPage>('/api/v1/auth-attempts?size=1&status=PENDING'), staleTime: 10_000, refetchInterval: 10_000 },
      // 10 — recent audit logs
      { queryKey: ['stats', 'recent-activity'], queryFn: () => api.get<PageResponse<AuditLogResponseDto>>('/api/v1/audit-logs?size=5&sort=createdAt,DESC'), staleTime: 30_000, refetchInterval: 30_000 },
    ],
  });

  const intTotal = results[0].data?.page?.totalElements;
  const intActive = results[1].data?.page?.totalElements;
  const intInactive = intTotal !== undefined && intActive !== undefined ? intTotal - intActive : undefined;

  const enrTotal = results[2].data?.page?.totalElements;
  const enrVerified = results[3].data?.page?.totalElements;
  const enrBound = results[4].data?.page?.totalElements;
  const enrCreated = results[5].data?.page?.totalElements;

  const authTotal = results[6].data?.page?.totalElements;
  const authAccepted = results[7].data?.page?.totalElements;
  const authRejected = results[8].data?.page?.totalElements;
  const authPending = results[9].data?.page?.totalElements;

  const failureRate =
    authTotal && authTotal > 0 && authRejected !== undefined
      ? Math.round((authRejected / authTotal) * 100)
      : undefined;

  const recentLogs = (results[10].data as PageResponse<AuditLogResponseDto> | undefined)?.content ?? [];

  return (
    <AppShell title="Dashboard">
      <div className="space-y-6">

        {/* Stats Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">

          <StatCard title="Integrations" icon={Puzzle} isLoading={results[0].isLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={intTotal} isLoading={results[0].isLoading} />
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Badge variant="success"><StatNum value={intActive} isLoading={results[1].isLoading} /> active</Badge>
              <Badge variant="muted"><StatNum value={intInactive} isLoading={results[0].isLoading} /> inactive</Badge>
            </div>
          </StatCard>

          <StatCard title="Enrollments" icon={Users} isLoading={results[2].isLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={enrTotal} isLoading={results[2].isLoading} />
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Badge variant="success"><StatNum value={enrVerified} isLoading={results[3].isLoading} /> verified</Badge>
              <Badge variant="warning"><StatNum value={enrBound} isLoading={results[4].isLoading} /> bound</Badge>
              <Badge variant="muted"><StatNum value={enrCreated} isLoading={results[5].isLoading} /> created</Badge>
            </div>
          </StatCard>

          <StatCard title="Auth Attempts (24h)" icon={ShieldCheck} isLoading={results[6].isLoading}>
            <p className="text-4xl font-black text-fg">
              <StatNum value={authTotal} isLoading={results[6].isLoading} />
            </p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Badge variant="success"><StatNum value={authAccepted} isLoading={results[7].isLoading} /> accepted</Badge>
              <Badge variant="error"><StatNum value={authRejected} isLoading={results[8].isLoading} /> rejected</Badge>
              {(authPending ?? 0) > 0 && (
                <Badge variant="warning"><StatNum value={authPending} isLoading={results[9].isLoading} /> pending</Badge>
              )}
            </div>
          </StatCard>

          <StatCard title="Failure Rate (24h)" icon={TrendingUp} isLoading={results[6].isLoading}>
            <p className={`text-4xl font-black ${
              failureRate === undefined ? 'text-fg'
              : failureRate > 20 ? 'text-error'
              : failureRate > 10 ? 'text-warning'
              : 'text-success'
            }`}>
              {failureRate !== undefined ? `${failureRate}%` : <StatNum value={undefined} isLoading={results[6].isLoading} />}
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
                {results[10].isLoading ? (
                  <div className="py-6 flex justify-center">
                    <span className="size-5 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
                  </div>
                ) : recentLogs.length === 0 ? (
                  <p className="text-sm text-fg-muted italic py-4 text-center">No recent activity.</p>
                ) : (
                  <div>
                    {recentLogs.map((log) => (
                      <div
                        key={log.auditLogId}
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
