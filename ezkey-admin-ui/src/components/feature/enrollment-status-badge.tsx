import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import type { EnrollmentResponseDtoEnrollmentStatus } from '@/generated/admin-api/model';

const HELP_KEYS: Record<EnrollmentResponseDtoEnrollmentStatus, string> = {
  VERIFIED: 'status.helpVerified',
  BOUND: 'status.helpBound',
  CREATED: 'status.helpCreated',
  INVALID: 'status.helpInvalid',
  REVOKED: 'status.helpRevoked',
  EXPIRED: 'status.helpExpired',
};

const LABEL_KEYS: Record<EnrollmentResponseDtoEnrollmentStatus, string> = {
  VERIFIED: 'status.labelVerified',
  BOUND: 'status.labelBound',
  CREATED: 'status.labelCreated',
  INVALID: 'status.labelInvalid',
  REVOKED: 'status.labelRevoked',
  EXPIRED: 'status.labelExpired',
};

const VARIANTS: Record<EnrollmentResponseDtoEnrollmentStatus, 'success' | 'warning' | 'muted' | 'error'> = {
  VERIFIED: 'success',
  BOUND: 'warning',
  CREATED: 'muted',
  INVALID: 'error',
  REVOKED: 'error',
  EXPIRED: 'muted',
};

export function EnrollmentStatusBadge({ status }: { status: EnrollmentResponseDtoEnrollmentStatus | undefined }) {
  const { t } = useTranslation('enrollments');
  if (!status) return <Badge variant="muted">—</Badge>;
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
