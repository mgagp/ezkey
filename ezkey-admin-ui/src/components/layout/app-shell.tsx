import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/context/use-auth';
import { isBootstrapSession } from '@/lib/evaluator-bootstrap-session';
import { Header } from './header';
import { Sidebar } from './sidebar';

export interface BreadcrumbItem {
  label: string;
  /** When omitted, the segment is rendered as plain text (non-clickable). */
  path?: string;
}

interface AppShellProps {
  title: string;
  breadcrumb?: BreadcrumbItem[];
  /** Optional row above the breadcrumb (e.g. list prev/next navigation). */
  detailNav?: ReactNode;
  children: ReactNode;
}

/**
 * Main layout wrapper — sidebar + header + scrollable content area.
 * Use on every authenticated page.
 */
export function AppShell({ title, breadcrumb, detailNav, children }: AppShellProps) {
  const { t } = useTranslation(['layout']);
  const { session } = useAuth();
  const showBootstrapHonesty =
    isBootstrapSession(session) || session?.lifecycleStatus === 'PENDING_ACTIVATION';

  return (
    <div data-testid="app-shell" className="flex h-screen overflow-hidden bg-bg">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <Header title={title} />
        <main data-testid="app-main" className="flex-1 overflow-y-auto p-6">
          {showBootstrapHonesty && (
            <div
              data-testid="bootstrap-incomplete-enrollment-banner"
              className="mb-4 border-2 border-fg bg-surface px-3 py-2 text-sm text-fg"
              role="status"
            >
              {t('layout:bootstrap.incompleteEnrollmentBanner')}{' '}
              <Link
                to="/evaluator-onboarding"
                className="font-bold underline underline-offset-2"
              >
                {t('layout:bootstrap.completeEnrollmentLink')}
              </Link>
            </div>
          )}
          {detailNav}
          {breadcrumb && breadcrumb.length > 0 && (
            <nav className="flex items-center gap-1.5 text-xs text-fg-muted mb-4" aria-label="Breadcrumb">
              {breadcrumb.map((crumb, i) => (
                <span key={crumb.path ?? `crumb-${i}`} className="flex items-center gap-1.5">
                  {i > 0 && <ChevronRight className="size-3 text-fg-muted/50" />}
                  {crumb.path !== undefined ? (
                    <Link to={crumb.path} className="hover:text-fg transition-colors font-medium">
                      {crumb.label}
                    </Link>
                  ) : (
                    <span className="font-medium text-fg-muted">{crumb.label}</span>
                  )}
                </span>
              ))}
              <ChevronRight className="size-3 text-fg-muted/50" />
              <span className="text-fg font-bold">{title}</span>
            </nav>
          )}
          {children}
        </main>
      </div>
    </div>
  );
}
