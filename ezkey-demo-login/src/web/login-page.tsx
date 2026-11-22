import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, ApiError, type LoginStarted } from './api';
import type { Strings } from './i18n';

type State =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'waiting'; login: LoginStarted; read: boolean }
  | { kind: 'refused' }
  | { kind: 'invalid' }
  | { kind: 'expired' }
  | { kind: 'cancelled' }
  | { kind: 'error'; code: string };

function formatCountdown(seconds: number): string {
  const s = Math.max(0, seconds);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export function LoginPage({ t, configured, onSignedIn }: { t: Strings; configured: boolean; onSignedIn: () => void }) {
  const [state, setState] = useState<State>({ kind: 'idle' });
  const [username, setUsername] = useState('');
  const [challengeRequested, setChallengeRequested] = useState(true);
  const [now, setNow] = useState(() => Date.now());
  const abortRef = useRef<AbortController | null>(null);

  // Long-poll the back end while the request waits on the device.
  const waitingLoginId = state.kind === 'waiting' ? state.login.loginId : null;
  useEffect(() => {
    if (!waitingLoginId) return;
    const controller = new AbortController();
    abortRef.current = controller;
    let stopped = false;
    (async () => {
      while (!stopped) {
        try {
          const outcome = await api.wait(waitingLoginId, controller.signal);
          if (stopped) return;
          if (outcome.state === 'pending') {
            setState((s) => (s.kind === 'waiting' ? { ...s, read: outcome.read } : s));
            continue;
          }
          if (outcome.state === 'approved') onSignedIn();
          else setState({ kind: outcome.state });
          return;
        } catch (err) {
          if (stopped || (err as Error).name === 'AbortError') return;
          setState({ kind: 'error', code: err instanceof ApiError ? err.code : 'unexpected' });
          return;
        }
      }
    })();
    return () => {
      stopped = true;
      controller.abort();
    };
  }, [waitingLoginId, onSignedIn]);

  useEffect(() => {
    if (state.kind !== 'waiting') return;
    const timer = window.setInterval(() => setNow(Date.now()), 500);
    return () => window.clearInterval(timer);
  }, [state.kind]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!username.trim()) {
      setState({ kind: 'error', code: 'invalid-request' });
      return;
    }
    setState({ kind: 'submitting' });
    try {
      const login = await api.startLogin(username.trim(), challengeRequested);
      setNow(Date.now());
      setState({ kind: 'waiting', login, read: false });
    } catch (err) {
      setState({ kind: 'error', code: err instanceof ApiError ? err.code : 'unexpected' });
    }
  }

  function cancel() {
    if (state.kind !== 'waiting') return;
    const { loginId } = state.login;
    abortRef.current?.abort();
    setState({ kind: 'cancelled' });
    void api.cancel(loginId).catch(() => undefined);
  }

  const retry = () => setState({ kind: 'idle' });

  if (state.kind === 'waiting') {
    const remaining = Math.ceil((Date.parse(state.login.expiresAt) - now) / 1000);
    const tone = remaining <= 15 ? 'text-error' : remaining <= 45 ? 'text-warning' : 'text-ink';
    const hasCode = state.login.challengeCode != null;
    return (
      <div data-testid="login-waiting" className="text-center space-y-5">
        <p className="text-xs font-black uppercase tracking-widest">
          {hasCode ? t.waitingTitleChallenge : t.waitingTitle}
        </p>
        {hasCode && (
          <div data-testid="login-challenge" className="border-3 border-ink bg-accent-soft p-5 shadow-brutal">
            <p className="text-[10px] font-black uppercase tracking-widest mb-2">{t.challengeCode}</p>
            <p className="font-mono text-6xl font-black tracking-[0.2em] tabular-nums">
              {String(state.login.challengeCode).padStart(2, '0')}
            </p>
          </div>
        )}
        <div className="flex items-center justify-center gap-2 text-sm">
          <span className="size-4 border-2 border-ink border-t-transparent rounded-full animate-spin" aria-hidden />
          <span data-testid="login-waiting-hint">{state.read ? t.waitingRead : t.waitingHint}</span>
        </div>
        <div>
          <p className="text-[10px] font-black uppercase tracking-widest mb-1">{t.timeRemaining}</p>
          <p data-testid="login-countdown" className={`font-mono text-2xl font-bold tabular-nums ${tone}`}>
            {formatCountdown(remaining)}
          </p>
        </div>
        <button type="button" data-testid="login-cancel" onClick={cancel} className="btn-secondary w-full">
          {t.cancel}
        </button>
      </div>
    );
  }

  const result =
    state.kind === 'refused'
      ? { title: t.refusedTitle, message: t.refusedMessage, testid: 'login-refused' }
      : state.kind === 'invalid'
        ? { title: t.invalidTitle, message: t.invalidMessage, testid: 'login-invalid' }
        : state.kind === 'expired'
          ? { title: t.expiredTitle, message: t.expiredMessage, testid: 'login-expired' }
          : state.kind === 'cancelled'
            ? { title: t.cancelledTitle, message: t.cancelledMessage, testid: 'login-cancelled' }
            : state.kind === 'error'
              ? { title: t.errorTitle, message: t.errors[state.code] ?? t.errors.unexpected, testid: 'login-error' }
              : null;

  if (result) {
    const isError = state.kind !== 'cancelled';
    return (
      <div data-testid={result.testid} className="space-y-4">
        <div role="alert" className={`border-3 border-ink p-4 shadow-brutal ${isError ? 'bg-error-soft' : 'bg-surface-alt'}`}>
          <p className="font-black">{result.title}</p>
          <p className="text-sm mt-1">{result.message}</p>
          {state.kind === 'error' && (
            <p data-testid="login-error-code" className="text-xs font-mono mt-2 opacity-70">
              {t.errorCode}: {state.code}
            </p>
          )}
        </div>
        <button type="button" data-testid="login-retry" onClick={retry} className="btn-secondary w-full">
          {t.tryAgain}
        </button>
      </div>
    );
  }

  return (
    <form data-testid="login-form" onSubmit={submit} className="space-y-5" noValidate>
      {!configured && (
        <div data-testid="login-not-configured" className="border-3 border-ink bg-warning-soft p-3 text-sm">
          {t.notConfigured}
        </div>
      )}
      <div>
        <label htmlFor="username" className="block text-sm font-black mb-1">
          {t.username}
        </label>
        <input
          id="username"
          data-testid="login-username"
          autoFocus
          autoComplete="username"
          spellCheck={false}
          maxLength={128}
          placeholder={t.usernamePlaceholder}
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="w-full border-3 border-ink bg-surface px-3 py-2 font-mono focus:outline-none focus:shadow-brutal-accent"
        />
      </div>
      <label className="flex items-center gap-2.5 text-sm cursor-pointer select-none">
        <input
          type="checkbox"
          data-testid="login-challenge-toggle"
          checked={challengeRequested}
          onChange={(e) => setChallengeRequested(e.target.checked)}
          className="size-4 accent-ink"
        />
        {t.requireChallenge}
      </label>
      <button
        type="submit"
        data-testid="login-submit"
        disabled={state.kind === 'submitting'}
        className="btn-primary w-full"
      >
        {state.kind === 'submitting' ? '…' : t.submit}
      </button>
    </form>
  );
}
