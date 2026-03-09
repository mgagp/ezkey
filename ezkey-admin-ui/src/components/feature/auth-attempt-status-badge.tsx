import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import { AUTH_ATTEMPT_STATUS_HELP } from '@/lib/help-text';
import type { AuthAttemptDtoAuthAttemptStatus } from '@/generated/admin-api/model';

const config: Record<AuthAttemptDtoAuthAttemptStatus, { label: string; variant: 'success' | 'warning' | 'muted' | 'error' }> = {
  PENDING: { label: 'Pending', variant: 'warning' },
  READ: { label: 'Read', variant: 'muted' },
  ACCEPTED: { label: 'Accepted', variant: 'success' },
  REJECTED: { label: 'Rejected', variant: 'error' },
  EXPIRED: { label: 'Expired', variant: 'muted' },
  INVALID: { label: 'Invalid', variant: 'error' },
};

export function AuthAttemptStatusBadge({ status }: { status: AuthAttemptDtoAuthAttemptStatus }) {
  const { label, variant } = config[status] ?? { label: status, variant: 'muted' as const };
  const tooltipContent = status in AUTH_ATTEMPT_STATUS_HELP ? AUTH_ATTEMPT_STATUS_HELP[status] : undefined;
  const badge = <Badge variant={variant}>{label}</Badge>;
  if (tooltipContent) {
    return <Tooltip content={tooltipContent}>{badge}</Tooltip>;
  }
  return badge;
}
