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
