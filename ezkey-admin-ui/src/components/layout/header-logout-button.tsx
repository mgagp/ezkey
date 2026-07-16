import { useState } from 'react';
import { LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/use-auth';
import { useToast } from '@/context/use-toast';
import { logout as logoutApi } from '@/generated/admin-api/admin-authentication/admin-authentication';
import { isBrowserSessionCookieBuild } from '@/lib/auth';
import { mayCompleteLogoutLocallyAfterApiFailure } from '@/lib/auth-session-lifecycle';

/**
 * Primary header action: icon + label, always visible (not behind a menu) so logout stays a clear habit.
 *
 * Cookie mode (SEC-027): do not claim logout success when server revocation fails — the HttpOnly
 * session cookie cannot be cleared from JavaScript. Mode A may still wipe the local bearer.
 */
export function HeaderLogoutButton() {
  const { t } = useTranslation('common');
  const { session, logout } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);

  if (!session) return null;

  const handleLogout = async () => {
    if (pending) return;
    setPending(true);
    try {
      await logoutApi();
      logout();
      navigate('/login', { replace: true });
    } catch {
      if (mayCompleteLogoutLocallyAfterApiFailure(isBrowserSessionCookieBuild())) {
        logout();
        navigate('/login', { replace: true });
        return;
      }
      toast(t('logout.cookieRevocationFailed'), 'error');
    } finally {
      setPending(false);
    }
  };

  return (
    <button
      type="button"
      data-testid="app-logout-button"
      disabled={pending}
      aria-busy={pending}
      onClick={() => {
        void handleLogout();
      }}
      className="inline-flex items-center gap-1.5 rounded-sm px-2 py-1.5 text-sm font-bold text-fg hover:bg-fg/10 border-2 border-transparent hover:border-fg/20 transition-colors shrink-0 disabled:opacity-60 disabled:pointer-events-none"
    >
      <LogOut className="size-3.5 shrink-0 text-fg-muted" aria-hidden />
      {pending ? t('logout.inProgress') : t('buttons.logout')}
    </button>
  );
}
