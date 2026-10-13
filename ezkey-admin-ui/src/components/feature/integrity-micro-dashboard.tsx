/**
 * Integrity cut 3 micro-dashboard — one strip for the whole Integrity tab (Julie / #624).
 * Separates job state (running / finished / failed) from chain verdict (intact / gaps / violation).
 */

import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, CheckCircle2, Loader2, ShieldAlert, ShieldQuestion } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Tooltip } from '@/components/ui/tooltip';
import { cn, formatDateWithTimezone, formatRelativeTime } from '@/lib/utils';
import {
  abandonIntegrityAsyncJob,
  getCurrentIntegrityAsyncJob,
  isIntegrityAsyncEscapeStatus,
  type IntegrityAsyncJobResponse,
} from '@/lib/integrity-async-jobs';
import {
  hasJobBackedChainGlance,
  integrityMicroDashboardSource,
  isIntegrityChainStatusNonGreen,
  isIntegrityVerdictScopeMismatch,
  resolveIntegrityMicroDashboardTone,
  startIntegrityRunningPoll,
  type IntegrityMicroDashboardTone,
} from '@/lib/integrity-micro-dashboard';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { useToast } from '@/context/use-toast';

const POLL_MS_RUNNING = 3000;

export interface IntegrityMicroDashboardProps {
  job: IntegrityAsyncJobResponse | null;
  onJobChange: (job: IntegrityAsyncJobResponse | null) => void;
  /** When the Integrity tab is focused / mounted. */
  active: boolean;
  /** Current Vérifier picker (YYYY-MM-DD) for #696 mismatch. */
  pickerRange?: { from: string; to: string } | null;
  /** Verdict Instant scope converted to display YYYY-MM-DD. */
  verdictDisplayRange?: { from: string; to: string } | null;
  /** Explicit Revérifier (never silent reuse). */
  onReverify?: () => void;
  reverifyDisabled?: boolean;
  reverifyDisabledReason?: string;
  /** Mid-work non-green: open Remédier without auto tab switch (OQ1). */
  showOpenRemediate?: boolean;
  onOpenRemediate?: () => void;
}

function tileToneClasses(tone: IntegrityMicroDashboardTone): string {
  switch (tone) {
    case 'intact':
      return 'border-fg/25 bg-fg/[0.03] text-fg';
    case 'gaps':
      return 'border-warning/50 bg-warning/10 text-fg';
    case 'violation':
      return 'border-error/50 bg-error/10 text-fg';
    case 'stale':
      return 'border-fg/20 bg-fg/[0.02] text-fg-muted';
    case 'running':
    case 'nightly-running':
      return 'border-fg/30 bg-fg/[0.04] text-fg';
    case 'did-not-complete':
      return 'border-warning/40 bg-warning/5 text-fg';
    default:
      return 'border-fg/15 bg-transparent text-fg-muted';
  }
}

function VerdictIcon({ tone }: { tone: IntegrityMicroDashboardTone }) {
  const className = 'size-4 shrink-0';
  switch (tone) {
    case 'intact':
      return <CheckCircle2 className={cn(className, 'text-fg')} aria-hidden />;
    case 'gaps':
      return <AlertTriangle className={cn(className, 'text-warning')} aria-hidden />;
    case 'violation':
      return <ShieldAlert className={cn(className, 'text-error')} aria-hidden />;
    case 'running':
    case 'nightly-running':
      return <Loader2 className={cn(className, 'animate-spin')} aria-hidden />;
    case 'did-not-complete':
      return <ShieldQuestion className={cn(className, 'text-warning')} aria-hidden />;
    default:
      return <ShieldQuestion className={cn(className, 'text-fg-muted')} aria-hidden />;
  }
}

/**
 * Compact Integrity glance strip above the three atelier modes.
 *
 * @param props job + picker scope + reverify / Remédier actions
 */
