import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useGetAdminById } from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import type { AdminResponseDto } from '@/generated/admin-api/model';
import { adminOwnerDisplayName } from '@/lib/admin-owner-display-name';
import { adminDetailHref } from '@/lib/list-detail-navigation';

/**
 * Link back from an administrator's MFA enrollment to the administrator record (#1313).
 *
 * The API exposes only ownerAdminId (filled for a Global Admin caller); the name comes from
 * GET /admins/{id}, and the link shows #id until that loads or if it fails. An enrollment with no
 * administrator (old or hand-edited data) shows the plain label, with no link.
 */
export function AdminEnrollmentOwner({ ownerAdminId }: { ownerAdminId: number | null | undefined }) {
  const { t } = useTranslation('enrollments');
  const { data: owner } = useGetAdminById<AdminResponseDto>(ownerAdminId ?? 0, {
    query: { enabled: ownerAdminId != null },
  });

  if (ownerAdminId == null) {
    return (
      <p className="text-sm font-medium" data-testid="enrollment-admin-owner">
        {t('detail.adminEnrollmentNoOwner')}
      </p>
    );
  }
  const name = adminOwnerDisplayName(owner) ?? `#${ownerAdminId}`;
  return (
    <p className="text-sm" data-testid="enrollment-admin-owner">
      {t('detail.adminEnrollmentOf')}{' '}
      <Link to={adminDetailHref(ownerAdminId)} className="font-medium text-accent hover:underline">
        {name}
      </Link>
    </p>
  );
}
