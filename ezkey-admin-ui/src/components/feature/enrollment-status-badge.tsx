import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import { ENROLLMENT_STATUS_HELP } from '@/lib/help-text';
import type { EnrollmentResponseDtoEnrollmentStatus } from '@/generated/admin-api/model';

const config: Record<EnrollmentResponseDtoEnrollmentStatus, { label: string; variant: 'success' | 'warning' | 'muted' | 'error' }> = {
  VERIFIED: { label: 'Verified', variant: 'success' },
  BOUND: { label: 'Bound', variant: 'warning' },
  CREATED: { label: 'Created', variant: 'muted' },
  INVALID: { label: 'Invalid', variant: 'error' },
  REVOKED: { label: 'Revoked', variant: 'error' },
  EXPIRED: { label: 'Expired', variant: 'muted' },
};

export function EnrollmentStatusBadge({ status }: { status: EnrollmentResponseDtoEnrollmentStatus | undefined }) {
  if (!status) return <Badge variant="muted">—</Badge>;
  const { label, variant } = config[status] ?? { label: status, variant: 'muted' as const };
  const tooltipContent = status in ENROLLMENT_STATUS_HELP ? ENROLLMENT_STATUS_HELP[status as keyof typeof ENROLLMENT_STATUS_HELP] : undefined;
  const badge = <Badge variant={variant}>{label}</Badge>;
  if (tooltipContent) {
    return <Tooltip content={tooltipContent}>{badge}</Tooltip>;
  }
  return badge;
}
