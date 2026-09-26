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

interface OnboardingResumeResponse {
  sessionToken?: string | null;
  sessionExpiresAt?: string;
  username?: string;
  adminId?: number;
}

/**
 * Minimal resume for incomplete evaluator onboarding after BOOTSTRAP session death.
 * Capability secret from activation — not a public username oracle.
 */
export function EvaluatorOnboardingResumeSection() {
  const { t } = useTranslation(['login']);
  const navigate = useNavigate();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const schema = useMemo(
    () =>
      z.object({
        onboardingResumeSecret: z
          .string()
          .min(20, t('login:bootstrap.resume.validation.secretRequired'))
          .regex(
            /^ezkey_onboarding_resume_[A-Za-z0-9]+$/,
            t('login:bootstrap.resume.validation.secretFormat'),
          ),
      }),
    [t],
  );
  type FormValues = z.infer<typeof schema>;

  const storedPrefill =
    typeof sessionStorage !== 'undefined'
      ? sessionStorage.getItem('ezkey_evaluator_onboarding_resume_secret') ?? ''
      : '';

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { onboardingResumeSecret: storedPrefill },
  });

  const onSubmit = async (values: FormValues) => {
    setErrorMessage(null);
    setSubmitting(true);
    try {
      const data = await fetchApi<OnboardingResumeResponse>(
        '/api/v1/admin/auth/onboarding-resume',
        {
          method: 'POST',
          requireAuth: false,
          body: JSON.stringify({
            onboardingResumeSecret: values.onboardingResumeSecret.trim(),
          }),
        },
      );

      if (!data.sessionExpiresAt || !data.username) {
        setErrorMessage(t('login:bootstrap.resume.failed'));
        return;
      }

      storeEvaluatorBootstrapHandoff({
        sessionToken: data.sessionToken ?? undefined,
        sessionExpiresAt: data.sessionExpiresAt,
        username: data.username,
        adminId: data.adminId,
      });
      navigate('/evaluator-bootstrap', { replace: true });
    } catch (err) {
      setErrorMessage(getTranslatedApiError(err, t, t('login:bootstrap.resume.failed')));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <details
      className="mt-4 rounded border-2 border-fg/30 bg-bg p-3 text-sm"
      data-testid="evaluator-onboarding-resume"
    >
      <summary className="cursor-pointer font-semibold text-fg outline-none">
        {t('login:bootstrap.resume.summary')}
      </summary>
      <p className="mt-2 text-xs text-fg-muted leading-snug">{t('login:bootstrap.resume.hint')}</p>
      <form className="mt-3 space-y-3" onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="evaluator-resume-secret">
            {t('login:bootstrap.resume.secretLabel')}
          </Label>
          <Input
            id="evaluator-resume-secret"
            autoComplete="off"
            spellCheck={false}
            data-testid="evaluator-resume-secret"
            {...register('onboardingResumeSecret')}
          />
          {errors.onboardingResumeSecret && (
            <p className="text-xs text-danger">{errors.onboardingResumeSecret.message}</p>
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
