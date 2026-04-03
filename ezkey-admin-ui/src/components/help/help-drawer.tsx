import { useEffect, useId } from 'react';
import { useTranslation } from 'react-i18next';
import { CircleHelp, X } from 'lucide-react';
import type { HelpPatternId, HelpTopicId } from '@/lib/help-topics';

interface HelpDrawerProps {
  open: boolean;
  onClose: () => void;
  topicId: HelpTopicId;
  patternId: HelpPatternId | null;
  isGlobalAdmin: boolean;
  showDemoExtra: boolean;
}

/**
 * Right-side help panel (neo-brutalism). Content is driven by i18n keys under `help:topics.<topicId>`.
 */
export function HelpDrawer({
  open,
  onClose,
  topicId,
  patternId,
  isGlobalAdmin,
  showDemoExtra,
}: HelpDrawerProps) {
  const { t } = useTranslation('help');
  const titleId = useId();

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  if (!open) return null;

  const base = patternId ? `patterns.${patternId}` : `topics.${topicId}`;
  const demoExtraText = patternId ? '' : t(`${base}.demoExtra`, { defaultValue: '' });

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/*
        Backdrop: slight navy tint (sidebar family) so the overlay reads as “focus mode”
        without matching the main content bg exactly.
      */}
      <div
        className="fixed inset-0 bg-sidebar-bg/35"
        onClick={onClose}
        aria-hidden="true"
      />
      {/*
        Panel: left rail = EZKey logo blue (#3076DF); header band = sidebar navy — same
        vocabulary as AppShell, so Help is instantly recognizable but not a new theme.
      */}
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="relative z-10 h-full w-full max-w-md flex flex-col border-2 border-fg border-l-[6px] border-l-[#3076DF] bg-surface shadow-brutal-lg animate-[slideIn_0.2s_ease-out_forwards]"
      >
        <div className="flex items-center justify-between gap-3 px-4 py-3 border-b-2 border-fg bg-sidebar-bg shrink-0">
          <div className="flex items-center gap-2.5 min-w-0">
            <span
              className="flex size-8 shrink-0 items-center justify-center border-2 border-sidebar-fg/30 bg-sidebar-bg text-accent"
              aria-hidden
            >
              <CircleHelp className="size-4" strokeWidth={2.5} />
            </span>
            <div className="min-w-0">
              <p className="text-[9px] font-black uppercase tracking-[0.25em] text-sidebar-fg/70">
                {t('drawer.badge')}
              </p>
              <h2
                id={titleId}
                className="text-xs font-black uppercase tracking-widest text-sidebar-fg truncate"
              >
                {t(`${base}.title`)}
              </h2>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 hover:bg-sidebar-fg/15 transition-colors shrink-0 text-sidebar-fg"
            aria-label={t('drawer.close')}
          >
            <X className="size-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-5 space-y-4 text-sm text-fg leading-relaxed bg-surface">
          <p className="text-fg-muted font-medium border-l-2 border-accent pl-3 -ml-0.5">
            {t(`${base}.summary`)}
          </p>
          <div className="whitespace-pre-wrap">{t(`${base}.body`)}</div>

          {!patternId && topicId === 'dashboard' && (
            <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg-muted">
              {t('topics.dashboard.authHealth')}
            </p>
          )}

          {!patternId && topicId === 'dashboard' && isGlobalAdmin && (
            <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg">
              {t('topics.dashboard.globalContext')}
            </p>
          )}
          {!patternId && topicId === 'dashboard' && !isGlobalAdmin && (
            <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg">
              {t('topics.dashboard.tenantContext')}
            </p>
          )}

          {!patternId && topicId === 'encryption-keys' && isGlobalAdmin && (
            <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg">
              {t('topics.encryption-keys.globalOps')}
            </p>
          )}
          {!patternId && topicId === 'encryption-keys' && (
            <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg-muted text-xs leading-relaxed">
              {t('topics.encryption-keys.developerContext')}
            </p>
          )}

          {!patternId && topicId === 'audit-logs' && (
            <>
              <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg">
                {t('topics.audit-logs.eventTypeVsStatus')}
              </p>
              <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg-muted">
                {t('topics.audit-logs.statusLegend')}
              </p>
              <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg">
                {t('topics.audit-logs.mfaAndLogin')}
              </p>
              <p className="whitespace-pre-wrap border-t-2 border-fg/15 pt-4 text-fg-muted text-sm">
                {t('topics.audit-logs.integrityNote')}
              </p>
            </>
          )}

          {showDemoExtra && demoExtraText.trim() !== '' && (
            <div className="border-2 border-accent bg-accent/10 p-3 shadow-[3px_3px_0_0_var(--color-accent-dark)]">
              <p className="text-[10px] font-black uppercase tracking-widest text-accent-dark mb-2">
                {t('demo.sectionTitle')}
              </p>
              <p className="whitespace-pre-wrap text-sm text-fg">{demoExtraText}</p>
            </div>
          )}
        </div>
        <div className="px-4 py-3 border-t-2 border-fg bg-bg text-[10px] text-fg-muted text-center font-mono tracking-tight">
          {t('drawer.shortcutHint')}
        </div>
      </aside>
    </div>
  );
}
