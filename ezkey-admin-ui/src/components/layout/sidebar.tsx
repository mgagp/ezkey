import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/context/auth-context';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { isDemoMode } from '@/lib/demo-mode';
import { cn } from '@/lib/utils';
import type { AdminResponseDtoAdminType } from '@/generated/admin-api/model';
import {
  Building2,
  FileText,
  Key,
  KeyRound,
  LayoutDashboard,
  Puzzle,
  ShieldCheck,
  UserCog,
  Users,
} from 'lucide-react';

type NavLabelKey =
  | 'dashboard'
  | 'tenants'
  | 'integrations'
  | 'enrollments'
  | 'authAttempts'
  | 'auditLogs'
  | 'admins'
  | 'apiKeys'
  | 'encryptionKeys';

interface NavItem {
  labelKey: NavLabelKey;
  path: string;
  icon: typeof LayoutDashboard;
  /** If set, the item is only shown when the logged-in admin has one of these roles. */
  roles?: AdminResponseDtoAdminType[];
}

const navItems: NavItem[] = [
  { labelKey: 'dashboard', path: '/dashboard', icon: LayoutDashboard },
  { labelKey: 'tenants', path: '/tenants', icon: Building2, roles: ['GLOBAL_ADMIN'] },
  { labelKey: 'integrations', path: '/integrations', icon: Puzzle },
  { labelKey: 'enrollments', path: '/enrollments', icon: Users },
  { labelKey: 'authAttempts', path: '/auth-attempts', icon: ShieldCheck },
  { labelKey: 'auditLogs', path: '/audit-logs', icon: FileText },
  { labelKey: 'admins', path: '/admins', icon: UserCog },
  { labelKey: 'apiKeys', path: '/api-keys', icon: Key },
  { labelKey: 'encryptionKeys', path: '/encryption-keys', icon: KeyRound, roles: ['GLOBAL_ADMIN'] },
];

function getAdminTaglineKey(adminType: string | undefined): string {
  if (adminType === 'GLOBAL_ADMIN') return 'tagline.globalAdmin';
  if (adminType === 'TENANT_ADMIN') return 'tagline.tenantAdmin';
  return 'tagline.adminConsole';
}

export function Sidebar() {
  const { t } = useTranslation('layout');
  const { pathname } = useLocation();
  const { session } = useAuth();
  const { toggleSessionDemo } = useDemoModeSession();

  const visibleItems = navItems.filter(
    (item) => !item.roles || item.roles.includes(session?.adminType as AdminResponseDtoAdminType),
  );

  const handleBrandClick = (e: React.MouseEvent) => {
    if (isDemoMode && e.ctrlKey) {
      e.preventDefault();
      toggleSessionDemo();
    }
  };

  return (
    <aside className="w-52 shrink-0 h-screen bg-sidebar-bg flex flex-col border-r-2 border-fg sticky top-0">
      {/* Brand — Ctrl+click toggles demo mode when VITE_DEMO_MODE is true */}
      <div
        className="px-4 py-5 border-b-2 border-white/10"
        role={isDemoMode ? 'button' : undefined}
        onClick={handleBrandClick}
        onKeyDown={
          isDemoMode
            ? (e) => {
                if (e.key === 'Enter' && e.ctrlKey) toggleSessionDemo();
              }
            : undefined
        }
        tabIndex={isDemoMode ? 0 : undefined}
        title={isDemoMode ? t('sidebar.demoModeTitle') : undefined}
      >
        <p className="text-[10px] font-black uppercase tracking-[0.25em] text-sidebar-active">
          {t('brand')}
        </p>
        <p className="text-sm font-bold text-sidebar-fg mt-0.5 leading-tight">
          {t(getAdminTaglineKey(session?.adminType))}
        </p>
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-3 overflow-y-auto">
        <ul className="space-y-0.5 px-2">
          {visibleItems.map(({ labelKey, path, icon: Icon }) => {
            const isActive = pathname === path || pathname.startsWith(`${path}/`);
            return (
              <li key={path}>
                <Link
                  to={path}
                  className={cn(
                    'flex items-center gap-3 px-3 py-2.5 text-sm font-medium border-l-[3px]',
                    'transition-colors duration-75',
                    isActive
                      ? 'border-l-sidebar-active text-sidebar-fg bg-white/10'
                      : 'border-l-transparent text-sidebar-fg/50 hover:text-sidebar-fg hover:bg-white/5',
                  )}
                >
                  <Icon className="size-4 shrink-0" />
                  {t(`nav.${labelKey}`)}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Footer */}
      <div className="px-4 py-3 border-t-2 border-white/10">
        <p className="text-[10px] text-sidebar-fg/25 font-mono tracking-wide">v0.1.0</p>
      </div>
    </aside>
  );
}
