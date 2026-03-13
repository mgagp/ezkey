import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import type { AuthAttemptDtoAuthAttemptStatus } from '@/generated/admin-api/model';

const HELP_KEYS: Record<AuthAttemptDtoAuthAttemptStatus, string> = {
  PENDING: 'status.helpPending',
  READ: 'status.helpRead',
  ACCEPTED: 'status.helpAccepted',
  REJECTED: 'status.helpRejected',
  EXPIRED: 'status.helpExpired',
  INVALID: 'status.helpInvalid',
};

const LABEL_KEYS: Record<AuthAttemptDtoAuthAttemptStatus, string> = {
  PENDING: 'status.labelPending',
  READ: 'status.labelRead',
  ACCEPTED: 'status.labelAccepted',
  REJECTED: 'status.labelRejected',
  EXPIRED: 'status.labelExpired',
  INVALID: 'status.labelInvalid',
};

const VARIANTS: Record<AuthAttemptDtoAuthAttemptStatus, 'success' | 'warning' | 'muted' | 'error'> = {
  PENDING: 'warning',
  READ: 'muted',
  ACCEPTED: 'success',
  REJECTED: 'error',
  EXPIRED: 'muted',
  INVALID: 'error',
};

export function AuthAttemptStatusBadge({ status }: { status: AuthAttemptDtoAuthAttemptStatus }) {
  const { t } = useTranslation('auth-attempts');
  const labelKey = LABEL_KEYS[status];
  const label = labelKey ? t(labelKey) : status;
  const variant = VARIANTS[status] ?? 'muted';
  const helpKey = HELP_KEYS[status];
  const tooltipContent = helpKey ? t(helpKey) : undefined;
  const badge = <Badge variant={variant}>{label}</Badge>;
  if (tooltipContent) {
    return <Tooltip content={tooltipContent}>{badge}</Tooltip>;
  }
  return badge;
}
