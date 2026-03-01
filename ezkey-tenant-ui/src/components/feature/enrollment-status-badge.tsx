import { Badge } from '@/components/ui/badge';
import type { EnrollmentStatus } from '@/types/models';

const config: Record<EnrollmentStatus, { label: string; variant: 'success' | 'warning' | 'muted' | 'error' }> = {
  VERIFIED: { label: 'Verified', variant: 'success' },
  BOUND: { label: 'Bound', variant: 'warning' },
  CREATED: { label: 'Created', variant: 'muted' },
  INVALID: { label: 'Invalid', variant: 'error' },
};

export function EnrollmentStatusBadge({ status }: { status: EnrollmentStatus }) {
  const { label, variant } = config[status] ?? { label: status, variant: 'muted' as const };
  return <Badge variant={variant}>{label}</Badge>;
}
