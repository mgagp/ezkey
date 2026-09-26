import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Check, Copy } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { IntegratedDeliveryNotice } from '@/components/feature/integrated-delivery-notice';
import { useAuth } from '@/context/use-auth';
import { fetchApi } from '@/lib/api-client';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { isBrowserSessionCookieBuild, type AuthSession } from '@/lib/auth';
import { enrollmentPayloadToQrDataUrl } from '@/lib/enrollment-qr-data-url';
import { buildEnrollmentQrPayloadJson } from '@/lib/enrollment-qr-payload';

interface AdminActivationResponseShape {
  success?: boolean;
  message?: string;
  username?: string;
  enrollmentId?: number;
  enrollmentProofToken?: string;
  enrollmentChallenge?: number;
}

interface EvaluatorTempSessionResponseShape {
  success?: boolean;
  message?: string;
  token?: string;
  adminType?: string;
  username?: string;
  expiresAt?: string;
  adminId?: number;
  tenantId?: number | null;
  tenantName?: string | null;
  tokenPurpose?: string;
  csrfToken?: string;
}

interface LoginActivationSectionProps {
  onBackToPasswordless: () => void;
  authApiPublicBaseUrl?: string | null;
  /** When true (self-reg ON), show explore temporary console vs enroll device fork. */
  temporaryExploreEnabled?: boolean;
}

type QrRenderState = {
  key: string | null;
  status: 'idle' | 'loading' | 'ready' | 'error';
  dataUrl: string | null;
};

type PostActivateView = 'fork' | 'enroll';

