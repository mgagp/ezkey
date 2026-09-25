/**
 * Integrity async job banner — one strip for the whole Integrity tab (Julie lock).
 */

import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { formatRelativeTime } from '@/lib/utils';
import {
  abandonIntegrityAsyncJob,
  getCurrentIntegrityAsyncJob,
  type IntegrityAsyncJobResponse,
  type IntegrityAsyncJobStatus,
  type IntegrityAsyncJobType,
} from '@/lib/integrity-async-jobs';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { useToast } from '@/context/use-toast';

const POLL_MS_RUNNING = 3000;

function jobTypeLabelKey(type: IntegrityAsyncJobType): string {
  switch (type) {
    case 'VERIFY_CHAIN_RANGE':
      return 'integrity.asyncJob.type.verifyChain';
    case 'VERIFY_ENTRY_HMAC_RANGE':
      return 'integrity.asyncJob.type.verifyEntry';
    case 'RUN_VALIDATION':
      return 'integrity.asyncJob.type.runValidation';
    default:
      return 'integrity.asyncJob.type.unknown';
  }
}

function isEscapeStatus(status: IntegrityAsyncJobStatus): boolean {
  return status === 'EXPIRED' || status === 'CANCELLED' || status === 'INTERRUPTED';
}

export interface IntegrityAsyncJobBannerProps {
  job: IntegrityAsyncJobResponse | null;
  onJobChange: (job: IntegrityAsyncJobResponse | null) => void;
  /** When the Integrity tab is focused / mounted. */
  active: boolean;
}

export function IntegrityAsyncJobBanner({ job, onJobChange, active }: IntegrityAsyncJobBannerProps) {
  const { t } = useTranslation('audit-logs');
  const { toast } = useToast();
  const [abandonOpen, setAbandonOpen] = useState(false);
  const [abandoning, setAbandoning] = useState(false);

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

  if (!job || job.abandonedAt) {
    return null;
  }

  const typeLabel = t(jobTypeLabelKey(job.type));
  const who = job.startedByUsername ?? '—';
  const since =
    job.startedAt != null ? formatRelativeTime(job.startedAt) : '—';

  let message: string;
  switch (job.status) {
    case 'RUNNING':
      message = t('integrity.asyncJob.banner.running', { jobTypeLabel: typeLabel, since, who });
      break;
    case 'SUCCEEDED':
      message = t('integrity.asyncJob.banner.succeeded', {
        jobTypeLabel: typeLabel,
        summary: job.resultSummary ?? '—',
      });
      break;
    case 'FAILED':
      message = t('integrity.asyncJob.banner.failed', {
        jobTypeLabel: typeLabel,
        reason: truncate(job.errorSummary ?? job.resultSummary ?? '—', 120),
      });
      break;
    case 'EXPIRED':
      message = t('integrity.asyncJob.banner.expired', { jobTypeLabel: typeLabel, who });
      break;
    case 'CANCELLED':
    case 'INTERRUPTED':
      message = t('integrity.asyncJob.banner.interrupted', {
        jobTypeLabel: typeLabel,
        summary: job.resultSummary ?? job.errorSummary ?? '—',
      });
      break;
    default:
      message = t('integrity.asyncJob.banner.interrupted', {
        jobTypeLabel: typeLabel,
        summary: '—',
      });
  }

  // RUNNING must not look like healthy OK (no success/green styling).
  const tone =
    job.status === 'RUNNING'
      ? 'border-fg/30 bg-fg/[0.04]'
      : job.status === 'SUCCEEDED'
        ? 'border-fg/20 bg-fg/[0.03]'
        : 'border-warning/40 bg-warning/5';

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

  return (
    <>
      <div
        className={`border-2 px-3 py-2 text-sm flex flex-wrap items-center gap-2 ${tone}`}
        data-testid="integrity-async-job-banner"
        role="status"
      >
        <span className="min-w-0 flex-1">{message}</span>
        {isEscapeStatus(job.status) && (
          <Button
            type="button"
            size="sm"
            variant="secondary"
            className="ml-auto shrink-0"
            data-testid="integrity-async-job-abandon"
            onClick={() => setAbandonOpen(true)}
          >
            {t('integrity.asyncJob.abandon')}
          </Button>
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

function truncate(value: string, max: number): string {
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`;
}
