import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '@/context/auth-context';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { formatChallengeCode, formatCountdown } from '@/lib/utils';
import type { AdminLoginResponseDto } from '@/generated/admin-api/model';

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

const loginSchema = z.object({
  username: z.string().min(3, 'At least 3 characters').max(50),
  challengeRequested: z.boolean(),
});
type LoginForm = z.infer<typeof loginSchema>;

type LoginState = 'idle' | 'submitting' | 'waiting' | 'rejected' | 'expired' | 'error';

interface WaitingData {
  authAttemptId: number;
  challengeCode: number | null;
  expiresAt: string;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, isAuthenticated } = useAuth();

  const [loginState, setLoginState] = useState<LoginState>('idle');
  const [waitingData, setWaitingData] = useState<WaitingData | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  // Guards against race conditions — once a final state is set, ignore further callbacks.
  const finalStatusRef = useRef<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const { register, handleSubmit, formState: { errors } } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { challengeRequested: false },
  });

  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated) navigate('/dashboard', { replace: true });
  }, [isAuthenticated, navigate]);

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
        const res = await fetch(`${BASE_URL}/api/v1/admin/auth/passwordless-wait`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            authAttemptId: waitingData.authAttemptId,
            ...(waitingData.challengeCode != null && { challengeCode: waitingData.challengeCode }),
          }),
          signal: controller.signal,
        });

        const data: AdminLoginResponseDto = await res.json();

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
          setErrorMessage(data.message ?? 'Authentication failed. Please try again.');
        }
      } catch (err) {
        if ((err as Error).name === 'AbortError') return;
        if (finalStatusRef.current !== null) return;
        finalStatusRef.current = 'ERROR';
        setLoginState('error');
        setErrorMessage('Connection lost. Please check your network and try again.');
      }
    };

    void doWait();
    return () => { controller.abort(); };
  }, [loginState, waitingData, login, navigate]);

  const onSubmit = async (data: LoginForm) => {
    setLoginState('submitting');
    setErrorMessage(null);
    try {
      const res = await fetch(`${BASE_URL}/api/v1/admin/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: data.username.trim(), challengeRequested: data.challengeRequested, nonBlocking: true }),
      });
      const result: AdminLoginResponseDto = await res.json();
      if (res.ok && result.authAttemptId && result.expiresAt) {
        setWaitingData({
          authAttemptId: result.authAttemptId,
          challengeCode: result.challengeCode ?? null,
          expiresAt: result.expiresAt,
        });
        setLoginState('waiting');
      } else {
        setLoginState('error');
        setErrorMessage(result.message ?? 'Login failed. Check your username.');
      }
    } catch {
      setLoginState('error');
      setErrorMessage('Could not reach the server. Is the Admin API running?');
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

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center p-4">
      <div className="w-full max-w-sm">

        {/* Brand header */}
        <div className="mb-8 text-center">
          <p className="text-[10px] font-black uppercase tracking-[0.4em] text-sidebar-bg mb-2">
            EZKey
          </p>
          <h1 className="text-3xl font-black text-sidebar-bg tracking-tight">Admin Console</h1>
          <p className="text-sm text-fg-muted mt-1">Passwordless · Secure · Simple</p>
        </div>

        {/* Main card */}
        <div className="bg-surface border-2 border-sidebar-bg shadow-brutal-lg p-6">

          {/* ── Idle / Submitting ── Login Form */}
          {(loginState === 'idle' || loginState === 'submitting') && (
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
              <div>
                <Label htmlFor="username">Username</Label>
                <Input
                  id="username"
                  autoFocus
                  autoComplete="username"
                  placeholder="your.username"
                  error={errors.username?.message}
                  {...register('username')}
                />
              </div>

              <div className="flex items-center gap-2.5">
                <input
                  id="challengeRequested"
                  type="checkbox"
                  className="size-4 border-2 border-sidebar-bg accent-sidebar-bg cursor-pointer"
                  {...register('challengeRequested')}
                />
                <label
                  htmlFor="challengeRequested"
                  className="text-sm font-medium cursor-pointer select-none text-fg"
                >
                  Require challenge code
                </label>
              </div>

              <Button type="submit" size="lg" className="w-full" isLoading={loginState === 'submitting'}>
                Login with EZKey
              </Button>
            </form>
          )}

          {/* ── Waiting ── Countdown + challenge */}
          {loginState === 'waiting' && waitingData && (
            <div className="text-center space-y-5">
              <div className="space-y-2">
                <p className="text-xs font-black uppercase tracking-widest text-fg-muted">
                  {waitingData.challengeCode != null ? 'Enter code on device' : 'Awaiting approval'}
                </p>
                <div className="flex items-center justify-center gap-2">
                  <span className="size-4 border-2 border-fg border-t-transparent rounded-full animate-spin" />
                  <span className="text-sm text-fg-muted">Check your EZKey mobile app</span>
                </div>
              </div>

              {/* Challenge code — displayed only when challengeRequested */}
              {waitingData.challengeCode != null && (
                <div className="border-2 border-sidebar-bg bg-bg p-5">
                  <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-2">
                    Challenge Code
                  </p>
                  <p className="font-mono text-6xl font-black text-fg tracking-[0.2em] tabular-nums">
                    {formatChallengeCode(waitingData.challengeCode)}
                  </p>
                  <p className="text-xs text-fg-muted mt-2">Enter this 2-digit code on your device</p>
                </div>
              )}

              {/* Countdown */}
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                  Time remaining
                </p>
                <p className={`font-mono text-2xl font-bold tabular-nums ${countdownColor}`}>
                  {formatCountdown(remainingSeconds)}
                </p>
              </div>

              <Button variant="ghost" size="sm" onClick={handleCancel} className="w-full">
                Cancel
              </Button>
            </div>
          )}

          {/* ── Rejected ── */}
          {loginState === 'rejected' && (
            <div className="space-y-4">
              <Alert variant="error" title="Access Denied">
                The authentication request was rejected on your device.
              </Alert>
              <Button variant="secondary" className="w-full" onClick={handleRetry}>
                Try Again
              </Button>
            </div>
          )}

          {/* ── Expired ── */}
          {loginState === 'expired' && (
            <div className="space-y-4">
              <Alert variant="error" title="Request Expired">
                The authentication request timed out. Please try again.
              </Alert>
              <Button variant="secondary" className="w-full" onClick={handleRetry}>
                Try Again
              </Button>
            </div>
          )}

          {/* ── Error ── */}
          {loginState === 'error' && (
            <div className="space-y-4">
              <Alert variant="error" title="Authentication Error">
                {errorMessage ?? 'An unexpected error occurred.'}
              </Alert>
              <Button variant="secondary" className="w-full" onClick={handleRetry}>
                Try Again
              </Button>
            </div>
          )}
        </div>

        <p className="text-center text-xs text-fg-muted mt-5">
          Powered by{' '}
          <span className="font-bold text-sidebar-bg">EZKey</span>
          {' '}— Eat Your Own Dog Food
        </p>
      </div>
    </div>
  );
}
