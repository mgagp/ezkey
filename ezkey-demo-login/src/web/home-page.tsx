import { useState } from 'react';
import { api, type Me } from './api';
import type { Lang, Strings } from './i18n';

function formatTime(iso: string, lang: Lang): string {
  return new Date(iso).toLocaleString(lang === 'fr' ? 'fr-CA' : 'en-CA', { dateStyle: 'medium', timeStyle: 'short' });
}

export function HomePage({ t, lang, me, onSignedOut }: { t: Strings; lang: Lang; me: Me; onSignedOut: () => void }) {
  const [busy, setBusy] = useState(false);

  async function logout() {
    setBusy(true);
    await api.logout().catch(() => undefined);
    onSignedOut();
  }

  return (
    <div data-testid="home" className="space-y-6">
      <div className="border-3 border-ink bg-success-soft p-4 shadow-brutal">
        <p className="text-xs font-black uppercase tracking-widest">{t.homeTitle}</p>
        <p data-testid="home-welcome" className="text-2xl font-black mt-1">
          {t.homeWelcome.replace('{name}', me.user.displayName)}
        </p>
      </div>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="font-black">{t.homeUsername}</dt>
        <dd data-testid="home-username" className="font-mono">{me.user.username}</dd>
        <dt className="font-black">{t.homeSignedInAt}</dt>
        <dd>{formatTime(me.session.createdAt, lang)}</dd>
        <dt className="font-black">{t.homeExpiresAt}</dt>
        <dd>{formatTime(me.session.expiresAt, lang)}</dd>
      </dl>

      <section className="border-3 border-ink bg-surface-alt p-4">
        <h2 className="font-black mb-2">{t.howTitle}</h2>
        <ol className="list-decimal pl-5 space-y-1.5 text-sm">
          {t.how.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      </section>

      <button type="button" data-testid="home-logout" disabled={busy} onClick={logout} className="btn-secondary w-full">
        {t.logout}
      </button>
    </div>
  );
}
