import { Badge } from '@/components/ui/badge';
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
  return <Badge variant={variant}>{label}</Badge>;
}
