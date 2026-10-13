/**
 * Integrity cut 3 micro-dashboard — one strip above Observer / Verify / Remediate (Julie D4).
 * Replaces the text async-job banner; job state is separate from chain verdict.
 */

import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  AlertTriangle,
  CheckCircle2,
  Loader2,
  Moon,
  RefreshCw,
  ShieldAlert,
  XCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { formatDateWithTimezone, formatRelativeTime } from '@/lib/utils';
import { useDisplayTimezone } from '@/context/use-display-timezone';
import {
  abandonIntegrityAsyncJob,
  getCurrentIntegrityAsyncJob,
  isIntegrityAsyncEscapeStatus,
  type IntegrityAsyncJobResponse,
} from '@/lib/integrity-async-jobs';
import {
  integrityVerdictFinishedAt,
  integrityVerdictSourceKey,
  isIntegrityVerdictScopeMismatch,
  resolveIntegrityGlanceTone,
  type IntegrityGlanceTone,
} from '@/lib/integrity-micro-dashboard';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { useToast } from '@/context/use-toast';

const POLL_MS_RUNNING = 3000;
const HIGHLIGHT_MS = 2000;

export interface IntegrityMicroDashboardProps {
  /** Job shown on the strip (RUNNING current or last verdict). */
  job: IntegrityAsyncJobResponse | null;
  onJobChange: (job: IntegrityAsyncJobResponse | null) => void;
  /** When the Integrity tab is focused / mounted. */
  active: boolean;
  /** Display YYYY-MM-DD for the Vérifier filter (mismatch #696). */
  filterRange?: { from: string; to: string } | null;
  /** Display YYYY-MM-DD for the verdict scope. */
  verdictDisplayRange?: { from: string; to: string } | null;
  /** Explicit Revérifier (always allowed when not RUNNING / Escape-sticky). */
  onReverify: () => void;
  reverifyDisabled?: boolean;
  /** Mid-work non-green: open Remédier without auto tab switch. */
  showOpenRemediate?: boolean;
  onOpenRemediate?: () => void;
}

function toneTileClasses(tone: IntegrityGlanceTone, highlight: boolean): string {
  const ring = highlight ? ' ring-2 ring-accent ring-offset-1 animate-pulse' : '';
  switch (tone) {
    case 'intact':
      return `border-success/40 bg-success/10 text-fg${ring}`;
    case 'gaps':
      return `border-warning/50 bg-warning/10 text-fg${ring}`;
    case 'violation':
      return `border-error/50 bg-error/10 text-fg${ring}`;
    case 'stale':
      return `border-fg/20 bg-fg/[0.04] text-fg-muted${ring}`;
    case 'did-not-complete':
      return `border-warning/40 bg-warning/5 text-fg${ring}`;
    case 'running':
    case 'nightly':
      return `border-fg/30 bg-fg/[0.04] text-fg${ring}`;
    default:
      return `border-fg/15 bg-fg/[0.02] text-fg-muted${ring}`;
  }
}

function ToneIcon({ tone }: { tone: IntegrityGlanceTone }) {
  const className = 'size-4 shrink-0';
  switch (tone) {
    case 'intact':
      return <CheckCircle2 className={`${className} text-success`} aria-hidden />;
    case 'gaps':
      return <AlertTriangle className={`${className} text-warning`} aria-hidden />;
    case 'violation':
      return <ShieldAlert className={`${className} text-error`} aria-hidden />;
    case 'did-not-complete':
      return <XCircle className={`${className} text-warning`} aria-hidden />;
    case 'nightly':
      return <Moon className={`${className} text-fg-muted`} aria-hidden />;
    case 'running':
      return <Loader2 className={`${className} animate-spin text-fg-muted`} aria-hidden />;
    default:
      return <CheckCircle2 className={`${className} text-fg-muted`} aria-hidden />;
  }
}

function verdictLabel(
  tone: IntegrityGlanceTone,
  job: IntegrityAsyncJobResponse | null,
  t: (key: string, opts?: Record<string, unknown>) => string,
): string {
  switch (tone) {
    case 'intact':
      return t('integrity.microDashboard.verdict.intact');
    case 'gaps':
      return t('integrity.microDashboard.verdict.gaps', {
        count: job?.undeclaredGaps ?? 0,
      });
    case 'violation': {
      const n =
        (job?.invalidCheckpoints ?? 0) > 0
          ? (job?.invalidCheckpoints ?? 0)
          : (job?.entryHmacViolations ?? 0);
      return t('integrity.microDashboard.verdict.violation', { count: n });
    }
    case 'did-not-complete':
      return t('integrity.microDashboard.verdict.didNotComplete');
    case 'running':
      return t('integrity.microDashboard.verdict.running');
    case 'nightly':
      return t('integrity.microDashboard.verdict.nightly');
    case 'stale':
      return t('integrity.microDashboard.verdict.stale', {
        when: job?.finishedAt
          ? formatRelativeTime(job.finishedAt)
          : '—',
      });
    default:
      return t('integrity.microDashboard.verdict.idle');
  }
}

