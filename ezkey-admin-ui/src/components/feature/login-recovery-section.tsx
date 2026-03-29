import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Check, Copy } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { fetchApi, getApiErrorMessage } from '@/lib/api-client';
import { isValidRecoveryCodeFormat, normalizeRecoveryCodeInput } from '@/lib/recovery-code-format';
import {
  clearRecoverySession,
  getRecoverySession,
  saveRecoverySession,
  type RecoverySession,
} from '@/lib/recovery-session';
import { enrollmentPayloadToQrDataUrl } from '@/lib/enrollment-qr-data-url';
import { buildEnrollmentQrPayloadJson } from '@/lib/enrollment-qr-payload';
import { formatCountdown } from '@/lib/utils';
import type { AdminRecoveryResponseDto } from '@/generated/admin-api/model';
import type { EnrollmentResetResponseDto } from '@/generated/admin-api/model';

type AdminRecoveryUiResponse = AdminRecoveryResponseDto & {
  /**
   * Temporary compatibility field: the backend returns enrollmentId, but the checked-in
   * OpenAPI spec used by Docker codegen may lag behind the Java DTO until specs are refreshed.
   */
  enrollmentId?: number;
};

interface LoginRecoverySectionProps {
  onBackToPasswordless: () => void;
  /** Prefill username from the main login field when switching flows */
  initialUsername: string;
}

