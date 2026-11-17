import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { AdminResponseDto } from '@/generated/admin-api/model';

/** Global / Tenant / Integration administrator type, shared by the list and the record. */
export function AdminTypeBadge({ type }: { type: AdminResponseDto['adminType'] }) {
  const { t } = useTranslation('admins');
  if (type === 'GLOBAL_ADMIN') return <Badge variant="warning">{t('adminType.global')}</Badge>;
  if (type === 'INTEGRATION_ADMIN') return <Badge variant="muted">{t('adminType.integration')}</Badge>;
  return <Badge variant="muted">{t('adminType.tenant')}</Badge>;
}

/** Pending activation / active / inactive, with the list or record copy. */
export function AdminStatusBadge({
  admin,
  scope,
}: {
  admin: Pick<AdminResponseDto, 'active' | 'lifecycleStatus'>;
  scope: 'list' | 'detail';
}) {
  const { t } = useTranslation('admins');
  if (admin.lifecycleStatus === 'PENDING_ACTIVATION') {
    return <Badge variant="warning">{t(`${scope}.statusPendingActivation`)}</Badge>;
  }
  if (admin.active) {
    return <Badge variant="success">{t(`${scope}.statusActive`)}</Badge>;
  }
  return <Badge variant="muted">{t(`${scope}.statusInactive`)}</Badge>;
}
