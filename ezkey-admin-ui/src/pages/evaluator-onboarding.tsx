import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/app-shell';
import { Alert } from '@/components/ui/alert';
import { LoginActivationSection } from '@/components/feature/login-activation-section';
import { usePublicInstanceInfo } from '@/hooks/use-public-instance-info';
import { useAuth } from '@/context/use-auth';

/**
 * Minimal evaluator onboarding under a BOOTSTRAP session — activation + QR, honesty line only.
 */
export default function EvaluatorOnboardingPage() {
  const { t } = useTranslation(['login', 'layout']);
  const { session } = useAuth();
  const { data: publicInstanceInfo } = usePublicInstanceInfo();

  return (
    <AppShell title={t('login:bootstrap.onboardingTitle')}>
      <div className="max-w-xl space-y-4">
        <Alert variant="info">
          {t('layout:bootstrap.incompleteEnrollmentBanner')}
        </Alert>
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
