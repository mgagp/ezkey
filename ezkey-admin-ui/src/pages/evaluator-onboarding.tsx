import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/app-shell';
import { LoginActivationSection } from '@/components/feature/login-activation-section';
import { usePublicInstanceInfo } from '@/hooks/use-public-instance-info';
import { useAuth } from '@/context/use-auth';

interface EnrollmentPrefill {
  username?: string;
  enrollmentId?: number;
  enrollmentProofToken?: string;
  enrollmentChallenge?: number | null;
}

function readEnrollmentPrefill(): EnrollmentPrefill | null {
  if (typeof sessionStorage === 'undefined') {
    return null;
  }
  try {
    const raw = sessionStorage.getItem('ezkey_evaluator_enrollment_prefill');
    if (!raw) return null;
    sessionStorage.removeItem('ezkey_evaluator_enrollment_prefill');
    return JSON.parse(raw) as EnrollmentPrefill;
  } catch {
    sessionStorage.removeItem('ezkey_evaluator_enrollment_prefill');
    return null;
  }
}

/**
 * Minimal evaluator onboarding under a BOOTSTRAP session — activation + QR.
 * Honesty copy lives once in AppShell (no duplicate page Alert).
 */
export default function EvaluatorOnboardingPage() {
  const { t } = useTranslation(['login']);
  const { session } = useAuth();
  const { data: publicInstanceInfo } = usePublicInstanceInfo();

  const enrollmentPrefill = useMemo(() => readEnrollmentPrefill(), []);
  const initialEnrollmentResult =
    enrollmentPrefill?.enrollmentProofToken != null && enrollmentPrefill.enrollmentId != null
      ? {
          success: true,
          username: enrollmentPrefill.username ?? session?.username,
          enrollmentId: enrollmentPrefill.enrollmentId,
          enrollmentProofToken: enrollmentPrefill.enrollmentProofToken,
          enrollmentChallenge: enrollmentPrefill.enrollmentChallenge ?? undefined,
        }
      : null;

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
          initialEnrollmentResult={initialEnrollmentResult}
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