export function IntegrityMicroDashboard({
  job,
  onJobChange,
  active,
  pickerRange,
  verdictDisplayRange,
  onReverify,
  reverifyDisabled,
  reverifyDisabledReason,
  showOpenRemediate,
  onOpenRemediate,
}: IntegrityMicroDashboardProps) {
  const { t } = useTranslation('audit-logs');
  const { toast } = useToast();
  const [abandonOpen, setAbandonOpen] = useState(false);
  const [abandoning, setAbandoning] = useState(false);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [arrivalPulse, setArrivalPulse] = useState(false);
  const prevStatusRef = useRef<string | null>(null);
  const mountedAtRef = useRef(Date.now());

  useEffect(() => {
    if (!active) {
      return;
    }
    let cancelled = false;
    async function refresh() {
      try {
        const next = await getCurrentIntegrityAsyncJob();
        if (!cancelled) {
          onJobChange(next);
        }
      } catch {
        // Keep last known; avoid toast spam on tab focus.
      }
    }
    void refresh();
    return () => {
      cancelled = true;
    };
  }, [active, onJobChange]);

  useEffect(() => {
    if (!active || job?.status !== 'RUNNING') {
      return;
    }
    // Immediate poll + interval: setInterval alone waits POLL_MS before the first
    // tick, so a ~50 ms SUCCEEDED job stayed on "Check running…" until then.
    return startIntegrityRunningPoll(() => {
      void getCurrentIntegrityAsyncJob()
        .then((next) => onJobChange(next))
        .catch(() => undefined);
    }, POLL_MS_RUNNING);
  }, [active, job?.status, onJobChange]);

  // 1 s clock so relative "checked" ages while the page stays open.
  useEffect(() => {
    if (!active) {
      return;
    }
    const id = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [active]);

  // ~2 s ring/fade only when a terminal result arrives while the page is open.
  useEffect(() => {
    const status = job?.status ?? null;
    const prev = prevStatusRef.current;
    prevStatusRef.current = status;
    if (prev == null || status == null) {
      return;
    }
    if (prev === 'RUNNING' && status !== 'RUNNING' && Date.now() - mountedAtRef.current > 1500) {
      setArrivalPulse(true);
      const id = window.setTimeout(() => setArrivalPulse(false), 2000);
      return () => window.clearTimeout(id);
    }
  }, [job?.status, job?.jobId]);

  if (!job || job.abandonedAt) {
    return null;
  }

  const tone = resolveIntegrityMicroDashboardTone(job, nowMs);
  const who = job.startedByUsername ?? '—';
  const checkedAt = job.finishedAt ?? job.startedAt;
  const relativeChecked =
    checkedAt != null ? formatRelativeTime(checkedAt) : '—';
  const absoluteChecked =
    checkedAt != null ? formatDateWithTimezone(checkedAt) : '—';
  const source = integrityMicroDashboardSource(job.trigger);
  const scopeMismatch = isIntegrityVerdictScopeMismatch(pickerRange, verdictDisplayRange);
  const escapeSticky = isIntegrityAsyncEscapeStatus(job.status);
  const nonGreen = isIntegrityChainStatusNonGreen(job.chainStatus);
  const showCounters = hasJobBackedChainGlance(job) && job.status === 'SUCCEEDED';

  const verdictLabel = (() => {
    switch (tone) {
      case 'intact':
        return t('integrity.microDashboard.verdict.intact');
      case 'gaps':
        return t('integrity.microDashboard.verdict.gaps', {
          count: job.undeclaredGaps ?? 0,
        });
      case 'violation':
        return t('integrity.microDashboard.verdict.violation', {
          count: job.invalidCheckpoints ?? 0,
        });
      case 'stale':
        return t('integrity.microDashboard.verdict.stale', { when: relativeChecked });
      case 'running':
        return t('integrity.microDashboard.verdict.running');
      case 'nightly-running':
        return t('integrity.microDashboard.verdict.nightlyRunning');
      case 'did-not-complete':
        return t('integrity.microDashboard.verdict.didNotComplete');
      default:
        return t('integrity.microDashboard.verdict.idle');
    }
  })();

  const liveAnnouncement =
    tone === 'intact'
    || tone === 'gaps'
    || tone === 'violation'
    || tone === 'did-not-complete'
      ? t('integrity.microDashboard.ariaResultAvailable', { verdict: verdictLabel })
      : undefined;

  const scopePeriod =
    job.scopeFrom && job.scopeTo
      ? t('integrity.microDashboard.scope.period', {
          from: formatDateWithTimezone(job.scopeFrom),
          to: formatDateWithTimezone(job.scopeTo),
        })
      : null;

  async function confirmAbandon() {
    setAbandoning(true);
    try {
      await abandonIntegrityAsyncJob();
      const next = await getCurrentIntegrityAsyncJob();
      onJobChange(next);
      setAbandonOpen(false);
    } catch (e) {
      toast(getTranslatedApiError(e, t, t('integrity.asyncJob.abandonError')), 'error');
    } finally {
      setAbandoning(false);
    }
  }

  const reverifyButton = onReverify != null && (
    <Tooltip content={reverifyDisabledReason ?? t('integrity.microDashboard.reverify')}>
      <Button
        type="button"
        size="sm"
        variant="secondary"
        disabled={reverifyDisabled}
        data-testid="integrity-micro-dashboard-reverify"
        onClick={() => onReverify()}
      >
        {t('integrity.microDashboard.reverify')}
      </Button>
    </Tooltip>
  );

  return (
    <>
      <div
        className={cn(
          'border-2 px-3 py-2 space-y-2',
          tileToneClasses(tone),
          arrivalPulse && 'ring-2 ring-fg/40 transition-shadow duration-500',
        )}
        data-testid="integrity-micro-dashboard"
        data-tone={tone}
      >
        <div className="flex flex-wrap items-stretch gap-2 text-sm">
          <div
            className={cn(
              'flex min-w-[10rem] flex-1 items-center gap-2 border-2 px-2 py-1.5',
              tileToneClasses(tone),
            )}
            data-testid="integrity-micro-dashboard-verdict"
            aria-live="polite"
            aria-atomic="true"
          >
            <VerdictIcon tone={tone} />
            <span className="min-w-0 font-medium leading-snug">{verdictLabel}</span>
            {liveAnnouncement != null && arrivalPulse && (
              <span className="sr-only">{liveAnnouncement}</span>
            )}
          </div>

          {showCounters && (
            <>
              <StatTile
                testId="integrity-micro-dashboard-checkpoints"
                label={t('integrity.microDashboard.stat.checkpoints')}
                value={job.totalCheckpoints ?? 0}
                muted={(job.totalCheckpoints ?? 0) === 0 && tone === 'intact'}
              />
              <StatTile
                testId="integrity-micro-dashboard-invalid"
                label={t('integrity.microDashboard.stat.invalid')}
                value={job.invalidCheckpoints ?? 0}
                emphasize={(job.invalidCheckpoints ?? 0) > 0 && tone === 'violation'}
                muted={(job.invalidCheckpoints ?? 0) === 0}
              />
              <StatTile
                testId="integrity-micro-dashboard-gaps"
                label={t('integrity.microDashboard.stat.undeclaredGaps')}
                value={job.undeclaredGaps ?? 0}
                emphasize={(job.undeclaredGaps ?? 0) > 0 && tone === 'gaps'}
                muted={(job.undeclaredGaps ?? 0) === 0}
              />
            </>
          )}

          <div
            className="flex min-w-[12rem] flex-1 flex-col justify-center gap-1 border-2 border-fg/15 px-2 py-1.5"
            data-testid="integrity-micro-dashboard-checked"
          >
            <span className="text-xs text-fg-muted">{t('integrity.microDashboard.checked')}</span>
            <span className="text-xs leading-snug">
              <span className="text-fg">{absoluteChecked}</span>
              <span className="text-fg-muted"> · {relativeChecked}</span>
            </span>
            <div className="flex flex-wrap items-center gap-2 pt-0.5">
              {reverifyButton}
              {showOpenRemediate && onOpenRemediate != null && nonGreen && (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  data-testid="integrity-micro-dashboard-open-remediate"
                  onClick={() => onOpenRemediate()}
                >
                  {t('integrity.microDashboard.openRemediate')}
                </Button>
              )}
              {escapeSticky && (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  data-testid="integrity-async-job-abandon"
                  onClick={() => setAbandonOpen(true)}
                >
                  {t('integrity.asyncJob.abandon')}
                </Button>
              )}
            </div>
          </div>
        </div>

        <div
          className="text-xs text-fg-muted flex flex-wrap gap-x-2 gap-y-1"
          data-testid="integrity-micro-dashboard-scope"
        >
          {scopePeriod != null && <span>{scopePeriod}</span>}
          <span>
            {t(`integrity.microDashboard.source.${source}`)}
            {' · '}
            {t('integrity.microDashboard.byAdmin', { who })}
          </span>
          {scopeMismatch && (
            <span
              className="text-warning"
              data-testid="integrity-micro-dashboard-scope-mismatch"
            >
              {t('integrity.microDashboard.scopeMismatch')}
            </span>
          )}
          <Tooltip content={t('integrity.microDashboard.whatIsVerifiedHelp')}>
            <button
              type="button"
              className="underline decoration-dotted underline-offset-2 hover:text-fg"
              data-testid="integrity-micro-dashboard-what-verified"
            >
              {t('integrity.microDashboard.whatIsVerified')}
            </button>
          </Tooltip>
        </div>
      </div>

      <Dialog
        open={abandonOpen}
        onClose={() => {
          if (!abandoning) {
            setAbandonOpen(false);
          }
        }}
        title={t('integrity.asyncJob.abandonConfirmTitle')}
        dismissible={!abandoning}
      >
        <p className="text-sm text-fg-muted mb-4">{t('integrity.asyncJob.abandonConfirmBody')}</p>
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="secondary"
            disabled={abandoning}
            onClick={() => setAbandonOpen(false)}
          >
            {t('integrity.asyncJob.cancel')}
          </Button>
          <Button
            type="button"
            disabled={abandoning}
            data-testid="integrity-async-job-abandon-confirm"
            onClick={() => void confirmAbandon()}
          >
            {abandoning ? t('integrity.asyncJob.abandoning') : t('integrity.asyncJob.abandon')}
          </Button>
        </div>
      </Dialog>
    </>
  );
}

function StatTile({
  label,
  value,
  testId,
  muted,
  emphasize,
}: {
  label: string;
  value: number;
  testId: string;
  muted?: boolean;
  emphasize?: boolean;
}) {
  return (
    <div
      className={cn(
        'flex min-w-[5.5rem] flex-col justify-center border-2 px-2 py-1.5',
        emphasize ? 'border-fg/40 bg-fg/[0.04]' : 'border-fg/15',
        muted && 'text-fg-muted',
      )}
      data-testid={testId}
    >
      <span className="text-xs text-fg-muted">{label}</span>
      <span className="font-mono text-sm tabular-nums">{value}</span>
    </div>
  );
}
