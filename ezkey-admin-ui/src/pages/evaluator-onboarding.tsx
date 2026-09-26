import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/app-shell';
import { LoginActivationSection } from '@/components/feature/login-activation-section';
import { usePublicInstanceInfo } from '@/hooks/use-public-instance-info';
import { useAuth } from '@/context/use-auth';

/**
 * Minimal evaluator onboarding under a BOOTSTRAP session — activation + QR.
 * Honesty copy lives once in AppShell (no duplicate page Alert).
 */
export default function EvaluatorOnboardingPage() {
  const { t } = useTranslation(['login']);
  const { session } = useAuth();
  const { data: publicInstanceInfo } = usePublicInstanceInfo();

  return (
    <AppShell title={t('login:bootstrap.onboardingTitle')}>
      <div className="max-w-xl space-y-4">
        <p className="text-sm text-fg-muted">
          {t('login:bootstrap.onboardingHint', { username: session?.username ?? '' })}
        </p>
        <LoginActivationSection
          onBackToPasswordless={() => undefined}
          authApiPublicBaseUrl={publicInstanceInfo?.authApiPublicBaseUrl}
          hideBackLink
          initialActivationCode={
            typeof sessionStorage !== 'undefined'
              ? sessionStorage.getItem('ezkey_evaluator_activation_code_prefill') ?? undefined
              : undefined
          }
        />
        <p className="text-xs text-fg-muted">
          <Link to="/login" className="underline font-medium text-fg">
            {t('login:bootstrap.afterBindLogin')}
          </Link>
        </p>
      </div>
    </AppShell>
  );
}
