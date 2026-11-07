import { AlertTriangle, Check, Copy } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import {
  recoveryCodesDialogCopy,
  type RecoveryCodesDialogMode,
  type RecoveryCodesIssuanceKind,
} from '@/lib/admin-recovery-codes-issuance';

export interface AdminRecoveryCodesDialogResult {
  recoveryCodes?: string[];
  /** English server summary. Not shown; localized success from issuance kind is used instead. */
  message?: string;
}

export interface AdminRecoveryCodesDialogProps {
  open: boolean;
  onClose: () => void;
  mode: RecoveryCodesDialogMode;
  /** Kind captured when the dialog opened. Do not recompute from a post-issuance refetch. */
  issuanceKind: RecoveryCodesIssuanceKind;
  username: string;
  result: AdminRecoveryCodesDialogResult | null;
  copied: boolean;
  savedConfirmed: boolean;
  onCopyAll: () => void;
  onSavedConfirmedChange: (confirmed: boolean) => void;
  onConfirm: () => void;
  pending: boolean;
  errorMessage: string | null;
}

export function AdminRecoveryCodesDialog({
  open,
  onClose,
  mode,
  issuanceKind,
  username,
  result,
  copied,
  savedConfirmed,
  onCopyAll,
  onSavedConfirmedChange,
  onConfirm,
  pending,
  errorMessage,
}: AdminRecoveryCodesDialogProps) {
  const { t } = useTranslation('admins');
  const copy = recoveryCodesDialogCopy(mode, issuanceKind);
  const issueInitial = mode === 'issue-initial';

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={t(copy.dialogTitle, { username })}
      size="md"
      dismissible={false}
    >
      {result ? (
        <div className="space-y-4">
          <Alert variant="success" data-testid="recovery-codes-issuance-success">
            {t(copy.success)}
          </Alert>
          <div className="border-2 border-error bg-error/5 p-3 flex gap-2">
            <AlertTriangle className="size-4 text-error shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-black text-error">
                {issueInitial
                  ? t('detail.issueInitialRecoveryCodesWarningTitle')
                  : t('detail.regenerateRecoveryCodesWarningTitle')}
              </p>
              <p className="text-xs text-error/80 mt-0.5">
                {issueInitial
                  ? t('detail.issueInitialRecoveryCodesWarningBody')
                  : t('detail.regenerateRecoveryCodesWarningBody')}
              </p>
            </div>
          </div>
          <div className="space-y-2 border-2 border-accent/40 bg-bg p-3">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
              {t('detail.recoveryCodesTitle')}
            </p>
            <p className="text-xs text-fg-muted">{t('detail.recoveryCodesHint')}</p>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={onCopyAll}
              className="gap-1.5"
            >
              {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
              {copied ? t('onboarding.copied') : t('detail.copyAllRecoveryCodes')}
            </Button>
            <ul
              data-testid="recovery-codes-once-list"
              className="font-mono text-xs space-y-1 break-all max-h-48 overflow-y-auto border-2 border-fg/20 p-2 bg-surface"
            >
              {(result.recoveryCodes ?? []).map((code) => (
                <li key={code}>{code}</li>
              ))}
            </ul>
            <div className="flex items-center gap-2.5 p-3 border-2 border-fg/20 bg-bg">
              <input
                id="regenerated-recovery-codes-saved"
                type="checkbox"
                className="size-4 border-2 border-fg accent-accent"
                checked={savedConfirmed}
                onChange={(e) => onSavedConfirmedChange(e.target.checked)}
              />
              <label
                htmlFor="regenerated-recovery-codes-saved"
                className="text-sm font-bold cursor-pointer select-none"
              >
                {t('detail.recoveryCodesSavedConfirmLabel')}
              </label>
            </div>
          </div>
          <div className="flex justify-end pt-2">
            <Button onClick={onClose} disabled={!savedConfirmed}>
              {t('detail.close')}
            </Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <Alert variant="warning">{t(copy.intro)}</Alert>
          <div className="border-2 border-error bg-error/5 p-3 flex gap-2">
            <AlertTriangle className="size-4 text-error shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-black text-error">
                {issueInitial
                  ? t('detail.issueInitialRecoveryCodesWarningTitle')
                  : t('detail.regenerateRecoveryCodesWarningTitle')}
              </p>
              <p className="text-xs text-error/80 mt-0.5">
                {issueInitial
                  ? t('detail.issueInitialRecoveryCodesWarningBody')
                  : t('detail.regenerateRecoveryCodesWarningBody')}
              </p>
            </div>
          </div>
          {errorMessage != null && errorMessage !== '' && (
            <Alert variant="error">{errorMessage}</Alert>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="ghost"
              type="button"
              onClick={onClose}
              disabled={pending}
            >
              {t('detail.editCancel')}
            </Button>
            <Button type="button" isLoading={pending} onClick={onConfirm}>
              {t(copy.confirm)}
            </Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}
