import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { CircleHelp, Pin, PinOff } from 'lucide-react';
import { useAuth } from '@/context/auth-context';
import { useHelp } from '@/context/help-context';
import { usePublicInstanceInfo } from '@/hooks/use-public-instance-info';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tooltip } from '@/components/ui/tooltip';
import { I18N_STORAGE_KEY } from '@/i18n';
import type { FetchOptions } from '@/lib/api-client';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { mapPasswordlessWaitError } from '@/lib/map-passwordless-wait-error';
import { persistUsernamePref, readUsernamePref } from '@/lib/last-username-pref';
import { LoginRecoverySection } from '@/components/feature/login-recovery-section';
import { getRecoverySession } from '@/lib/recovery-session';
import { cn, formatChallengeCode, formatCountdown } from '@/lib/utils';
import { login as loginApi, passwordlessWait } from '@/generated/admin-api/admin-authentication/admin-authentication';
import type { AdminLoginResponseDto } from '@/generated/admin-api/model';

type LoginState = 'idle' | 'submitting' | 'waiting' | 'rejected' | 'expired' | 'error';

interface WaitingData {
  authAttemptId: number;
  challengeCode: number | null;
  expiresAt: string;
}

export default function LoginPage() {
  const { t, i18n } = useTranslation(['login', 'common', 'layout', 'help']);
  const navigate = useNavigate();
  const { login, isAuthenticated } = useAuth();
  const { openHelp } = useHelp();
  const { data: publicInstanceInfo } = usePublicInstanceInfo();

  const loginSchema = useMemo(
    () =>
      z.object({
        username: z.string().min(3, t('login:validation.minLength')).max(50),
        rememberUsername: z.boolean(),
        challengeRequested: z.boolean(),
      }),
    [t],
  );
  type LoginForm = z.infer<typeof loginSchema>;

  const [loginState, setLoginState] = useState<LoginState>('idle');
  const [waitingData, setWaitingData] = useState<WaitingData | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  // Guards against race conditions — once a final state is set, ignore further callbacks.
  const finalStatusRef = useRef<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const loginDefaults = useMemo(() => {
    const pref = readUsernamePref();
    return {
      username: pref?.username ?? '',
      rememberUsername: pref?.rememberUsername ?? false,
      challengeRequested: false,
    };
  }, []);

  const { register, control, handleSubmit, watch, formState: { errors } } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: loginDefaults,
  });

  /** Passwordless vs recovery funnel (recovery is not a full admin session). */
  const [authFlow, setAuthFlow] = useState<'passwordless' | 'recovery'>('passwordless');
  const [recoveryUsernamePrefill, setRecoveryUsernamePrefill] = useState('');

  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated) navigate('/dashboard', { replace: true });
  }, [isAuthenticated, navigate]);

  // Resume in-progress recovery (temporary recovery token) after refresh
  useEffect(() => {
    if (isAuthenticated) return;
    if (getRecoverySession()) setAuthFlow('recovery');
  }, [isAuthenticated]);

  // Countdown timer — driven by server expiresAt
  useEffect(() => {
    if (!waitingData?.expiresAt || loginState !== 'waiting') return;

    const update = () => {
      const remaining = Math.max(
        0,
        Math.floor((new Date(waitingData.expiresAt).getTime() - Date.now()) / 1000),
      );
      setRemainingSeconds(remaining);
      if (remaining === 0 && finalStatusRef.current === null) {
        finalStatusRef.current = 'EXPIRED';
        abortRef.current?.abort();
        setLoginState('expired');
      }
    };

    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, [waitingData?.expiresAt, loginState]);

  // Long-running passwordless-wait call — blocks on the server until the device responds.
  useEffect(() => {
    if (loginState !== 'waiting' || !waitingData) return;

    finalStatusRef.current = null;
    const controller = new AbortController();
    abortRef.current = controller;

    const doWait = async () => {
      try {
        const waitOptions: FetchOptions = { signal: controller.signal, requireAuth: false };
        const response = await passwordlessWait(
          {
            authAttemptId: waitingData.authAttemptId,
            ...(waitingData.challengeCode != null && { challengeCode: waitingData.challengeCode }),
          },
          waitOptions,
        );
        const data = response as unknown as AdminLoginResponseDto;

        // Ignore result if the countdown or cancel already set a final state
        if (finalStatusRef.current !== null) return;

        if (data.success && data.token && data.username && data.adminType && data.expiresAt) {
          finalStatusRef.current = 'ACCEPTED';
          login({ token: data.token, username: data.username, adminType: data.adminType, expiresAt: data.expiresAt });
          navigate('/dashboard', { replace: true });
        } else if (data.status === 'rejected') {
          finalStatusRef.current = 'REJECTED';
          setLoginState('rejected');
        } else {
          finalStatusRef.current = 'ERROR';
          setLoginState('error');
          setErrorMessage(data.message ?? t('login:states.error.authFailed'));
        }
      } catch (err) {
        if ((err as Error).name === 'AbortError') return;
        if (finalStatusRef.current !== null) return;

        const mapped = mapPasswordlessWaitError(
          err,
          t('login:states.error.messageFallback'),
          t('login:states.error.connectionLost'),
          (e, fb) => getTranslatedApiError(e, t, fb),
        );

        switch (mapped.outcome) {
          case 'rejected':
            finalStatusRef.current = 'REJECTED';
            setLoginState('rejected');
            setErrorMessage(null);
            break;
          case 'expired':
            finalStatusRef.current = 'EXPIRED';
            setLoginState('expired');
            setErrorMessage(null);
            break;
          case 'error':
            finalStatusRef.current = 'ERROR';
            setLoginState('error');
            setErrorMessage(mapped.message);
            break;
          case 'connectionLost':
            finalStatusRef.current = 'ERROR';
            setLoginState('error');
            setErrorMessage(mapped.message);
            break;
        }
      }
    };

    void doWait();
    return () => { controller.abort(); };
  // eslint-disable-next-line react-hooks/exhaustive-deps -- omit t to avoid restarting wait on language change
  }, [loginState, waitingData, login, navigate]);

  const onSubmit = async (formData: LoginForm) => {
    setLoginState('submitting');
    setErrorMessage(null);
    try {
      const loginRequestOptions: FetchOptions = { requireAuth: false };
      const result = await loginApi(
        {
          username: formData.username.trim(),
          challengeRequested: formData.challengeRequested,
          nonBlocking: true,
        },
        loginRequestOptions,
      ) as unknown as AdminLoginResponseDto;
      if (result.authAttemptId && result.expiresAt) {
        persistUsernamePref(formData.username, formData.rememberUsername);
        setWaitingData({
          authAttemptId: result.authAttemptId,
          challengeCode: result.challengeCode ?? null,
          expiresAt: result.expiresAt,
        });
        setLoginState('waiting');
      } else {
        setLoginState('error');
        setErrorMessage(result.message ?? t('login:states.error.loginFailed'));
      }
    } catch (err) {
      setLoginState('error');
      setErrorMessage(getTranslatedApiError(err, t, t('login:states.error.serverUnreachable')));
    }
  };

  const handleCancel = () => {
    finalStatusRef.current = 'CANCELLED';
    abortRef.current?.abort();
    setLoginState('idle');
    setWaitingData(null);
    setErrorMessage(null);
  };

  const handleRetry = () => {
    setLoginState('idle');
    setErrorMessage(null);
  };

  const countdownColor =
    remainingSeconds <= 30 ? 'text-error' : remainingSeconds <= 60 ? 'text-warning' : 'text-fg';

  const setLanguage = (lng: 'en' | 'fr') => {
    i18n.changeLanguage(lng);
    window.localStorage.setItem(I18N_STORAGE_KEY, lng);
  };

  const recoveryLayout = authFlow === 'recovery';

  return (
    <div
      data-testid="login-page"
      className={cn(
        'min-h-screen bg-bg flex justify-center p-4',
        recoveryLayout ? 'items-start py-6 sm:py-10' : 'items-center',
      )}
    >
      <div
        className={cn(
          'w-full border border-[#3076DF] p-4 sm:p-6',
          recoveryLayout ? 'max-w-4xl' : 'max-w-sm',
        )}
      >
        <div className="relative">
          {/* Language selector — no header when unauthenticated */}
          <div className="absolute top-0 right-0 flex items-center gap-2 text-sm">
          <button
            type="button"
            onClick={() => openHelp()}
            className="p-1 hover:bg-fg/10 transition-colors"
            data-testid="login-help-button"
            aria-label={t('help:drawer.openHelp')}
            title={t('help:drawer.openHelp')}
          >
            <CircleHelp className="size-4 text-sidebar-bg" />
          </button>
          <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setLanguage('en')}
            data-testid="login-language-en"
            className={i18n.language.startsWith('en') ? 'font-bold text-sidebar-bg' : 'text-fg-muted hover:text-fg'}
            aria-label="English"
          >
            EN
          </button>
          <span className="text-fg/30">|</span>
          <button
            type="button"
            onClick={() => setLanguage('fr')}
            data-testid="login-language-fr"
            className={i18n.language.startsWith('fr') ? 'font-bold text-sidebar-bg' : 'text-fg-muted hover:text-fg'}
            aria-label="Français"
          >
            FR
          </button>
          </div>
        </div>

        {/* Brand header — tighter when recovery so bind-after-reset fits common viewports */}
        <div className={cn('text-center', recoveryLayout ? 'mb-4 sm:mb-6' : 'mb-8')}>
          <img
            src="/logo.svg"
            alt=""
            className="mx-auto mb-4"
            width={80}
            height={80}
          />
          <p className="text-[10px] font-black uppercase tracking-[0.4em] text-sidebar-bg mb-2">
            {t('layout:brand')}
          </p>
          <h1 className="text-3xl font-black text-sidebar-bg tracking-tight">{t('login:title')}</h1>
          <p className="text-sm text-fg-muted mt-1">{t('login:tagline')}</p>
          {publicInstanceInfo?.instanceName ? (
            <p className="text-xs font-semibold text-sidebar-bg/90 mt-2 tracking-tight">
              {t('login:instanceSubtitle', { name: publicInstanceInfo.instanceName })}
            </p>
          ) : null}
        </div>

        {/* Main card — wider padding when recovery so bind-after-reset fits without excessive scroll */}
        <div
          className={cn(
            'bg-surface border-2 border-sidebar-bg shadow-brutal-lg',
            recoveryLayout ? 'p-4 sm:p-6' : 'p-6',
          )}
        >
          {authFlow === 'recovery' && (
            <LoginRecoverySection
              initialUsername={recoveryUsernamePrefill}
              onBackToPasswordless={() => setAuthFlow('passwordless')}
            />
          )}

          {/* ── Idle / Submitting ── Login Form */}
          {authFlow === 'passwordless' && (loginState === 'idle' || loginState === 'submitting') && (
            <form data-testid="login-form" onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
              <div>
                <Label htmlFor="username">{t('login:form.username')}</Label>
                <div className="flex gap-2 items-start">
                  <div className="flex-1 min-w-0">
                    <Input
                      id="username"
                      data-testid="login-username-input"
                      autoFocus
                      autoComplete="username"
                      placeholder={t('login:form.usernamePlaceholder')}
                      error={errors.username?.message}
                      {...register('username')}
                    />
                  </div>
                  <Controller
                    name="rememberUsername"
                    control={control}
                    render={({ field }) => {
                      const rememberLabel = field.value
                        ? t('login:form.rememberUsernameTooltipOn')
                        : t('login:form.rememberUsernameTooltipOff');
                      return (
                        <Tooltip content={rememberLabel} position="top">
                          <button
                            type="button"
                            data-testid="login-remember-username-toggle"
                            className={cn(
                              'flex shrink-0 items-center justify-center size-10 border-2 border-fg bg-surface',
                              'hover:bg-bg transition-colors focus:outline-none focus:shadow-accent focus:border-accent',
                              field.value && 'border-accent bg-accent/15 text-accent',
                              !field.value && 'text-fg-muted',
                            )}
                            aria-pressed={field.value}
                            aria-label={rememberLabel}
                            onClick={() => field.onChange(!field.value)}
                          >
                            {field.value ? (
                              <Pin className="size-5" strokeWidth={2} aria-hidden />
                            ) : (
                              <PinOff className="size-5" strokeWidth={2} aria-hidden />
                            )}
                          </button>
                        </Tooltip>
                      );
                    }}
                  />
                </div>
              </div>

              <div className="flex items-center gap-2.5">
                <input
                  id="challengeRequested"
                  type="checkbox"
                  data-testid="login-challenge-toggle"
                  className="size-4 border-2 border-sidebar-bg accent-sidebar-bg cursor-pointer"
                  {...register('challengeRequested')}
                />
                <label
                  htmlFor="challengeRequested"
                  className="text-sm font-medium cursor-pointer select-none text-fg"
                >
                  {t('login:form.requireChallengeCode')}
                </label>
              </div>

              <Button
                type="submit"
                size="lg"
                className="w-full"
                isLoading={loginState === 'submitting'}
                data-testid="login-submit-button"
              >
                {t('login:form.submit')}
              </Button>

              <div className="text-center pt-1">
                <button
                  type="button"
                  data-testid="login-recovery-link"
                  className="text-sm text-fg-muted hover:text-accent underline underline-offset-2"
                  onClick={() => {
                    setRecoveryUsernamePrefill(watch('username').trim());
                    setAuthFlow('recovery');
                  }}
                >
                  {t('login:recovery.useRecoveryLink')}
                </button>
              </div>
            </form>
          )}

          {/* ── Waiting ── Countdown + challenge */}
          {authFlow === 'passwordless' && loginState === 'waiting' && waitingData && (
            <div data-testid="login-waiting-state" className="text-center space-y-5">
              <div className="space-y-2">
                <p className="text-xs font-black uppercase tracking-widest text-fg-muted">
                  {waitingData.challengeCode != null
                    ? t('login:waiting.enterCodeOnDevice')
                    : t('login:waiting.awaitingApproval')}
                </p>
                <div className="flex items-center justify-center gap-2">
                  <span className="size-4 border-2 border-fg border-t-transparent rounded-full animate-spin" />
                  <span className="text-sm text-fg-muted">{t('login:waiting.checkMobileApp')}</span>
                </div>
              </div>

              {/* Challenge code — displayed only when challengeRequested */}
              {waitingData.challengeCode != null && (
                <div data-testid="login-challenge-panel" className="border-2 border-sidebar-bg bg-bg p-5">
                  <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-2">
                    {t('login:waiting.challengeCode')}
                  </p>
                  <p className="font-mono text-6xl font-black text-fg tracking-[0.2em] tabular-nums">
                    {formatChallengeCode(waitingData.challengeCode)}
                  </p>
                  <p className="text-xs text-fg-muted mt-2">{t('login:waiting.enterCodeOnDeviceHint')}</p>
                </div>
              )}

              {/* Countdown */}
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                  {t('login:waiting.timeRemaining')}
                </p>
                <p className={`font-mono text-2xl font-bold tabular-nums ${countdownColor}`}>
                  {formatCountdown(remainingSeconds)}
                </p>
              </div>

              <Button variant="ghost" size="sm" onClick={handleCancel} className="w-full">
                {t('common:buttons.cancel')}
              </Button>
            </div>
          )}

          {/* ── Rejected ── */}
          {authFlow === 'passwordless' && loginState === 'rejected' && (
            <div data-testid="login-rejected-state" className="space-y-4">
              <Alert variant="error" title={t('login:states.rejected.title')}>
                {t('login:states.rejected.message')}
              </Alert>
              <Button variant="secondary" className="w-full" onClick={handleRetry}>
                {t('common:buttons.tryAgain')}
              </Button>
            </div>
          )}

          {/* ── Expired ── */}
          {authFlow === 'passwordless' && loginState === 'expired' && (
            <div data-testid="login-expired-state" className="space-y-4">
              <Alert variant="error" title={t('login:states.expired.title')}>
                {t('login:states.expired.message')}
              </Alert>
              <Button variant="secondary" className="w-full" onClick={handleRetry}>
                {t('common:buttons.tryAgain')}
              </Button>
            </div>
          )}

          {/* ── Error ── */}
          {authFlow === 'passwordless' && loginState === 'error' && (
            <div data-testid="login-error-state" className="space-y-4">
              <Alert variant="error" title={t('login:states.error.title')}>
                {errorMessage ?? t('login:states.error.messageFallback')}
              </Alert>
              <Button variant="secondary" className="w-full" onClick={handleRetry}>
                {t('common:buttons.tryAgain')}
              </Button>
            </div>
          )}
        </div>

        <p className="text-center text-xs text-fg-muted mt-5">
          {t('login:footer', { brand: t('layout:brand') })}
        </p>
        </div>
      </div>
    </div>
  );
}
