import { FileText, Key, Puzzle, ShieldCheck, TrendingUp, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { AppShell } from '@/components/layout/app-shell';

// ── Stat card layout ────────────────────────────────────────────────────────

interface StatCardProps {
  title: string;
  icon: typeof Puzzle;
  children: ReactNode;
}

function StatCard({ title, icon: Icon, children }: StatCardProps) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>{title}</CardTitle>
          <Icon className="size-4 text-fg-muted" />
        </div>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

// ── Mock data — replaced with live API calls in Phase 2 ─────────────────────

const mock = {
  integrations: { total: 4, active: 3, inactive: 1 },
  enrollments: { total: 42, verified: 38, bound: 2, created: 1, invalid: 1 },
  authAttempts: { total: 128, accepted: 115, rejected: 8, pending: 5 },
  recentActivity: [
    { id: 1, type: 'AUTH_ATTEMPT_ACCEPTED', time: '2m ago', actor: 'alice.smith' },
    { id: 2, type: 'ENROLLMENT_CREATED', time: '15m ago', actor: 'bob.jones' },
    { id: 3, type: 'AUTH_ATTEMPT_REJECTED', time: '1h ago', actor: 'carol.white' },
    { id: 4, type: 'INTEGRATION_CREATED', time: '3h ago', actor: 'admin' },
    { id: 5, type: 'AUTH_ATTEMPT_ACCEPTED', time: '5h ago', actor: 'dave.brown' },
  ],
};

const quickActions = [
  { label: 'New Integration', desc: 'Register an application', to: '/integrations', icon: Puzzle },
  { label: 'New Enrollment', desc: 'Enroll a user device', to: '/enrollments', icon: Users },
  { label: 'New Admin', desc: 'Add a tenant admin', to: '/admins', icon: ShieldCheck },
  { label: 'New API Key', desc: 'Create M2M credentials', to: '/api-keys', icon: Key },
];

// ── Dashboard page ───────────────────────────────────────────────────────────

export default function DashboardPage() {
  const failureRate =
    mock.authAttempts.total > 0
      ? Math.round((mock.authAttempts.rejected / mock.authAttempts.total) * 100)
      : 0;

  return (
    <AppShell title="Dashboard">
      <div className="space-y-6">

        {/* Stats Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">

          <StatCard title="Integrations" icon={Puzzle}>
            <p className="text-4xl font-black text-fg">{mock.integrations.total}</p>
            <div className="flex gap-2 mt-2 flex-wrap">
              <Badge variant="success">{mock.integrations.active} active</Badge>
              <Badge variant="muted">{mock.integrations.inactive} inactive</Badge>
            </div>
          </StatCard>

          <StatCard title="Enrollments" icon={Users}>
            <p className="text-4xl font-black text-fg">{mock.enrollments.total}</p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Badge variant="success">{mock.enrollments.verified} verified</Badge>
              <Badge variant="warning">{mock.enrollments.bound} bound</Badge>
              <Badge variant="muted">{mock.enrollments.created} created</Badge>
            </div>
          </StatCard>

          <StatCard title="Auth Attempts (24h)" icon={ShieldCheck}>
            <p className="text-4xl font-black text-fg">{mock.authAttempts.total}</p>
            <div className="flex gap-1 mt-2 flex-wrap">
              <Badge variant="success">{mock.authAttempts.accepted} accepted</Badge>
              <Badge variant="error">{mock.authAttempts.rejected} rejected</Badge>
              {mock.authAttempts.pending > 0 && (
                <Badge variant="warning">{mock.authAttempts.pending} pending</Badge>
              )}
            </div>
          </StatCard>

          <StatCard title="Failure Rate (24h)" icon={TrendingUp}>
            <p
              className={`text-4xl font-black ${
                failureRate > 20 ? 'text-error' : failureRate > 10 ? 'text-warning' : 'text-success'
              }`}
            >
              {failureRate}%
            </p>
            <p className="text-xs text-fg-muted mt-2 font-medium">
              {failureRate <= 5 ? 'Healthy' : failureRate <= 15 ? 'Monitor closely' : 'Action required'}
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
                <div>
                  {mock.recentActivity.map((item) => (
                    <div
                      key={item.id}
                      className="flex items-center justify-between py-2.5 border-b border-fg/10 last:border-0"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <Badge
                          variant={
                            item.type.includes('ACCEPTED')
                              ? 'success'
                              : item.type.includes('REJECTED')
                                ? 'error'
                                : 'muted'
                          }
                        >
                          {item.type.replaceAll('_', ' ')}
                        </Badge>
                        <span className="text-xs text-fg-muted truncate">{item.actor}</span>
                      </div>
                      <span className="text-[10px] text-fg-muted font-mono shrink-0 ml-2">
                        {item.time}
                      </span>
                    </div>
                  ))}
                </div>
                <p className="text-[10px] text-fg-muted mt-3 italic">
                  Mock data — live audit logs in Phase 2
                </p>
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
