import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { AppShell } from '@/components/layout/app-shell';
import { LoginActivationSection } from '@/components/feature/login-activation-section';
import { usePublicInstanceInfo } from '@/hooks/use-public-instance-info';
import { useAuth } from '@/context/use-auth';
import { fetchApi } from '@/lib/api-client';
import { isBrowserSessionCookieBuild } from '@/lib/auth';
import { mayCompleteLogoutLocallyAfterApiFailure } from '@/lib/auth-session-lifecycle';
import { releaseBootstrapSessionAfterBind } from '@/lib/evaluator-bootstrap-session';
import { logout as logoutApi } from '@/generated/admin-api/admin-authentication/admin-authentication';
import { Alert } from '@/components/ui/alert';

interface OnboardingCredentials {
  enrollmentId?: number;
  enrollmentProofToken?: string;
  enrollmentChallenge?: number;
}

interface EnrollmentStatusSnapshot {
  enrollmentStatus?: string;
}

const ENROLLMENT_POLL_MS = 2500;

/**
 * Minimal evaluator onboarding under a BOOTSTRAP session — activation + QR.
 * Honesty copy lives once in AppShell (no duplicate page Alert).
 * After activate / resume, QR is loaded via authenticated GET …/onboarding (no public QR oracle).
 * When enrollment reaches VERIFIED, release BOOTSTRAP then go to /login (sticky Mode B cookie).
 */
export default function EvaluatorOnboardingPage() {
  const { t } = useTranslation(['login']);
  const navigate = useNavigate();
  const { session, logout } = useAuth();
  const { data: publicInstanceInfo } = usePublicInstanceInfo();
  const [enrollmentResult, setEnrollmentResult] = useState<{
    success?: boolean;
    username?: string;
    enrollmentId?: number;
    enrollmentProofToken?: string;
    enrollmentChallenge?: number;
  } | null>(null);
  const [onboardingError, setOnboardingError] = useState<string | null>(null);
  const [releasingSession, setReleasingSession] = useState(false);
  const releaseStartedRef = useRef(false);

  useEffect(() => {
    const adminId =
      session?.adminId ??
      (typeof sessionStorage !== 'undefined'
        ? Number(sessionStorage.getItem('ezkey_evaluator_resume_admin_id') || '')
        : NaN);
    const alreadyActive =
      session?.lifecycleStatus === 'ACTIVE' ||
      (Number.isFinite(adminId) &&
        adminId > 0 &&
        !sessionStorage.getItem('ezkey_evaluator_activation_code_prefill'));

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

  // Soft craft: after bind VERIFIED, invalidate BOOTSTRAP then navigate to passwordless login.
  useEffect(() => {
    const enrollmentId = enrollmentResult?.enrollmentId;
    if (enrollmentId == null || releaseStartedRef.current) {
      return;
    }

    let cancelled = false;
    const poll = async () => {
      try {
        const snap = await fetchApi<EnrollmentStatusSnapshot>(
          `/api/v1/enrollments/${enrollmentId}`,
          { method: 'GET' },
        );
        if (cancelled || snap.enrollmentStatus !== 'VERIFIED') {
          return;
        }
        if (releaseStartedRef.current) {
          return;
        }
        releaseStartedRef.current = true;
        setReleasingSession(true);
        const released = await releaseBootstrapSessionAfterBind({
          callLogout: () => logoutApi(),
          clearLocalSession: logout,
          mayCompleteLocallyAfterApiFailure: mayCompleteLogoutLocallyAfterApiFailure(
            isBrowserSessionCookieBuild(),
          ),
        });
        if (!cancelled && released) {
          navigate('/login', { replace: true });
        } else if (!cancelled) {
          releaseStartedRef.current = false;
          setReleasingSession(false);
          setOnboardingError(t('login:bootstrap.releaseAfterBindFailed'));
        }
      } catch {
        // Transient poll failures are expected while the device is still binding.
      }
    };

    void poll();
    const timer = window.setInterval(() => {
      void poll();
    }, ENROLLMENT_POLL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [enrollmentResult?.enrollmentId, logout, navigate, t]);

  return (
    <AppShell title={t('login:bootstrap.onboardingTitle')}>
      <div className="max-w-xl space-y-4">
        <p className="text-sm text-fg-muted">
          {t('login:bootstrap.onboardingHint', { username: session?.username ?? '' })}
        </p>
        {onboardingError && <Alert variant="error">{onboardingError}</Alert>}
        {releasingSession && (
          <p className="text-sm text-fg-muted" data-testid="evaluator-onboarding-releasing">
            {t('login:bootstrap.releasingAfterBind')}
          </p>
        )}
        <LoginActivationSection
          onBackToPasswordless={() => undefined}
          authApiPublicBaseUrl={publicInstanceInfo?.authApiPublicBaseUrl}
          hideBackLink
          initialActivationCode={
            typeof sessionStorage !== 'undefined'
              ? (sessionStorage.getItem('ezkey_evaluator_activation_code_prefill') ?? undefined)
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
