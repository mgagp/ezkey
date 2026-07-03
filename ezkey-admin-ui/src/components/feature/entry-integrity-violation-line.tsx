import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import {
  entryViolationDisplayState,
  type EntryHmacDisplayState,
} from '@/lib/integrity-investigation-session';
import type { EntryIntegrityViolation } from '@/generated/admin-api/model';

function violationLineClass(state: EntryHmacDisplayState): string {
  if (state === 'violationExplained') {
    return 'text-warning';
  }
  if (state === 'violationRetamper') {
    return 'text-error font-semibold';
  }
  return 'text-error';
}

export function EntryIntegrityViolationLine({
  violation,
}: {
  violation: EntryIntegrityViolation;
}) {
  const { t } = useTranslation('audit-logs');
  const state = entryViolationDisplayState(violation);
  const suffix =
    state === 'violationExplained'
      ? ` — ${t('integrity.hmacBadge.violationExplained')}`
      : state === 'violationRetamper'
        ? ` — ${t('integrity.hmacBadge.violationRetamper')}`
        : '';

  return (
    <li className={cn('font-mono', violationLineClass(state))}>
      #{violation.auditLogId} — {violation.reason ?? '—'}
      {suffix}
    </li>
  );
}
