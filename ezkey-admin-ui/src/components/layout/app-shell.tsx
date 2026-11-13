import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/context/use-auth';
import { formatDate } from '@/lib/utils';
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
  const isTemporarySession = session?.tokenPurpose === 'EVALUATOR_TEMP';
  const bindHref =
    session?.adminId != null ? `/admins?adminId=${session.adminId}` : '/admins';

  return (
    <div data-testid="app-shell" className="flex h-screen overflow-hidden bg-bg">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <Header title={title} />
        <main data-testid="app-main" className="flex-1 overflow-y-auto p-6">
          {isTemporarySession && (
            <div
              role="status"
              className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-1 border-2 border-blue-500 bg-blue-50 p-3 text-sm text-blue-800"
              data-testid="temporary-session-banner"
            >
              <span>
                {t('layout:temporarySession.banner', {
                  expiresAt: session?.expiresAt ? formatDate(session.expiresAt) : '—',
                })}
              </span>
              <Link
                to={bindHref}
                className="underline font-semibold hover:text-accent"
                data-testid="temporary-session-bind-link"
              >
                {t('layout:temporarySession.bindLink')}
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
