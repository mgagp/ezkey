import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { Header } from './header';
import { Sidebar } from './sidebar';

export interface BreadcrumbItem {
  label: string;
  path: string;
}

interface AppShellProps {
  title: string;
  breadcrumb?: BreadcrumbItem[];
  children: ReactNode;
}

/**
 * Main layout wrapper — sidebar + header + scrollable content area.
 * Use on every authenticated page.
 */
export function AppShell({ title, breadcrumb, children }: AppShellProps) {
  return (
    <div className="flex h-screen overflow-hidden bg-bg">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <Header title={title} />
        <main className="flex-1 overflow-y-auto p-6">
          {breadcrumb && breadcrumb.length > 0 && (
            <nav className="flex items-center gap-1.5 text-xs text-fg-muted mb-4" aria-label="Breadcrumb">
              {breadcrumb.map((crumb, i) => (
                <span key={crumb.path} className="flex items-center gap-1.5">
                  {i > 0 && <ChevronRight className="size-3 text-fg-muted/50" />}
                  <Link to={crumb.path} className="hover:text-fg transition-colors font-medium">
                    {crumb.label}
                  </Link>
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
