import { ShieldAlert, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Tooltip } from '@/components/ui/tooltip';
import type { EntryHmacDisplayState } from '@/lib/integrity-investigation-session';

export function EntryHmacBadge({ state }: { state: EntryHmacDisplayState }) {
  const { t } = useTranslation('audit-logs');

  if (state === 'unsigned') {
    return <span className="text-fg-muted text-xs">—</span>;
  }

  if (state === 'violation' || state === 'violationRetamper') {
    const labelKey =
      state === 'violationRetamper'
        ? 'integrity.hmacBadge.violationRetamper'
        : 'integrity.hmacBadge.violation';
    const tooltipKey =
      state === 'violationRetamper'
        ? 'integrity.hmacBadge.violationRetamperTooltip'
        : 'integrity.hmacBadge.violationTooltip';
    return (
      <Tooltip content={t(tooltipKey)}>
        <ShieldAlert className="size-3.5 text-error" aria-label={t(labelKey)} />
      </Tooltip>
    );
  }

  if (state === 'violationExplained') {
    return (
      <Tooltip content={t('integrity.hmacBadge.violationExplainedTooltip')}>
        <ShieldAlert
          className="size-3.5 text-warning"
          aria-label={t('integrity.hmacBadge.violationExplained')}
        />
      </Tooltip>
    );
  }

  if (state === 'verified') {
    return (
      <Tooltip content={t('integrity.hmacBadge.verifiedTooltip')}>
        <ShieldCheck className="size-3.5 text-success" aria-label={t('integrity.hmacBadge.verified')} />
      </Tooltip>
    );
  }

  return (
    <Tooltip content={t('integrity.hmacBadge.signedTooltip')}>
      <ShieldCheck className="size-3.5 text-fg-muted" aria-label={t('integrity.hmacBadge.signed')} />
    </Tooltip>
  );
}
