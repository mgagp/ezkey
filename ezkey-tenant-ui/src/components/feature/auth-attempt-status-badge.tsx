import { Badge } from '@/components/ui/badge';
import type { AuthAttemptStatus } from '@/types/models';

const config: Record<AuthAttemptStatus, { label: string; variant: 'success' | 'warning' | 'muted' | 'error' }> = {
  PENDING: { label: 'Pending', variant: 'warning' },
  READ: { label: 'Read', variant: 'muted' },
  ACCEPTED: { label: 'Accepted', variant: 'success' },
  REJECTED: { label: 'Rejected', variant: 'error' },
  EXPIRED: { label: 'Expired', variant: 'muted' },
  INVALID: { label: 'Invalid', variant: 'error' },
};

export function AuthAttemptStatusBadge({ status }: { status: AuthAttemptStatus }) {
  const { label, variant } = config[status] ?? { label: status, variant: 'muted' as const };
  return <Badge variant={variant}>{label}</Badge>;
}