export function LoginActivationSection({
  onBackToPasswordless,
  authApiPublicBaseUrl,
  temporaryExploreEnabled = false,
}: LoginActivationSectionProps) {
  const { t } = useTranslation(['login']);
  const navigate = useNavigate();
  const { login } = useAuth();

  const activationSchema = useMemo(
    () =>
      z.object({
        activationCode: z
          .string()
          .min(1, t('login:activation.validation.codeRequired'))
          .regex(
            /^ezkey_activation_[A-Za-z0-9]+$/,
            t('login:activation.validation.codeFormat'),
          ),
      }),
    [t],
  );
  type ActivationForm = z.infer<typeof activationSchema>;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ActivationForm>({
    resolver: zodResolver(activationSchema),
    defaultValues: { activationCode: '' },
  });

  const [activationResult, setActivationResult] =
    useState<AdminActivationResponseShape | null>(null);
  const [postActivateView, setPostActivateView] = useState<PostActivateView>('fork');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [exploring, setExploring] = useState(false);
  const [tokenCopied, setTokenCopied] = useState(false);
  const [enrollmentIdCopied, setEnrollmentIdCopied] = useState(false);
  const [qrState, setQrState] = useState<QrRenderState>({
    key: null,
    status: 'idle',
    dataUrl: null,
  });

  const showFork =
    activationResult?.enrollmentProofToken != null &&
    temporaryExploreEnabled &&
    postActivateView === 'fork';
  const showEnroll =
    activationResult?.enrollmentProofToken != null &&
    (!temporaryExploreEnabled || postActivateView === 'enroll');

  const qrInput = useMemo(() => {
    if (!showEnroll) {
      return null;
    }
    const proofToken = activationResult?.enrollmentProofToken;
    if (proofToken == null) {
      return null;
    }

    const enrollmentId = activationResult?.enrollmentId;
    if (enrollmentId == null) {
      return { invalid: true } as const;
    }

    const proof = String(proofToken);
    const key = `${enrollmentId}:${proof}:${authApiPublicBaseUrl ?? ''}`;
    return {
      invalid: false,
      key,
      enrollmentId,
      proof,
    } as const;
  }, [
    activationResult?.enrollmentId,
    activationResult?.enrollmentProofToken,
    authApiPublicBaseUrl,
    showEnroll,
  ]);

  useEffect(() => {
    if (qrInput == null || qrInput.invalid) {
      return;
    }

    if (qrState.key === qrInput.key) {
      return;
    }

    let cancelled = false;
    setQrState({
      key: qrInput.key,
      status: 'loading',
      dataUrl: null,
    });
    void (async () => {
      try {
        const payload = buildEnrollmentQrPayloadJson(
          qrInput.enrollmentId,
          qrInput.proof,
          authApiPublicBaseUrl,
        );
        const url = await enrollmentPayloadToQrDataUrl(payload);
        if (!cancelled) {
          setQrState({
            key: qrInput.key,
            status: 'ready',
            dataUrl: url,
          });
        }
      } catch {
        if (!cancelled) {
          setQrState({
            key: qrInput.key,
            status: 'error',
            dataUrl: null,
          });
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [authApiPublicBaseUrl, qrInput, qrState.key]);

  const hasValidQrInput = qrInput != null && !qrInput.invalid;
  const isCurrentQrResult = hasValidQrInput && qrState.key === qrInput.key;
  const qrLoading = isCurrentQrResult && qrState.status === 'loading';
  const qrError =
    (qrInput != null && qrInput.invalid) || (isCurrentQrResult && qrState.status === 'error');
  const qrDataUrl = isCurrentQrResult && qrState.status === 'ready' ? qrState.dataUrl : null;

  const onActivate = async (values: ActivationForm) => {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const data = await fetchApi<AdminActivationResponseShape>('/api/v1/admin/auth/activate', {
        method: 'POST',
        body: JSON.stringify({ activationCode: values.activationCode.trim() }),
        requireAuth: false,
      });

      if (
        !data.success ||
        data.enrollmentId == null ||
        data.enrollmentProofToken == null
      ) {
        setErrorMessage(data.message ?? t('login:activation.activateFailed'));
        return;
      }

      setActivationResult(data);
      setPostActivateView(temporaryExploreEnabled ? 'fork' : 'enroll');
    } catch (err) {
      setErrorMessage(getTranslatedApiError(err, t, t('login:activation.activateFailed')));
    } finally {
      setSubmitting(false);
    }
  };

  const onExploreTemporary = async () => {
    if (
      activationResult?.enrollmentId == null ||
      activationResult.enrollmentProofToken == null
    ) {
      return;
    }
    setExploring(true);
    setErrorMessage(null);
    try {
      const data = await fetchApi<EvaluatorTempSessionResponseShape>(
        '/api/v1/admin/auth/evaluator-temp',
        {
          method: 'POST',
          body: JSON.stringify({
            enrollmentId: activationResult.enrollmentId,
            enrollmentProofToken: activationResult.enrollmentProofToken,
          }),
          requireAuth: false,
        },
      );

      if (!data.success || !data.username || !data.adminType || !data.expiresAt) {
        setErrorMessage(data.message ?? t('login:activation.exploreFailed'));
        return;
      }

      const cookieBuild = isBrowserSessionCookieBuild();
      if (cookieBuild) {
        const restored = await fetchApi<AuthSession>('/api/v1/admin/auth/me', {
          method: 'GET',
          requireAuth: false,
        });
        login(restored);
      } else {
        if (typeof data.token !== 'string' || data.token.length === 0) {
          setErrorMessage(t('login:activation.exploreFailed'));
          return;
        }
        login({
          token: data.token,
          username: data.username,
          adminType: data.adminType,
          expiresAt:
            typeof data.expiresAt === 'string' ? data.expiresAt : String(data.expiresAt),
          ...(data.adminId != null && { adminId: data.adminId }),
          ...(data.tenantId != null && { tenantId: data.tenantId }),
          ...(data.tenantName != null &&
            data.tenantName !== '' && { tenantName: data.tenantName }),
          ...(data.csrfToken != null && { csrfToken: data.csrfToken }),
          tokenPurpose: data.tokenPurpose ?? 'EVALUATOR_TEMP',
        });
      }
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setErrorMessage(getTranslatedApiError(err, t, t('login:activation.exploreFailed')));
    } finally {
      setExploring(false);
    }
  };

  const handleCopyToken = async () => {
    const token = activationResult?.enrollmentProofToken;
    if (token == null || token === '') return;
    try {
      await navigator.clipboard.writeText(String(token));
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 2000);
    } catch {
      // ignore clipboard errors
    }
  };

  const handleCopyEnrollmentId = async () => {
    const id = activationResult?.enrollmentId;
    if (id == null) return;
    try {
      await navigator.clipboard.writeText(String(id));
      setEnrollmentIdCopied(true);
      setTimeout(() => setEnrollmentIdCopied(false), 2000);
    } catch {
      // ignore clipboard errors
    }
  };

  const handleBackToLogin = () => {
    setActivationResult(null);
    setErrorMessage(null);
    setPostActivateView('fork');
    onBackToPasswordless();
  };

  if (showFork) {
    return (
      <div className="flex flex-col gap-4" data-testid="login-activation-fork">
        <Alert variant="success" title={t('login:activation.successTitle')}>
          {t('login:activation.successBody', {
            username: activationResult?.username ?? '',
          })}
        </Alert>
        <p className="text-sm text-fg-muted">{t('login:activation.forkIntro')}</p>
        {errorMessage && <Alert variant="error">{errorMessage}</Alert>}
        <div className="flex flex-col gap-3">
          <Button
            type="button"
            className="w-full"
            isLoading={exploring}
            onClick={() => void onExploreTemporary()}
            data-testid="login-activation-explore-temp"
          >
            {t('login:activation.exploreTemporary')}
          </Button>
          <Button
            type="button"
            variant="secondary"
            className="w-full"
            onClick={() => {
              setErrorMessage(null);
              setPostActivateView('enroll');
            }}
            data-testid="login-activation-enroll-device"
          >
            {t('login:activation.enrollDevice')}
          </Button>
        </div>
        <Button type="button" variant="ghost" size="sm" className="w-full" onClick={handleBackToLogin}>
          {t('login:activation.backToLogin')}
        </Button>
      </div>
    );
  }

  if (showEnroll) {
    return (
      <div className="flex flex-col gap-4" data-testid="login-activation-success">
        <section className="space-y-3">
          <Alert variant="success" title={t('login:activation.successTitle')}>
            {t('login:activation.successBody', {
              username: activationResult?.username ?? '',
            })}
          </Alert>
          <IntegratedDeliveryNotice summary={t('common:integratedDelivery.summaryBootstrap')}>
            {t('login:activation.previewNotice')}
          </IntegratedDeliveryNotice>
          <Alert variant="info" title={t('login:activation.recoveryCodesDeferredTitle')}>
            {t('login:activation.recoveryCodesDeferredBody')}
          </Alert>
        </section>

        <section className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6 md:items-start">
          <div className="space-y-2 min-w-0 md:max-w-[280px] md:justify-self-center">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
              {t('login:activation.qrTitle')}
            </p>
            {qrLoading && (
              <p className="text-sm text-fg-muted">{t('login:activation.qrLoading')}</p>
            )}
            {qrError && (
              <Alert variant="warning" title={t('login:activation.qrErrorTitle')}>
                {t('login:activation.qrErrorBody')}
              </Alert>
            )}
            {qrDataUrl != null && !qrLoading && (
              <div className="flex flex-col items-stretch gap-2 border-2 border-fg p-3 bg-bg">
                <img
                  src={qrDataUrl}
                  alt={t('login:activation.qrCodeAlt')}
                  className="mx-auto w-48 h-48 max-w-full object-contain"
                  width={192}
                  height={192}
                />
                <p className="text-center text-xs text-fg-muted leading-snug">
                  {t('login:activation.qrHint')}
                </p>
              </div>
            )}
          </div>

          <div className="space-y-3 min-w-0 flex flex-col">
            <div className="border-2 border-fg/30 p-3 bg-bg">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                {t('login:activation.bindingChallenge')}
              </p>
              <p className="font-mono text-3xl font-black tracking-widest tabular-nums">
                {String(activationResult?.enrollmentChallenge ?? '')}
              </p>
            </div>

            <details className="rounded border-2 border-fg/30 bg-bg p-3 text-sm open:border-fg/50">
              <summary className="cursor-pointer font-semibold text-fg outline-none">
                {t('login:activation.manualEntry')}
              </summary>
              <p className="mt-2 text-xs text-fg-muted leading-snug">
                {t('login:activation.manualEntryHint')}
              </p>
              <div className="mt-3 space-y-4">
                {activationResult?.enrollmentId != null && (
                  <div className="space-y-1.5">
                    <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
                      {t('login:activation.enrollmentIdLabel')}
                    </p>
                    <div className="border-2 border-fg p-2 font-mono text-sm tabular-nums bg-bg">
                      {String(activationResult.enrollmentId)}
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
                      {enrollmentIdCopied
                        ? t('login:activation.copied')
                        : t('login:activation.copyEnrollmentId')}
                    </Button>
                  </div>
                )}

                <div className="space-y-1.5">
                  <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
                    {t('login:activation.enrollmentProofToken')}
                  </p>
                  <div className="border-2 border-fg p-2 font-mono text-[11px] break-all bg-bg leading-relaxed max-h-32 overflow-y-auto">
                    {String(activationResult?.enrollmentProofToken)}
                  </div>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => void handleCopyToken()}
                    className="gap-1.5"
                  >
                    {tokenCopied ? (
                      <Check className="size-3.5 text-success" />
                    ) : (
                      <Copy className="size-3.5" />
                    )}
                    {tokenCopied
                      ? t('login:activation.copied')
                      : t('login:activation.copyToken')}
                  </Button>
                </div>
              </div>
            </details>

            <p className="text-xs text-fg-muted leading-snug">
              {t('login:activation.thenPasswordless')}
            </p>
          </div>
        </section>

        {temporaryExploreEnabled && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="w-full"
            onClick={() => setPostActivateView('fork')}
          >
            {t('login:activation.backToFork')}
          </Button>
        )}
        <Button className="w-full shrink-0" onClick={handleBackToLogin}>
          {t('login:activation.backToLogin')}
        </Button>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSubmit(onActivate)}
      className="space-y-4"
      noValidate
      data-testid="login-activation-form"
    >
      <p className="text-sm text-fg-muted">{t('login:activation.intro')}</p>
      <p className="text-xs text-fg-muted leading-snug border-l-2 border-fg/25 pl-3 py-0.5">
        {t('login:activation.adminLoginDistinctionNote')}
      </p>
      {errorMessage && <Alert variant="error">{errorMessage}</Alert>}
      <div className="space-y-1">
        <Label htmlFor="activation-code">{t('login:activation.activationCodeLabel')}</Label>
        <Input
          id="activation-code"
          autoFocus
          autoComplete="off"
          className="font-mono text-sm"
          placeholder={t('login:activation.activationCodePlaceholder')}
          error={errors.activationCode?.message}
          {...register('activationCode')}
        />
      </div>
      <div className="flex flex-col gap-2">
        <Button className="w-full" isLoading={submitting} type="submit">
          {t('login:activation.submitActivate')}
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="w-full"
          onClick={handleBackToLogin}
        >
          {t('login:activation.backToPasswordless')}
        </Button>
      </div>
    </form>
  );
}
