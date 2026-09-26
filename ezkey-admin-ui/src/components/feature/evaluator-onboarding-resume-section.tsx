import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { fetchApi } from '@/lib/api-client';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { storeEvaluatorBootstrapHandoff } from '@/lib/evaluator-bootstrap-session';

interface ReissueResponse {
  phase?: string;
  username?: string;
  activationCode?: string | null;
  enrollmentId?: number | null;
  enrollmentProofToken?: string | null;
  enrollmentChallenge?: number | null;
  sessionToken?: string | null;
  sessionExpiresAt?: string;
}

/**
 * Minimal public resume for incomplete evaluator onboarding after BOOTSTRAP session death.
 * Same self-reg flag on the API; rate-limited; no permanent password.
 */
export function EvaluatorOnboardingResumeSection() {
  const { t } = useTranslation(['login']);
  const navigate = useNavigate();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const schema = useMemo(
    () =>
      z.object({
        username: z
          .string()
          .min(3, t('login:bootstrap.resume.validation.usernameRequired'))
          .max(80),
      }),
    [t],
  );
  type FormValues = z.infer<typeof schema>;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: '' },
  });

  const onSubmit = async (values: FormValues) => {
    setErrorMessage(null);
    setSubmitting(true);
    try {
      const data = await fetchApi<ReissueResponse>('/api/v1/public/evaluator-onboarding/reissue', {
        method: 'POST',
        requireAuth: false,
        body: JSON.stringify({ username: values.username.trim() }),
      });

      if (!data.sessionExpiresAt || !data.username) {
        setErrorMessage(t('login:bootstrap.resume.failed'));
        return;
      }

      if (data.activationCode) {
        sessionStorage.setItem('ezkey_evaluator_activation_code_prefill', data.activationCode);
      }
      if (data.enrollmentProofToken && data.enrollmentId != null) {
        sessionStorage.setItem(
          'ezkey_evaluator_enrollment_prefill',
          JSON.stringify({
            username: data.username,
            enrollmentId: data.enrollmentId,
            enrollmentProofToken: data.enrollmentProofToken,
            enrollmentChallenge: data.enrollmentChallenge ?? null,
          }),
        );
      }

      storeEvaluatorBootstrapHandoff({
        sessionToken: data.sessionToken ?? undefined,
        sessionExpiresAt: data.sessionExpiresAt,
        username: data.username,
        activationCode: data.activationCode ?? undefined,
        enrollmentId: data.enrollmentId ?? undefined,
        enrollmentProofToken: data.enrollmentProofToken ?? undefined,
        enrollmentChallenge: data.enrollmentChallenge ?? undefined,
      });
      navigate('/evaluator-bootstrap', { replace: true });
    } catch (err) {
      setErrorMessage(getTranslatedApiError(err, t, t('login:bootstrap.resume.failed')));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <details className="mt-4 rounded border-2 border-fg/30 bg-bg p-3 text-sm" data-testid="evaluator-onboarding-resume">
      <summary className="cursor-pointer font-semibold text-fg outline-none">
        {t('login:bootstrap.resume.summary')}
      </summary>
      <p className="mt-2 text-xs text-fg-muted leading-snug">{t('login:bootstrap.resume.hint')}</p>
      <form
        className="mt-3 space-y-3"
        onSubmit={handleSubmit(onSubmit)}
        noValidate
      >
        <div className="space-y-1.5">
          <Label htmlFor="evaluator-resume-username">{t('login:bootstrap.resume.usernameLabel')}</Label>
          <Input
            id="evaluator-resume-username"
            autoComplete="username"
            data-testid="evaluator-resume-username"
            {...register('username')}
          />
          {errors.username && (
            <p className="text-xs text-danger">{errors.username.message}</p>
          )}
        </div>
        {errorMessage && (
          <Alert variant="error" data-testid="evaluator-resume-error">
            {errorMessage}
          </Alert>
        )}
        <Button type="submit" disabled={submitting} data-testid="evaluator-resume-submit">
          {submitting
            ? t('login:bootstrap.resume.submitting')
            : t('login:bootstrap.resume.submit')}
        </Button>
      </form>
    </details>
  );
}
