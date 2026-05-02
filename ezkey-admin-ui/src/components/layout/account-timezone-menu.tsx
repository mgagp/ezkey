import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Clock } from 'lucide-react';
import { useAuth } from '@/context/use-auth';
import { useDisplayTimezone } from '@/context/use-display-timezone';
import type { DisplayTimezoneMode } from '@/lib/display-timezone-pref';

/**
 * Clock + short label at first level; opens a compact panel for display timezone (rare operation).
 */
export function DisplayTimezoneMenu() {
  const { t } = useTranslation('layout');
  const { session } = useAuth();
  const { mode, setMode, effectiveTimeZoneId, tenantTimeZoneRaw } = useDisplayTimezone();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const tenantScoped = typeof session?.tenantId === 'number';

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  const handleMode = (next: DisplayTimezoneMode) => {
    setMode(next);
  };

  if (!session) return null;

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        data-testid="app-display-timezone-button"
        onClick={() => setOpen((o) => !o)}
        className="inline-flex items-center gap-1.5 rounded-sm px-2 py-1.5 text-sm font-bold text-fg hover:bg-fg/10 border-2 border-transparent hover:border-fg/20 transition-colors shrink-0"
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={t('header.account.displayTimezone')}
      >
        <Clock className="size-3.5 shrink-0 text-fg-muted" aria-hidden />
        <span>{t('header.account.timezoneButtonShort')}</span>
      </button>

      {open && (
        <div
          role="dialog"
          aria-label={t('header.account.displayTimezone')}
          data-testid="app-display-timezone-panel"
          className="absolute right-0 top-full z-50 mt-1 min-w-[17rem] border-2 border-fg bg-surface shadow-brutal p-3 text-left"
        >
          <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-2">
            {t('header.account.displayTimezone')}
          </p>
          <div className="space-y-2 mb-3">
            <label className="flex items-start gap-2 cursor-pointer text-sm">
              <input
                type="radio"
                name="display-tz"
                className="mt-0.5"
                checked={mode === 'local'}
                onChange={() => handleMode('local')}
              />
              <span>
                <span className="font-bold block">{t('header.account.modeLocal')}</span>
                <span className="text-xs text-fg-muted">{t('header.account.modeLocalHint')}</span>
              </span>
            </label>
            {tenantScoped && (
              <label className="flex items-start gap-2 cursor-pointer text-sm">
                <input
                  type="radio"
                  name="display-tz"
                  className="mt-0.5"
                  checked={mode === 'tenant'}
                  onChange={() => handleMode('tenant')}
                />
                <span>
                  <span className="font-bold block">{t('header.account.modeTenant')}</span>
                  <span className="text-xs text-fg-muted">
                    {tenantTimeZoneRaw
                      ? t('header.account.modeTenantHint', { zone: tenantTimeZoneRaw })
                      : t('header.account.modeTenantNoZone')}
                  </span>
                </span>
              </label>
            )}
          </div>
          {mode === 'tenant' && tenantScoped && (
            <p className="text-[10px] text-fg-muted border-t-2 border-fg/15 pt-2">
              {effectiveTimeZoneId
                ? t('header.account.effectiveZone', { zone: effectiveTimeZoneId })
                : t('header.account.effectiveFallbackLocal')}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
