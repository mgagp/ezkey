import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';

/** Audit log event status (SUCCESS, FAILURE, ERROR). Used by audit-logs list and dashboard widget. */
export type AuditEventStatus = 'SUCCESS' | 'FAILURE' | 'ERROR';

export function EventStatusBadge({ status }: { status: AuditEventStatus | undefined }) {
  const { t } = useTranslation('audit-logs');
  if (status === 'SUCCESS') return <Badge variant="success">{t('eventStatus.labelSuccess')}</Badge>;
  if (status === 'FAILURE') return <Badge variant="error">{t('eventStatus.labelFailure')}</Badge>;
  if (status === 'ERROR') return <Badge variant="error">{t('eventStatus.labelError')}</Badge>;
  return <Badge variant="muted">—</Badge>;
}