export function IntegrityMicroDashboard({
  job,
  onJobChange,
  active,
  filterRange,
  verdictDisplayRange,
  onReverify,
  reverifyDisabled = false,
  showOpenRemediate = false,
  onOpenRemediate,
}: IntegrityMicroDashboardProps) {
  const { t } = useTranslation('audit-logs');
  const { toast } = useToast();
  const { effectiveTimeZoneId } = useDisplayTimezone();
  const [abandonOpen, setAbandonOpen] = useState(false);
  const [abandoning, setAbandoning] = useState(false);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [highlight, setHighlight] = useState(false);
  const prevStatusRef = useRef<string | null>(null);
  const mountedRef = useRef(false);

  // 1 s clock so relative "checked" ages while the page stays open (dashboard pattern).
  useEffect(() => {
    if (!active) {
      return;
    }
    const id = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [active]);

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
    const id = window.setInterval(() => {
      void getCurrentIntegrityAsyncJob()
        .then((next) => onJobChange(next))
        .catch(() => undefined);
    }, POLL_MS_RUNNING);
    return () => window.clearInterval(id);
  }, [active, job?.status, onJobChange]);

  // New-result signal only when status transitions while the page is open (not on first paint).
  useEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      prevStatusRef.current = job?.status ?? null;
      return;
    }
    const prev = prevStatusRef.current;
    const next = job?.status ?? null;
    prevStatusRef.current = next;
    if (prev === 'RUNNING' && next != null && next !== 'RUNNING') {
      setHighlight(true);
      const id = window.setTimeout(() => setHighlight(false), HIGHLIGHT_MS);
      return () => window.clearTimeout(id);
    }
  }, [job?.status]);

  const tone = resolveIntegrityGlanceTone(job, nowMs);
  const checkedAt = integrityVerdictFinishedAt(job);
  // Touch nowMs so relative time re-renders each second.
  void nowMs;
  const relativeChecked =
    checkedAt != null && tone !== 'running' && tone !== 'nightly'
      ? formatRelativeTime(checkedAt)
      : null;
  const absoluteChecked =
    checkedAt != null
      ? formatDateWithTimezone(checkedAt, effectiveTimeZoneId)
      : null;
  const sourceKey = integrityVerdictSourceKey(job);
  const who = job?.startedByUsername ?? '—';
  const scopeMismatch = isIntegrityVerdictScopeMismatch(filterRange, verdictDisplayRange);
  const ariaLive =
    highlight && tone !== 'running' && tone !== 'nightly'
      ? t('integrity.microDashboard.ariaResult', {
          verdict: verdictLabel(tone, job, t),
        })
      : undefined;

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

  if (!job || job.abandonedAt) {
    return (
      <div
        className="border-2 border-fg/15 bg-fg/[0.02] px-3 py-2 text-sm text-fg-muted"
        data-testid="integrity-micro-dashboard"
        role="status"
      >
        {t('integrity.microDashboard.verdict.idle')}
        <div className="mt-2 flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            variant="secondary"
            className="gap-1.5"
            data-testid="integrity-micro-dashboard-reverify"
            disabled={reverifyDisabled}
            onClick={() => onReverify()}
          >
            <RefreshCw className="size-3.5" />
            {t('integrity.microDashboard.reverify')}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <>
      <div
        className="border-2 border-fg space-y-2 px-3 py-2"
        data-testid="integrity-micro-dashboard"
      >
        <div className="flex flex-wrap items-stretch gap-2">
          <div
            className={`min-w-[10rem] flex-1 border-2 px-2 py-1.5 flex items-center gap-2 ${toneTileClasses(tone, highlight)}`}
            data-testid="integrity-micro-dashboard-verdict"
            role="status"
            aria-live="polite"
            aria-atomic="true"
          >
            <ToneIcon tone={tone} />
            <span className="text-sm font-bold leading-snug">
              {verdictLabel(tone, job, t)}
            </span>
            {ariaLive ? <span className="sr-only">{ariaLive}</span> : null}
          </div>

          {tone !== 'running'
            && tone !== 'nightly'
            && tone !== 'did-not-complete'
            && tone !== 'idle' && (
            <>
              <StatTile
                label={t('integrity.microDashboard.stat.checkpoints')}
                value={job.totalCheckpoints}
                testId="integrity-micro-dashboard-checkpoints"
              />
              <StatTile
                label={t('integrity.microDashboard.stat.invalid')}
                value={job.invalidCheckpoints}
                emphasize={(job.invalidCheckpoints ?? 0) > 0}
                testId="integrity-micro-dashboard-invalid"
              />
              <StatTile
                label={t('integrity.microDashboard.stat.gaps')}
                value={job.undeclaredGaps}
                emphasize={(job.undeclaredGaps ?? 0) > 0}
                warning
                testId="integrity-micro-dashboard-gaps"
              />
            </>
          )}

          <div className="min-w-[9rem] flex-1 border-2 border-fg/20 px-2 py-1.5 flex flex-col justify-center gap-1">
            <span className="text-[10px] uppercase tracking-wider text-fg-muted font-bold">
              {t('integrity.microDashboard.stat.checked')}
            </span>
            {relativeChecked != null ? (
              <span className="text-xs text-fg" data-testid="integrity-micro-dashboard-checked">
                {relativeChecked}
                {absoluteChecked ? (
                  <span className="text-fg-muted"> · {absoluteChecked}</span>
                ) : null}
              </span>
            ) : (
              <span className="text-xs text-fg-muted">—</span>
            )}
            <div className="flex flex-wrap gap-1.5 mt-0.5">
              <Button
                type="button"
                size="sm"
                variant="secondary"
                className="gap-1 h-7"
                data-testid="integrity-micro-dashboard-reverify"
                disabled={reverifyDisabled || job.status === 'RUNNING'}
                onClick={() => onReverify()}
              >
                <RefreshCw className="size-3" />
                {t('integrity.microDashboard.reverify')}
              </Button>
              {showOpenRemediate && onOpenRemediate && (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  className="h-7"
                  data-testid="integrity-micro-dashboard-open-remediate"
                  onClick={() => onOpenRemediate()}
                >
                  {t('integrity.microDashboard.openRemediate')}
                </Button>
              )}
              {isIntegrityAsyncEscapeStatus(job.status) && (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  className="h-7"
                  data-testid="integrity-async-job-abandon"
                  onClick={() => setAbandonOpen(true)}
                >
                  {t('integrity.asyncJob.abandon')}
                </Button>
              )}
            </div>
          </div>
        </div>

        <p
          className="text-xs text-fg-muted"
          data-testid="integrity-micro-dashboard-scope"
        >
          <span className="font-semibold text-fg/80">
            {t('integrity.microDashboard.scopeLabel')}:
          </span>{' '}
          {verdictDisplayRange?.from && verdictDisplayRange?.to
            ? t('integrity.microDashboard.scopePeriod', {
                from: verdictDisplayRange.from,
                to: verdictDisplayRange.to,
              })
            : '—'}
          {' · '}
          {t(`integrity.microDashboard.source.${sourceKey}`)}
          {' · '}
          {t('integrity.microDashboard.byAuthor', { who })}
        </p>

        {scopeMismatch && (
          <p
            className="text-xs text-warning"
            data-testid="integrity-micro-dashboard-scope-mismatch"
          >
            {t('integrity.microDashboard.scopeMismatch')}
          </p>
        )}
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
  emphasize = false,
  warning = false,
  testId,
}: {
  label: string;
  value: number | null | undefined;
  emphasize?: boolean;
  warning?: boolean;
  testId: string;
}) {
  const zero = value == null || value === 0;
  return (
    <div
      className={`min-w-[4.5rem] border-2 px-2 py-1.5 flex flex-col justify-center ${
        emphasize
          ? warning
            ? 'border-warning/40 bg-warning/5'
            : 'border-error/40 bg-error/5'
          : 'border-fg/15 bg-fg/[0.02]'
      }`}
      data-testid={testId}
    >
      <span className="text-[10px] uppercase tracking-wider text-fg-muted font-bold">{label}</span>
      <span className={`text-sm font-bold tabular-nums ${zero ? 'text-fg-muted' : 'text-fg'}`}>
        {value ?? '—'}
      </span>
    </div>
  );
}
