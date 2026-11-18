import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { UserX } from 'lucide-react';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import {
  getGetAdminByIdQueryKey,
  useDeactivateAdmin,
} from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import type { AdminResponseDto } from '@/generated/admin-api/model';
import { getTranslatedApiError } from '@/lib/api-error-i18n';

/** Confirms an administrator deactivation with a reason; opened from the list row or the record. */
export function DeactivateAdminDialog({
  open,
  onClose,
  admin,
}: {
  open: boolean;
  onClose: () => void;
  admin: AdminResponseDto | null;
}) {
  const { t } = useTranslation('admins');
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');

  const deactivateMutation = useDeactivateAdmin({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['admins'] });
        if (admin?.adminId != null) {
          void queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(admin.adminId) });
        }
        setReason('');
        onClose();
      },
    },
  });

  const handleClose = () => {
    deactivateMutation.reset();
    setReason('');
    onClose();
  };

  const reasonTooShort = reason.trim().length < 10;
  const params =
    reason.trim().length >= 10 ? { reason: reason.trim() } : undefined;

  return (
    <Dialog open={open} onClose={handleClose} title={t('deactivate.title')} size="sm">
      {admin && (
        <div className="space-y-4">
          <p className="text-sm text-fg">
            {t('deactivate.confirmMessage', { username: admin.username })}
          </p>
          <p className="text-xs text-fg-muted">{t('deactivate.cannotUndo')}</p>

          <ReasonFieldRow
            presetGroup="admin_lifecycle"
            idPrefix="deactivate-admin"
            inputId="deactivate-admin-reason"
            value={reason}
            onChange={setReason}
            label={t('deactivate.reasonLabel')}
            placeholder={t('deactivate.reasonPlaceholder')}
            showMinLengthError={reasonTooShort}
            minLengthErrorTone="required"
            childrenAfterInput={<DemoReasonBadges onSelect={setReason} />}
          />

          {deactivateMutation.isError && (
            <Alert variant="error">
              {getTranslatedApiError(deactivateMutation.error, t, t('deactivate.errorDeactivate'))}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>
              {t('deactivate.cancel')}
            </Button>
            <Button
              variant="destructive"
              isLoading={deactivateMutation.isPending}
              disabled={reasonTooShort}
              onClick={() =>
                admin &&
                deactivateMutation.mutate({ id: admin.adminId!, params })
              }
            >
              <UserX className="size-3.5 mr-1.5" />
              {t('deactivate.submit')}
            </Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}
