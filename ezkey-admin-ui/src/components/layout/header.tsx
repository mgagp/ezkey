import { CircleHelp, LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/context/auth-context';
import { useHelp } from '@/context/help-context';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';
import { logout as logoutApi } from '@/generated/admin-api/admin-authentication/admin-authentication';
import { I18N_STORAGE_KEY } from '@/i18n';
import { isDemoMode } from '@/lib/demo-mode';

interface HeaderProps {
  title: string;
}

export function Header({ title }: HeaderProps) {
  const { t, i18n } = useTranslation(['layout', 'common', 'help']);
  const { session, logout } = useAuth();
  const { openHelp } = useHelp();
  const { sessionDemoOn } = useDemoModeSession();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await logoutApi();
    } catch {
      // Best effort: clear client session even if server logout fails
    }
    logout();
    navigate('/login', { replace: true });
  };

  const setLanguage = (lng: 'en' | 'fr') => {
    i18n.changeLanguage(lng);
    window.localStorage.setItem(I18N_STORAGE_KEY, lng);
  };

  return (
    <header className="h-13 shrink-0 border-b-2 border-fg bg-surface flex items-center justify-between px-6">
      <div className="flex items-center gap-3">
        <h1 className="text-xs font-black uppercase tracking-[0.2em] text-fg">{title}</h1>
        {isDemoMode && sessionDemoOn && (
          <span className="text-[10px] px-2 py-0.5 bg-accent text-surface font-black uppercase tracking-widest rounded-sm">
            {t('layout:header.demoBadge')}
          </span>
        )}
      </div>
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={openHelp}
          className="p-1.5 hover:bg-fg/10 transition-colors"
          aria-label={t('help:drawer.openHelp')}
          title={t('help:drawer.openHelp')}
        >
          <CircleHelp className="size-4" />
        </button>
        <div className="flex items-center gap-1 border-r-2 border-fg/20 pr-3 mr-1">
          <button
            type="button"
            onClick={() => setLanguage('en')}
            className={i18n.language.startsWith('en') ? 'text-sm font-bold text-fg' : 'text-sm text-fg-muted hover:text-fg'}
            aria-label="English"
          >
            EN
          </button>
          <span className="text-fg/30">|</span>
          <button
            type="button"
            onClick={() => setLanguage('fr')}
            className={i18n.language.startsWith('fr') ? 'text-sm font-bold text-fg' : 'text-sm text-fg-muted hover:text-fg'}
            aria-label="Français"
          >
            FR
          </button>
        </div>
        {session && (
          <div className="flex items-center gap-2 text-sm">
            <span className="text-fg-muted text-xs">{t('layout:header.signedInAs')}</span>
            <span className="font-bold text-fg text-sm">{session.username}</span>
            <span className="text-[10px] px-1.5 py-0.5 bg-fg text-surface font-black uppercase tracking-widest">
              {session.adminType.replace('_ADMIN', '')}
            </span>
          </div>
        )}
        <Button variant="ghost" size="sm" onClick={handleLogout} className="gap-1.5">
          <LogOut className="size-3.5" />
          {t('common:buttons.logout')}
        </Button>
      </div>
    </header>
  );
}
