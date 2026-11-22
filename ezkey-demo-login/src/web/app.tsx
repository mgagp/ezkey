import { useCallback, useEffect, useState } from 'react';
import { api, type Me } from './api';
import { HomePage } from './home-page';
import { dictionaries, initialLang, saveLang, type Lang } from './i18n';
import { LoginPage } from './login-page';

type View = { kind: 'loading' } | { kind: 'login'; configured: boolean } | { kind: 'home'; me: Me };

export function App() {
  const [lang, setLang] = useState<Lang>(initialLang);
  const [view, setView] = useState<View>({ kind: 'loading' });
  const t = dictionaries[lang];

  const refresh = useCallback(async () => {
    try {
      setView({ kind: 'home', me: await api.me() });
    } catch {
      const info = await api.info().catch(() => ({ configured: true }));
      setView({ kind: 'login', configured: info.configured });
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  function chooseLang(next: Lang) {
    setLang(next);
    saveLang(next);
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="w-full max-w-lg">
        <div className="flex justify-end gap-1 text-sm mb-2" role="group" aria-label="Language">
          {(['en', 'fr'] as const).map((l, i) => (
            <span key={l} className="flex items-center gap-1">
              {i > 0 && <span className="opacity-40">|</span>}
              <button
                type="button"
                data-testid={`lang-${l}`}
                onClick={() => chooseLang(l)}
                className={lang === l ? 'font-black underline underline-offset-4' : 'opacity-70 hover:opacity-100'}
              >
                {l.toUpperCase()}
              </button>
            </span>
          ))}
        </div>

        <header className="text-center mb-6">
          <div className="inline-block border-3 border-ink bg-accent px-4 py-1 shadow-brutal -rotate-1">
            <h1 className="text-3xl font-black tracking-tight">{t.brand}</h1>
          </div>
          <p className="text-sm mt-3">{t.tagline}</p>
        </header>

        <main className="bg-surface border-3 border-ink p-6 shadow-brutal-lg">
          {view.kind === 'loading' && <p className="text-center text-sm">…</p>}
          {view.kind === 'login' && <LoginPage t={t} configured={view.configured} onSignedIn={refresh} />}
          {view.kind === 'home' && <HomePage t={t} lang={lang} me={view.me} onSignedOut={refresh} />}
        </main>

        <p className="text-center text-xs mt-5 opacity-80">{t.footer}</p>
      </div>
    </div>
  );
}