export function LoginRecoverySection({
  onBackToPasswordless,
  initialUsername,
}: LoginRecoverySectionProps) {
  const { t } = useTranslation(['login', 'common']);

  const [step, setStep] = useState<'form' | 'tokenActive' | 'afterReset'>(() =>
    getRecoverySession() ? 'tokenActive' : 'form',
  );
  const [session, setSession] = useState<RecoverySession | null>(() => getRecoverySession());
  const [resetResult, setResetResult] = useState<EnrollmentResetResponseDto | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [tokenCopied, setTokenCopied] = useState(false);
  const [enrollmentIdCopied, setEnrollmentIdCopied] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [qrLoading, setQrLoading] = useState(false);
  const [qrError, setQrError] = useState(false);

  const recoverySchema = useMemo(
    () =>
      z.object({
        username: z.string().min(3, t('login:validation.minLength')).max(50),
        recoveryCode: z
          .string()
          .min(1, t('login:recovery.validation.codeRequired'))
          .refine((v) => isValidRecoveryCodeFormat(normalizeRecoveryCodeInput(v)), {
            message: t('login:recovery.validation.codeFormat'),
          }),
      }),
    [t],
  );
  type RecoveryForm = z.infer<typeof recoverySchema>;

  const { register, handleSubmit, formState: { errors } } = useForm<RecoveryForm>({
    resolver: zodResolver(recoverySchema),
    defaultValues: { username: initialUsername, recoveryCode: '' },
  });

  /** If session storage was cleared elsewhere, avoid a stuck "token active" step */
  useEffect(() => {
    if (step === 'tokenActive' && getRecoverySession() == null) {
      setSession(null);
      setStep('form');
    }
  }, [step]);

  useEffect(() => {
    if (step !== 'tokenActive' || !session?.expiresAt) return;
    const tick = () => {
      const remaining = Math.max(
        0,
        Math.floor((new Date(session.expiresAt).getTime() - Date.now()) / 1000),
      );
      setRemainingSeconds(remaining);
      if (remaining === 0) {
        clearRecoverySession();
        setSession(null);
        setStep('form');
        setErrorMessage(t('login:recovery.tokenExpired'));
      }
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [step, session?.expiresAt, t]);

  useEffect(() => {
    if (resetResult?.enrollmentProofToken == null) {
      setQrDataUrl(null);
      setQrLoading(false);
      setQrError(false);
      return;
    }
    const enrollmentId = resetResult.enrollmentId;
    const proof = String(resetResult.enrollmentProofToken);
    if (enrollmentId == null) {
      setQrDataUrl(null);
      setQrLoading(false);
      setQrError(true);
      return;
    }
    let cancelled = false;
    setQrLoading(true);
    setQrError(false);
    void (async () => {
      try {
        const json = buildEnrollmentQrPayloadJson(enrollmentId, proof);
        const url = await enrollmentPayloadToQrDataUrl(json);
        if (!cancelled) {
          setQrDataUrl(url);
          setQrLoading(false);
        }
      } catch {
        if (!cancelled) {
          setQrDataUrl(null);
          setQrError(true);
          setQrLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [resetResult]);

  const onRecover = async (values: RecoveryForm) => {
    setSubmitting(true);
    setErrorMessage(null);
    const normalized = normalizeRecoveryCodeInput(values.recoveryCode.trim());
    try {
      const data = await fetchApi<AdminRecoveryUiResponse>('/api/v1/admin/auth/recover', {
        method: 'POST',
        body: JSON.stringify({
          username: values.username.trim(),
          recoveryCode: normalized,
        }),
        requireAuth: false,
      });
      const enrollmentId = data.enrollmentId;
      if (
        !data.success ||
        data.recoveryToken == null ||
        data.expiresAt == null ||
        enrollmentId == null
      ) {
        setErrorMessage(data.message ?? t('login:recovery.recoverFailed'));
        return;
      }
      const next: RecoverySession = {
        recoveryToken: data.recoveryToken,
        expiresAt: data.expiresAt,
        username: values.username.trim(),
        codesRemaining: data.codesRemaining ?? 0,
        enrollmentId,
      };
      saveRecoverySession(next);
      setSession(next);
      setStep('tokenActive');
    } catch (err) {
      setErrorMessage(getApiErrorMessage(err, t('login:recovery.recoverFailed')));
    } finally {
      setSubmitting(false);
    }
  };

  const onResetEnrollment = async () => {
    if (session == null) return;
    setResetting(true);
    setErrorMessage(null);
    try {
      const data = await fetchApi<EnrollmentResetResponseDto>('/api/v1/admin/enrollments/reset', {
        method: 'POST',
        body: JSON.stringify({ enrollmentId: session.enrollmentId }),
        requireAuth: false,
        bearerToken: session.recoveryToken,
      });
      if (!data.success || data.enrollmentProofToken == null) {
        setErrorMessage(data.message ?? t('login:recovery.resetFailed'));
        return;
      }
      clearRecoverySession();
      setResetResult(data);
      setSession(null);
      setStep('afterReset');
    } catch (err) {
      setErrorMessage(getApiErrorMessage(err, t('login:recovery.resetFailed')));
    } finally {
      setResetting(false);
    }
  };

  const handleCopyToken = async () => {
    const token = resetResult?.enrollmentProofToken;
    if (token == null || token === '') return;
    try {
      await navigator.clipboard.writeText(String(token));
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 2000);
    } catch {
      /* ignore */
    }
  };

  const handleCopyEnrollmentId = async () => {
    const id = resetResult?.enrollmentId;
    if (id == null) return;
    try {
      await navigator.clipboard.writeText(String(id));
      setEnrollmentIdCopied(true);
      setTimeout(() => setEnrollmentIdCopied(false), 2000);
    } catch {
      /* ignore */
    }
  };

  const handleCancelRecovery = () => {
    clearRecoverySession();
    setSession(null);
    setErrorMessage(null);
    setStep('form');
    onBackToPasswordless();
  };

  const handleDoneAfterReset = () => {
    setResetResult(null);
    setStep('form');
    setErrorMessage(null);
    onBackToPasswordless();
  };

  const countdownColor =
    remainingSeconds <= 120 ? 'text-error' : remainingSeconds <= 600 ? 'text-warning' : 'text-fg';

  if (step === 'afterReset' && resetResult?.enrollmentProofToken != null) {
    return (
      <div className="flex flex-col gap-4">
        {/* 1. Status + framing */}
        <section className="space-y-3">
          <Alert variant="success" title={t('login:recovery.resetSuccessTitle')}>
            {t('login:recovery.resetSuccessBody')}
          </Alert>
          <p className="text-xs text-fg-muted leading-snug border-l-2 border-fg/25 pl-3 py-0.5">
            {t('login:recovery.previewNotice')}
          </p>
        </section>

        {/* 2. Bind credentials: QR + challenge / manual (side by side from md+) */}
        <section className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6 md:items-start">
          <div className="space-y-2 min-w-0 md:max-w-[280px] md:justify-self-center">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
              {t('login:recovery.qrTitle')}
            </p>
            {qrLoading && (
              <p className="text-sm text-fg-muted">{t('login:recovery.qrLoading')}</p>
            )}
            {qrError && (
              <Alert variant="warning" title={t('login:recovery.qrErrorTitle')}>
                {t('login:recovery.qrErrorBody')}
              </Alert>
            )}
            {qrDataUrl != null && !qrLoading && (
              <div className="flex flex-col items-stretch gap-2 border-2 border-fg p-3 bg-bg">
                <img
                  src={qrDataUrl}
                  alt={t('login:recovery.qrCodeAlt')}
                  className="mx-auto w-48 h-48 max-w-full object-contain"
                  width={192}
                  height={192}
                />
                <p className="text-center text-xs text-fg-muted leading-snug">{t('login:recovery.qrHint')}</p>
              </div>
            )}
          </div>

          <div className="space-y-3 min-w-0 flex flex-col">
            <div className="border-2 border-fg/30 p-3 bg-bg">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                {t('login:recovery.bindingChallenge')}
              </p>
              <p className="font-mono text-3xl font-black tracking-widest tabular-nums">
                {String(resetResult.enrollmentChallenge ?? '')}
              </p>
            </div>

            <details className="rounded border-2 border-fg/30 bg-bg p-3 text-sm open:border-fg/50">
              <summary className="cursor-pointer font-semibold text-fg outline-none">
                {t('login:recovery.manualEntry')}
              </summary>
              <p className="mt-2 text-xs text-fg-muted leading-snug">{t('login:recovery.manualEntryHint')}</p>
              <div className="mt-3 space-y-4">
                {resetResult.enrollmentId != null && (
                  <div className="space-y-1.5">
                    <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
                      {t('login:recovery.enrollmentIdLabel')}
                    </p>
                    <div className="border-2 border-fg p-2 font-mono text-sm tabular-nums bg-bg">
                      {String(resetResult.enrollmentId)}
                    </div>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => void handleCopyEnrollmentId()}
                      className="gap-1.5"
                    >
                      {enrollmentIdCopied ? (
                        <Check className="size-3.5 text-success" />
                      ) : (
                        <Copy className="size-3.5" />
                      )}
                      {enrollmentIdCopied ? t('login:recovery.copied') : t('login:recovery.copyEnrollmentId')}
                    </Button>
                  </div>
                )}
                <div className="space-y-1.5">
                  <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
                    {t('login:recovery.enrollmentProofToken')}
                  </p>
                  <div className="border-2 border-fg p-2 font-mono text-[11px] break-all bg-bg leading-relaxed max-h-32 overflow-y-auto">
                    {String(resetResult.enrollmentProofToken)}
                  </div>
                  <Button variant="secondary" size="sm" onClick={handleCopyToken} className="gap-1.5">
                    {tokenCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
                    {tokenCopied ? t('login:recovery.copied') : t('login:recovery.copyToken')}
                  </Button>
                </div>
              </div>
            </details>
            <p className="text-xs text-fg-muted leading-snug">{t('login:recovery.thenPasswordless')}</p>
          </div>
        </section>

        <Button className="w-full shrink-0" onClick={handleDoneAfterReset}>
          {t('login:recovery.backToLogin')}
        </Button>
      </div>
    );
  }

  if (step === 'tokenActive' && session != null) {
    return (
      <div className="space-y-4">
        <Alert variant="info" title={t('login:recovery.tokenActiveTitle')}>
          {t('login:recovery.tokenActiveBody', {
            count: session.codesRemaining,
            username: session.username,
          })}
        </Alert>
        <div>
          <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
            {t('login:recovery.recoveryTokenExpires')}
          </p>
          <p className={`font-mono text-2xl font-bold tabular-nums ${countdownColor}`}>
            {formatCountdown(remainingSeconds)}
          </p>
        </div>
        {errorMessage && (
          <Alert variant="error">{errorMessage}</Alert>
        )}
        <div className="flex flex-col gap-2">
          <Button
            className="w-full"
            isLoading={resetting}
            onClick={() => void onResetEnrollment()}
          >
            {t('login:recovery.resetEnrollment')}
          </Button>
          <Button variant="ghost" size="sm" className="w-full" onClick={handleCancelRecovery}>
            {t('login:recovery.cancelRecovery')}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit(onRecover)} className="space-y-4" noValidate>
      <p className="text-sm text-fg-muted">{t('login:recovery.intro')}</p>
      {errorMessage && (
        <Alert variant="error">{errorMessage}</Alert>
      )}
      <div className="space-y-1">
        <Label htmlFor="recovery-username">{t('login:form.username')}</Label>
        <Input
          id="recovery-username"
          autoComplete="username"
          placeholder={t('login:form.usernamePlaceholder')}
          error={errors.username?.message}
          {...register('username')}
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="recovery-code">{t('login:recovery.recoveryCodeLabel')}</Label>
        <Input
          id="recovery-code"
          autoComplete="off"
          className="font-mono text-sm"
          placeholder={t('login:recovery.recoveryCodePlaceholder')}
          error={errors.recoveryCode?.message}
          {...register('recoveryCode')}
        />
        <p className="text-xs text-fg-muted">{t('login:recovery.recoveryCodeHint')}</p>
      </div>
      <Button type="submit" size="lg" className="w-full" isLoading={submitting}>
        {t('login:recovery.submitRecover')}
      </Button>
      <Button type="button" variant="ghost" size="sm" className="w-full" onClick={handleCancelRecovery}>
        {t('login:recovery.backToPasswordless')}
      </Button>
    </form>
  );
}
