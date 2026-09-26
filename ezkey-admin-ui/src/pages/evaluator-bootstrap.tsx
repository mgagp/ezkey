import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/context/use-auth';
import { Alert } from '@/components/ui/alert';
import { fetchApi } from '@/lib/api-client';
import {
  establishBootstrapAuthSession,
  takeEvaluatorBootstrapHandoff,
} from '@/lib/evaluator-bootstrap-session';
import { isBrowserSessionCookieBuild, type AuthSession } from '@/lib/auth';

/**
 * Landing route after evaluator signup auto-redirect or onboarding-resume redeem.
 *
 * Mode B: restores via `/me` + HttpOnly cookie. Mode A: consumes sessionStorage handoff
 * (token never placed in the URL).
 */
export default function EvaluatorBootstrapPage() {
  const { t } = useTranslation(['login']);
  const navigate = useNavigate();
  const { login } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      try {
        const handoff = takeEvaluatorBootstrapHandoff();
        if (handoff?.activationCode) {
          sessionStorage.setItem(
            'ezkey_evaluator_activation_code_prefill',
            handoff.activationCode,
          );
        }
        if (handoff?.adminId != null) {
          sessionStorage.setItem(
            'ezkey_evaluator_resume_admin_id',
            String(handoff.adminId),
          );
        }

        if (isBrowserSessionCookieBuild()) {
          const restored = await fetchApi<AuthSession>('/api/v1/admin/auth/me', {
            method: 'GET',
            requireAuth: false,
          });
          if (cancelled) return;
          const session: AuthSession = {
            ...restored,
            tokenPurpose: restored.tokenPurpose ?? 'BOOTSTRAP',
          };
          login(session);
          navigate('/evaluator-onboarding', { replace: true });
          return;
        }

        if (handoff?.sessionToken && handoff.sessionExpiresAt && handoff.username) {
          const session = establishBootstrapAuthSession({
            sessionToken: handoff.sessionToken,
            sessionExpiresAt: handoff.sessionExpiresAt,
            username: handoff.username,
            adminType: handoff.adminType,
            adminId: handoff.adminId,
            lifecycleStatus: handoff.adminId != null ? 'ACTIVE' : 'PENDING_ACTIVATION',
          });
          if (cancelled) return;
          login(session);
          navigate('/evaluator-onboarding', { replace: true });
          return;
        }

        setError(t('login:bootstrap.handoffMissing'));
      } catch {
        if (!cancelled) {
          setError(t('login:bootstrap.handoffFailed'));
        }
      }
    };

    void run();
    return () => {
      cancelled = true;
    };
  }, [login, navigate, t]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg p-6">
      <div className="max-w-md w-full space-y-4">
        {error ? (
          <Alert variant="error">{error}</Alert>
        ) : (
          <p className="text-sm text-fg-muted text-center">{t('login:bootstrap.establishing')}</p>
        )}
      </div>
    </div>
  );
}
