import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/context/auth-context';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { usePublicInstanceInfo } from '@/hooks/use-public-instance-info';
import { isDemoMode } from '@/lib/demo-mode';
import { cn } from '@/lib/utils';
import { Dialog } from '@/components/ui/dialog';
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

function getAboutProductIntroKey(adminType: string | undefined): 'about.productIntroGlobal' | 'about.productIntroTenant' {
  return adminType === 'GLOBAL_ADMIN' ? 'about.productIntroGlobal' : 'about.productIntroTenant';
}

export function Sidebar() {
  const { t } = useTranslation('layout');
  const { pathname } = useLocation();
  const { session } = useAuth();
  const { toggleSessionDemo } = useDemoModeSession();
  const { data: publicInstanceInfo } = usePublicInstanceInfo();
  const [aboutOpen, setAboutOpen] = useState(false);

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
        <div className="flex items-center gap-3">
          <img
            src="/logo-sidebar.svg"
            alt=""
            className="size-10 shrink-0"
            width={40}
            height={40}
          />
          <div>
            <p className="text-[10px] font-black uppercase tracking-[0.25em] text-sidebar-active">
              {t('brand')}
            </p>
            <p className="text-sm font-bold text-sidebar-fg mt-0.5 leading-tight">
              {t(getAdminTaglineKey(session?.adminType))}
            </p>
            {publicInstanceInfo?.instanceName ? (
              <p className="text-[10px] font-semibold text-sidebar-fg/80 mt-1 leading-tight">
                {publicInstanceInfo.instanceName}
              </p>
            ) : null}
          </div>
        </div>
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
      <div className="px-4 py-3 border-t-2 border-white/10 space-y-1">
        <button
          type="button"
          onClick={() => setAboutOpen(true)}
          className="text-[10px] font-medium text-sidebar-fg/60 hover:text-sidebar-fg transition-colors"
        >
          {t('sidebar.about')}
        </button>
        <p className="text-[10px] text-sidebar-fg/25 font-mono tracking-wide">v0.1.0</p>
      </div>

      <Dialog open={aboutOpen} onClose={() => setAboutOpen(false)} title={t('about.title')} size="md">
        <div className="space-y-4 text-sm text-fg">
          <img src="/logo.svg" alt="" className="mx-auto" width={64} height={64} />
          {publicInstanceInfo?.instanceName?.trim() ? (
            <p className="font-semibold text-center text-fg">{publicInstanceInfo.instanceName}</p>
          ) : null}
          {publicInstanceInfo?.instanceDescription?.trim() ? (
            <p className="text-center text-xs text-fg-muted leading-relaxed">
              {publicInstanceInfo.instanceDescription}
            </p>
          ) : null}
          <div
            className="border-2 border-[#3076df] bg-bg/40 p-3"
            aria-label={t('about.productSummary')}
          >
            <p className="text-xs leading-relaxed">{t(getAboutProductIntroKey(session?.adminType))}</p>
          </div>
          <p className="text-center">
            <a
              href={publicInstanceInfo?.aboutUrl?.trim() || t('about.learnMoreDefaultUrl')}
              target="_blank"
              rel="noopener noreferrer"
              className="underline text-sidebar-bg hover:opacity-80"
            >
              {t('about.learnMore')}
            </a>
          </p>
        </div>
      </Dialog>
    </aside>
  );
}
