import { ShieldAlert, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Tooltip } from '@/components/ui/tooltip';
import type { EntryHmacDisplayState } from '@/lib/integrity-investigation-session';

export function EntryHmacBadge({ state }: { state: EntryHmacDisplayState }) {
  const { t } = useTranslation('audit-logs');

  if (state === 'unsigned') {
    return <span className="text-fg-muted text-xs">—</span>;
  }

  if (state === 'violation') {
    return (
      <Tooltip content={t('integrity.hmacBadge.violationTooltip')}>
        <ShieldAlert className="size-3.5 text-error" aria-label={t('integrity.hmacBadge.violation')} />
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
