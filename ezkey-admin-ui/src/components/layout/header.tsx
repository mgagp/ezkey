import { CircleHelp, User } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useHelp } from '@/context/use-help';
import { useAuth } from '@/context/use-auth';
import { useDemoModeSession } from '@/context/use-demo-mode-session';
import { usePublicInstanceInfo } from '@/hooks/use-public-instance-info';
import { I18N_STORAGE_KEY } from '@/i18n';
import { isDemoMode } from '@/lib/demo-mode';
import { DisplayTimezoneMenu } from '@/components/layout/account-timezone-menu';
import { HeaderLogoutButton } from '@/components/layout/header-logout-button';

interface HeaderProps {
  title: string;
}

export function Header({ title }: HeaderProps) {
  const { t, i18n } = useTranslation(['layout', 'common', 'help']);
  const { session } = useAuth();
  const { openHelp } = useHelp();
  const { sessionDemoOn } = useDemoModeSession();
  const { data: publicInstanceInfo } = usePublicInstanceInfo();

  const setLanguage = (lng: 'en' | 'fr') => {
    i18n.changeLanguage(lng);
    window.localStorage.setItem(I18N_STORAGE_KEY, lng);
  };

  const showInstanceBanner =
    publicInstanceInfo &&
    (publicInstanceInfo.instanceName?.trim() || publicInstanceInfo.instanceDescription?.trim());

  return (
    <header data-testid="app-header" className="h-13 shrink-0 border-b-2 border-fg bg-surface flex items-center gap-4 px-4 sm:px-6 min-h-13">
      <div className="flex items-center gap-3 shrink-0 min-w-0">
        <h1 className="text-xs font-black uppercase tracking-[0.2em] text-fg truncate">{title}</h1>
        {isDemoMode && sessionDemoOn && (
          <span className="text-[10px] px-2 py-0.5 bg-accent text-surface font-black uppercase tracking-widest rounded-sm shrink-0">
            {t('layout:header.demoBadge')}
          </span>
        )}
      </div>
      {showInstanceBanner && (
        <div
          className="flex-1 min-w-0 flex flex-col items-center justify-center text-center px-2 border-x-2 border-fg/15"
          aria-label={t('layout:header.instanceContext')}
        >
          {publicInstanceInfo?.instanceName?.trim() ? (
            <p className="text-xs font-bold text-fg leading-tight truncate max-w-full">
              {publicInstanceInfo.instanceName}
            </p>
          ) : null}
          {publicInstanceInfo?.instanceDescription?.trim() ? (
            <p className="text-[10px] text-fg-muted leading-snug line-clamp-2 max-w-2xl mt-0.5">
              {publicInstanceInfo.instanceDescription}
            </p>
          ) : null}
        </div>
      )}
      <div className="flex items-center gap-2 sm:gap-3 shrink-0 ml-auto">
        <button
          type="button"
          onClick={() => openHelp()}
          className="p-1.5 hover:bg-fg/10 transition-colors"
          data-testid="app-help-button"
          aria-label={t('help:drawer.openHelp')}
          title={t('help:drawer.openHelp')}
        >
          <CircleHelp className="size-4" />
        </button>
        <div className="flex items-center gap-1 border-r-2 border-fg/20 pr-3 mr-1">
          <button
            type="button"
            onClick={() => setLanguage('en')}
            data-testid="app-language-en"
            className={i18n.language.startsWith('en') ? 'text-sm font-bold text-fg' : 'text-sm text-fg-muted hover:text-fg'}
            aria-label={t('common:language.english')}
          >
            EN
          </button>
          <span className="text-fg/30">|</span>
          <button
            type="button"
            onClick={() => setLanguage('fr')}
            data-testid="app-language-fr"
            className={i18n.language.startsWith('fr') ? 'text-sm font-bold text-fg' : 'text-sm text-fg-muted hover:text-fg'}
            aria-label={t('common:language.french')}
          >
            FR
          </button>
        </div>
        {session && (
          <div className="flex items-center gap-2 sm:gap-3 flex-wrap justify-end text-sm min-w-0">
            <span className="text-fg-muted text-xs hidden sm:inline shrink-0">{t('layout:header.signedInAs')}</span>
            <div className="flex items-center gap-1.5 min-w-0 max-w-[min(100%,22rem)]">
              <User className="size-3.5 shrink-0 text-fg-muted" aria-hidden />
              <span className="font-bold text-fg truncate">{session.username}</span>
              <span className="text-[10px] px-1.5 py-0.5 bg-fg text-surface font-black uppercase tracking-widest shrink-0">
                {session.adminType.replace('_ADMIN', '')}
              </span>
              {session.tenantName ? (
                <span
                  className="text-xs text-fg-muted truncate max-w-[10rem]"
                  title={session.tenantName}
                  data-testid="app-header-tenant-name"
                >
                  {session.tenantName}
                </span>
              ) : null}
            </div>
            <DisplayTimezoneMenu />
            <HeaderLogoutButton />
          </div>
        )}
      </div>
    </header>
  );
}
