import type { AdminResponseDto } from '@/generated/admin-api/model';

/**
 * Both kinds call `POST /api/v1/admins/{id}/recovery-codes/issue-initial`; only the operator copy differs.
 * `replacement` applies when a previous set was issued and every code in it has been used.
 */
export type RecoveryCodesIssuanceKind = 'initial' | 'replacement';

export function recoveryCodesIssuanceKind(
  admin: Pick<AdminResponseDto, 'recoveryCodesExhausted'>,
): RecoveryCodesIssuanceKind {
  return admin.recoveryCodesExhausted === true ? 'replacement' : 'initial';
}

export interface RecoveryCodesIssuanceCopyKeys {
  action: string;
  dialogTitle: string;
  intro: string;
  confirm: string;
  success: string;
  toast: string;
  error: string;
}

/** `admins` namespace keys for the issuance action, dialog, and feedback. */
export const RECOVERY_CODES_ISSUANCE_COPY_KEYS: Record<RecoveryCodesIssuanceKind, RecoveryCodesIssuanceCopyKeys> = {
  initial: {
    action: 'detail.issueInitialRecoveryCodes',
    dialogTitle: 'detail.issueInitialRecoveryCodesDialogTitle',
    intro: 'detail.issueInitialRecoveryCodesIntro',
    confirm: 'detail.issueInitialRecoveryCodesConfirm',
    success: 'detail.issueInitialRecoveryCodesSuccess',
    toast: 'detail.toastRecoveryCodesIssued',
    error: 'detail.errorRecoveryCodesIssue',
  },
  replacement: {
    action: 'detail.issueReplacementRecoveryCodes',
    dialogTitle: 'detail.issueReplacementRecoveryCodesDialogTitle',
    intro: 'detail.issueReplacementRecoveryCodesIntro',
    confirm: 'detail.issueReplacementRecoveryCodesConfirm',
    success: 'detail.issueReplacementRecoveryCodesSuccess',
    toast: 'detail.toastReplacementRecoveryCodesIssued',
    error: 'detail.errorReplacementRecoveryCodesIssue',
  },
};

export type RecoveryCodesDialogMode = 'issue-initial' | 'regenerate';

export interface RecoveryCodesDialogCopyKeys {
  dialogTitle: string;
  intro: string;
  confirm: string;
  success: string;
  error: string;
}

/**
 * Operator-facing dialog copy. Success always comes from these keys — never from the
 * English `message` field on the issuance HTTP response.
 */
export function recoveryCodesDialogCopy(
  mode: RecoveryCodesDialogMode,
  issuanceKind: RecoveryCodesIssuanceKind,
): RecoveryCodesDialogCopyKeys {
  if (mode === 'regenerate') {
    return {
      dialogTitle: 'detail.regenerateRecoveryCodesDialogTitle',
      intro: 'detail.regenerateRecoveryCodesIntro',
      confirm: 'detail.regenerateRecoveryCodesConfirm',
      success: 'detail.regenerateRecoveryCodesSuccess',
      error: 'detail.errorRecoveryCodesRegenerate',
    };
  }
  const keys = RECOVERY_CODES_ISSUANCE_COPY_KEYS[issuanceKind];
  return {
    dialogTitle: keys.dialogTitle,
    intro: keys.intro,
    confirm: keys.confirm,
    success: keys.success,
    error: keys.error,
  };
}

/**
 * While the issuance dialog is open, keep the kind captured at open. A successful
 * issue-initial refetch clears `recoveryCodesExhausted` and must not switch copy.
 */
export function frozenIssuanceKindWhileOpen(
  dialogOpen: boolean,
  kindAtOpen: RecoveryCodesIssuanceKind,
  liveAdmin: Pick<AdminResponseDto, 'recoveryCodesExhausted'>,
): RecoveryCodesIssuanceKind {
  if (dialogOpen) {
    return kindAtOpen;
  }
  return recoveryCodesIssuanceKind(liveAdmin);
}
