import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/app-shell';
import { LoginActivationSection } from '@/components/feature/login-activation-section';
import { usePublicInstanceInfo } from '@/hooks/use-public-instance-info';
import { useAuth } from '@/context/use-auth';
import { fetchApi } from '@/lib/api-client';
import { Alert } from '@/components/ui/alert';

interface OnboardingCredentials {
  enrollmentId?: number;
  enrollmentProofToken?: string;
  enrollmentChallenge?: number;
}

/**
 * Minimal evaluator onboarding under a BOOTSTRAP session — activation + QR.
 * Honesty copy lives once in AppShell (no duplicate page Alert).
 * After activate / resume, QR is loaded via authenticated GET …/onboarding (no public QR oracle).
 */
export default function EvaluatorOnboardingPage() {
  const { t } = useTranslation(['login']);
  const { session } = useAuth();
  const { data: publicInstanceInfo } = usePublicInstanceInfo();
  const [enrollmentResult, setEnrollmentResult] = useState<{
    success?: boolean;
    username?: string;
    enrollmentId?: number;
    enrollmentProofToken?: string;
    enrollmentChallenge?: number;
  } | null>(null);
  const [onboardingError, setOnboardingError] = useState<string | null>(null);

  useEffect(() => {
    const adminId =
      session?.adminId ??
      (typeof sessionStorage !== 'undefined'
        ? Number(sessionStorage.getItem('ezkey_evaluator_resume_admin_id') || '')
        : NaN);
    const alreadyActive =
      session?.lifecycleStatus === 'ACTIVE' ||
      (Number.isFinite(adminId) && adminId > 0 && !sessionStorage.getItem('ezkey_evaluator_activation_code_prefill'));

    if (!alreadyActive || !Number.isFinite(adminId) || adminId <= 0) {
      return;
    }

    let cancelled = false;
    void (async () => {
      try {
        const data = await fetchApi<OnboardingCredentials>(
          `/api/v1/admins/${adminId}/onboarding`,
          { method: 'GET' },
        );
        if (cancelled) return;
        if (data.enrollmentProofToken && data.enrollmentId != null) {
          setEnrollmentResult({
            success: true,
            username: session?.username,
            enrollmentId: data.enrollmentId,
            enrollmentProofToken: data.enrollmentProofToken,
            enrollmentChallenge: data.enrollmentChallenge,
          });
          sessionStorage.removeItem('ezkey_evaluator_resume_admin_id');
        }
      } catch {
        if (!cancelled) {
          setOnboardingError(t('login:bootstrap.onboardingLoadFailed'));
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [session?.adminId, session?.lifecycleStatus, session?.username, t]);

  return (
    <AppShell title={t('login:bootstrap.onboardingTitle')}>
      <div className="max-w-xl space-y-4">
        <p className="text-sm text-fg-muted">
          {t('login:bootstrap.onboardingHint', { username: session?.username ?? '' })}
        </p>
        {onboardingError && <Alert variant="error">{onboardingError}</Alert>}
        <LoginActivationSection
          onBackToPasswordless={() => undefined}
          authApiPublicBaseUrl={publicInstanceInfo?.authApiPublicBaseUrl}
          hideBackLink
          initialActivationCode={
            typeof sessionStorage !== 'undefined'
              ? sessionStorage.getItem('ezkey_evaluator_activation_code_prefill') ?? undefined
              : undefined
          }
          initialEnrollmentResult={enrollmentResult}
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
