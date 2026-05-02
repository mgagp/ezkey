import { LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/use-auth';
import { logout as logoutApi } from '@/generated/admin-api/admin-authentication/admin-authentication';

/**
 * Primary header action: icon + label, always visible (not behind a menu) so logout stays a clear habit.
 */
export function HeaderLogoutButton() {
  const { t } = useTranslation('common');
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  if (!session) return null;

  const handleLogout = async () => {
    try {
      await logoutApi();
    } catch {
      // Best effort
    }
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <button
      type="button"
      data-testid="app-logout-button"
      onClick={handleLogout}
      className="inline-flex items-center gap-1.5 rounded-sm px-2 py-1.5 text-sm font-bold text-fg hover:bg-fg/10 border-2 border-transparent hover:border-fg/20 transition-colors shrink-0"
    >
      <LogOut className="size-3.5 shrink-0 text-fg-muted" aria-hidden />
      {t('buttons.logout')}
    </button>
  );
}
