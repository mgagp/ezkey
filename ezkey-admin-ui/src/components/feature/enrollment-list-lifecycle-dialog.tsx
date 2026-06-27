import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Power, PowerOff } from 'lucide-react';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { useToast } from '@/context/use-toast';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { useDeactivate, useReactivate } from '@/generated/admin-api/enrollments/enrollments';
import type { EnrollmentResponseDto } from '@/generated/admin-api/model';

export type EnrollmentLifecycleAction = 'deactivate' | 'reactivate';

export function EnrollmentListLifecycleDialog({
  open,
  onClose,
  enrollment,
  action,
  idPrefix = 'enrollment-list-lifecycle',
  renderReasonBadges,
}: {
  open: boolean;
  onClose: () => void;
  enrollment: EnrollmentResponseDto | null;
  action: EnrollmentLifecycleAction | null;
  /** Stable prefix for reason input ids (unique per host page). */
  idPrefix?: string;
  renderReasonBadges?: (setReason: (value: string) => void) => ReactNode;
}) {
  const { t } = useTranslation('enrollments');
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [reason, setReason] = useState('');

  const enrollmentId = enrollment?.enrollmentId ?? 0;

  const deactivateMutation = useDeactivate({
    mutation: {
      onSuccess: async () => {
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast(t('detail.toastDeactivated'));
        setReason('');
        onClose();
      },
    },
  });

  const reactivateMutation = useReactivate({
    mutation: {
      onSuccess: async () => {
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast(t('detail.toastReactivated'));
        setReason('');
        onClose();
      },
    },
  });

  const handleClose = () => {
    setReason('');
    deactivateMutation.reset();
    reactivateMutation.reset();
    onClose();
  };

  const isDeactivate = action === 'deactivate';
  const mutation = isDeactivate ? deactivateMutation : reactivateMutation;
  const reasonInvalid = reason.trim().length > 0 && reason.trim().length < 10;

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      title={isDeactivate ? t('lifecycleDialog.deactivateTitle') : t('lifecycleDialog.reactivateTitle')}
      size="sm"
      dismissible={false}
    >
      <div className="space-y-4">
        <p className="text-sm text-fg">
          {isDeactivate
            ? t('lifecycleDialog.deactivateMessage')
            : t('lifecycleDialog.reactivateMessage')}
          {enrollment?.enrollmentName != null && (
            <span className="block mt-1 font-medium">{enrollment.enrollmentName}</span>
          )}
        </p>
        <ReasonFieldRow
          presetGroup="enrollment_lifecycle"
          idPrefix={idPrefix}
          inputId={`${idPrefix}-reason`}
          value={reason}
          onChange={setReason}
          label={
            <>
              {t('lifecycleDialog.reasonLabel')}{' '}
              <span className="text-fg-muted font-normal">{t('lifecycleDialog.reasonHint')}</span>
            </>
          }
          placeholder={t('lifecycleDialog.reasonPlaceholder')}
          showMinLengthError={reasonInvalid}
          childrenAfterInput={
            renderReasonBadges?.(setReason) ?? <DemoReasonBadges onSelect={setReason} />
          }
        />
        {mutation.isError && (
          <Alert variant="error">
            {getTranslatedApiError(
              mutation.error,
              t,
              isDeactivate ? t('detail.errorDeactivate') : t('detail.errorReactivate'),
            )}
          </Alert>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="ghost" onClick={handleClose}>
            {t('lifecycleDialog.cancel')}
          </Button>
          <Button
            variant={isDeactivate ? 'destructive' : 'primary'}
            isLoading={mutation.isPending}
            disabled={reasonInvalid}
            onClick={() => {
              if (action === null || enrollmentId <= 0) return;
              const params = reason.trim().length >= 10 ? { reason: reason.trim() } : undefined;
              if (isDeactivate) {
                deactivateMutation.mutate({ id: enrollmentId, params });
              } else {
                reactivateMutation.mutate({ id: enrollmentId, params });
              }
            }}
          >
            {isDeactivate ? (
              <>
                <PowerOff className="size-3.5 mr-1.5" />
                {t('lifecycleDialog.deactivate')}
              </>
            ) : (
              <>
                <Power className="size-3.5 mr-1.5" />
                {t('lifecycleDialog.reactivate')}
              </>
            )}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
