import type { ReactNode } from 'react';
import { Header } from './header';
import { Sidebar } from './sidebar';

interface AppShellProps {
  title: string;
  children: ReactNode;
}

/**
 * Main layout wrapper — sidebar + header + scrollable content area.
 * Use on every authenticated page.
 */
export function AppShell({ title, children }: AppShellProps) {
  return (
    <div className="flex h-screen overflow-hidden bg-bg">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <Header title={title} />
        <main className="flex-1 overflow-y-auto p-6">{children}</main>
      </div>
    </div>
  );
}
