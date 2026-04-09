import { Shield, ShieldCheck, ShieldOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import type { EnrollmentResponseDtoDevicePrivateKeyStorageTier } from '@/generated/admin-api/model';

const LABEL_KEYS: Record<EnrollmentResponseDtoDevicePrivateKeyStorageTier, string> = {
  NONE: 'keyTier.labelNone',
  STANDARD: 'keyTier.labelStandard',
  STRONG: 'keyTier.labelStrong',
};

const HELP_KEYS: Record<EnrollmentResponseDtoDevicePrivateKeyStorageTier, string> = {
  NONE: 'keyTier.helpNone',
  STANDARD: 'keyTier.helpStandard',
  STRONG: 'keyTier.helpStrong',
};

const VARIANTS: Record<
  EnrollmentResponseDtoDevicePrivateKeyStorageTier,
  'success' | 'warning' | 'muted' | 'error'
> = {
  NONE: 'muted',
  STANDARD: 'warning',
  STRONG: 'success',
};

export function DevicePrivateKeyTierBadge({
  tier,
}: {
  tier: EnrollmentResponseDtoDevicePrivateKeyStorageTier | undefined | null;
}) {
  const { t } = useTranslation('enrollments');
  if (tier == null) {
    return (
      <span className="inline-flex items-center gap-1 text-fg-muted" title={t('keyTier.unknownTooltip')}>
        <ShieldOff className="size-3.5 shrink-0 opacity-70" aria-hidden />
        <span className="text-xs">—</span>
      </span>
    );
  }
  const labelKey = LABEL_KEYS[tier];
  const label = labelKey ? t(labelKey) : tier;
  const variant = VARIANTS[tier] ?? 'muted';
  const helpKey = HELP_KEYS[tier];
  const tooltipContent = helpKey ? t(helpKey) : undefined;
  const Icon = tier === 'STRONG' ? ShieldCheck : tier === 'STANDARD' ? Shield : ShieldOff;
  const badge = (
    <span className="inline-flex items-center gap-1.5">
      <Icon className="size-3.5 shrink-0" aria-hidden />
      <Badge variant={variant}>{label}</Badge>
    </span>
  );
  if (tooltipContent) {
    return <Tooltip content={tooltipContent}>{badge}</Tooltip>;
  }
  return badge;
}
