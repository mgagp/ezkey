import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Check, Copy, KeyRound, Pencil, Power, PowerOff, RefreshCw } from 'lucide-react';
import { AdminBindCard } from '@/components/feature/admin-bind-card';
import { AdminStatusBadge, AdminTypeBadge } from '@/components/feature/admin-badges';
import { DeactivateAdminDialog } from '@/components/feature/admin-deactivate-dialog';
import { AdminRecoveryCodesDialog } from '@/components/feature/admin-recovery-codes-dialog';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { EnrollmentFkLink } from '@/components/feature/fk-detail-links';
import { OperationalWarning } from '@/components/feature/operational-warning';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { AppShell } from '@/components/layout/app-shell';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ContextHelp } from '@/components/ui/context-help';
import { DetailInfoRow } from '@/components/ui/detail-info-row';
import { DetailPageNav } from '@/components/ui/detail-page-nav';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tooltip } from '@/components/ui/tooltip';
import { useAuth } from '@/context/use-auth';
import { useToast } from '@/context/use-toast';
import {
  getGetAdminByIdQueryKey,
  useGetAdminById,
  useUpdateAdmin,
} from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import type { AdminResponseDto, AdminUpdateRequestDto } from '@/generated/admin-api/model';
import { useListDetailPageNavigation } from '@/hooks/use-list-detail-page-navigation';
import { canShowOnboardingCredentials } from '@/lib/admin-onboarding-eligibility';
import { canReissuePendingAdministratorActivationCode } from '@/lib/admin-pending-activation-eligibility';
import {
  RECOVERY_CODES_ISSUANCE_COPY_KEYS,
  recoveryCodesDialogCopy,
  recoveryCodesIssuanceKind,
  type RecoveryCodesIssuanceKind,
} from '@/lib/admin-recovery-codes-issuance';
import { isPendingActivationAdmin, shouldShowTenantOperationalWarning } from '@/lib/admin-record-rules';
import { fetchApi } from '@/lib/api-client';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { isPhoneNumberInputValid, normalizePhoneNumberInput } from '@/lib/phone-number';
import { formatDate, formatRelativeTime } from '@/lib/utils';

/** UI-facing shape for recovery-code regeneration response. */
interface AdminRecoveryCodesRegenerationShape {
  adminId?: number;
  username?: string;
  recoveryCodes?: string[];
  codesCount?: number;
  invalidatedPreviousCodes?: boolean;
  message?: string;
}

/** UI-facing shape for activation-code re-issue (Global Admin recovery for pending admins). */
interface AdminActivationCodeReissueShape {
  adminId?: number;
  username?: string;
  activationCode?: string;
  activationCodeExpiresAt?: string;
  invalidatedPreviousTokens?: boolean;
  deactivatedActiveTokenCount?: number;
  message?: string;
}

type RecoveryAwareAdmin = AdminResponseDto & { hasRecoveryCodes?: boolean };

function canRegenerateAdminRecoveryCodes(
  admin: Pick<RecoveryAwareAdmin, 'active' | 'lifecycleStatus' | 'hasRecoveryCodes'>,
): boolean {
  return admin.active === true && admin.lifecycleStatus === 'ACTIVE' && admin.hasRecoveryCodes === true;
}

function canIssueInitialAdminRecoveryCodes(
  admin: Pick<RecoveryAwareAdmin, 'active' | 'lifecycleStatus' | 'lastLoginAt' | 'hasRecoveryCodes'>,
): boolean {
  return (
    admin.active === true
    && admin.lifecycleStatus === 'ACTIVE'
    && admin.hasRecoveryCodes !== true
    && admin.lastLoginAt != null
  );
}

function canShowIssueInitialAdminRecoveryCodesAction(
  admin: Pick<RecoveryAwareAdmin, 'active' | 'lifecycleStatus' | 'hasRecoveryCodes'>,
): boolean {
  return (
    admin.active === true
    && admin.lifecycleStatus === 'ACTIVE'
    && admin.hasRecoveryCodes !== true
  );
}

// ── Record ─────────────────────────────────────────────────────────────────────

/**
 * The administrator record (#1399): details, the bind card (#1313) and the actions, with their
 * dialogs. Mounted with key={adminId} so moving to another administrator resets every dialog.
 */
function AdminRecord({ adm }: { adm: RecoveryAwareAdmin }) {
  const { t } = useTranslation('admins');
  const { session } = useAuth();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [editOpen, setEditOpen] = useState(false);
  const [editFirstName, setEditFirstName] = useState('');
  const [editLastName, setEditLastName] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [regenerateOpen, setRegenerateOpen] = useState(false);
  const [regeneratedCodes, setRegeneratedCodes] = useState<AdminRecoveryCodesRegenerationShape | null>(null);
  const [regeneratedCodesCopied, setRegeneratedCodesCopied] = useState(false);
  const [regeneratedCodesSavedConfirmed, setRegeneratedCodesSavedConfirmed] = useState(false);
  const [recoveryCodesDialogMode, setRecoveryCodesDialogMode] = useState<'issue-initial' | 'regenerate'>('regenerate');
  // Frozen when the dialog opens: the detail refetch after issuance clears recoveryCodesExhausted.
  const [issuanceKind, setIssuanceKind] = useState<RecoveryCodesIssuanceKind>('initial');
  const issuanceCopy = RECOVERY_CODES_ISSUANCE_COPY_KEYS[issuanceKind];
  const [reissueActivationOpen, setReissueActivationOpen] = useState(false);
  const [reissuedActivation, setReissuedActivation] = useState<AdminActivationCodeReissueShape | null>(null);
  const [activationReissueCopied, setActivationReissueCopied] = useState(false);
  const [activationReissueSavedConfirmed, setActivationReissueSavedConfirmed] = useState(false);

  const [deactivateOpen, setDeactivateOpen] = useState(false);
  const [activateReason, setActivateReason] = useState('');

  const activateMutation = useMutation({
    mutationFn: async ({ id, reason }: { id: number; reason?: string }) => {
      const trimmed = reason?.trim();
      const qs =
        trimmed != null && trimmed.length >= 10
          ? `?reason=${encodeURIComponent(trimmed)}`
          : '';
      await fetchApi(`/api/v1/admins/${id}/activate${qs}`, { method: 'POST' });
    },
    onSuccess: () => {
      toast(t('detail.toastActivated', { username: adm.username }), 'success');
      setActivateReason('');
      void queryClient.invalidateQueries({ queryKey: ['admins'] });
      void queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm.adminId!) });
    },
    onError: (e: unknown) => toast(getTranslatedApiError(e, t, t('detail.errorActivate')), 'error'),
  });

  const updateMutation = useUpdateAdmin({
    mutation: {
      onSuccess: async () => {
        toast(t('detail.toastUpdated'), 'success');
        await queryClient.invalidateQueries({ queryKey: ['admins'] });
        await queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm.adminId!) });
        setEditOpen(false);
      },
    },
  });

  const regenerateRecoveryCodesMutation = useMutation({
    mutationFn: async (id: number) =>
      fetchApi<AdminRecoveryCodesRegenerationShape>(`/api/v1/admins/${id}/recovery-codes/regenerate`, {
        method: 'POST',
      }),
    onSuccess: async (data) => {
      setRegeneratedCodes(data);
      setRegeneratedCodesCopied(false);
      setRegeneratedCodesSavedConfirmed(false);
      toast(t('detail.toastRecoveryCodesRegenerated', { username: adm.username }), 'success');
      await queryClient.invalidateQueries({ queryKey: ['admins'] });
      await queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm.adminId!) });
    },
    onError: (e: unknown) =>
      toast(getTranslatedApiError(e, t, t('detail.errorRecoveryCodesRegenerate')), 'error'),
  });

  const issueInitialRecoveryCodesMutation = useMutation({
    mutationFn: async (id: number) =>
      fetchApi<AdminRecoveryCodesRegenerationShape>(`/api/v1/admins/${id}/recovery-codes/issue-initial`, {
        method: 'POST',
      }),
    onSuccess: async (data) => {
      setRegeneratedCodes(data);
      setRegeneratedCodesCopied(false);
      setRegeneratedCodesSavedConfirmed(false);
      toast(t(issuanceCopy.toast, { username: adm.username }), 'success');
      await queryClient.invalidateQueries({ queryKey: ['admins'] });
      await queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm.adminId!) });
    },
    onError: (e: unknown) =>
      toast(getTranslatedApiError(e, t, t(issuanceCopy.error)), 'error'),
  });

  const reissueActivationCodeMutation = useMutation({
    mutationFn: async (id: number) =>
      fetchApi<AdminActivationCodeReissueShape>(`/api/v1/admins/${id}/activation-code/regenerate`, {
        method: 'POST',
      }),
    onSuccess: async (data) => {
      setReissuedActivation(data);
      setActivationReissueCopied(false);
      setActivationReissueSavedConfirmed(false);
      toast(t('detail.toastActivationCodeReissued', { username: adm.username }), 'success');
      await queryClient.invalidateQueries({ queryKey: ['admins'] });
      await queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm.adminId!) });
    },
    onError: (e: unknown) =>
      toast(getTranslatedApiError(e, t, t('detail.errorActivationCodeReissue')), 'error'),
  });

  const activateReasonTooShort = activateReason.trim().length > 0 && activateReason.trim().length < 10;

  const fullName = [adm.firstName, adm.lastName].filter(Boolean).join(' ');
  const canShowIssueInitialAction = canShowIssueInitialAdminRecoveryCodesAction(adm);
  const issueActionLabel = t(RECOVERY_CODES_ISSUANCE_COPY_KEYS[recoveryCodesIssuanceKind(adm)].action);
  const canIssueInitialCodesNow = canIssueInitialAdminRecoveryCodes(adm);
  const issueInitialCodesDisabledReason = canShowIssueInitialAction && !canIssueInitialCodesNow
    ? t('detail.issueInitialRecoveryCodesBlockedFirstLoginRequired')
    : null;

  const openEditDialog = () => {
    setEditFirstName(adm.firstName ?? '');
    setEditLastName(adm.lastName ?? '');
    setEditEmail(adm.email ?? '');
    setEditPhone(adm.phoneNumber ?? '');
    setEditOpen(true);
  };

  const openRegenerateDialog = () => {
    setRecoveryCodesDialogMode('regenerate');
    setRegeneratedCodes(null);
    setRegeneratedCodesCopied(false);
    setRegeneratedCodesSavedConfirmed(false);
    setRegenerateOpen(true);
  };

  const openIssueInitialDialog = () => {
    setRecoveryCodesDialogMode('issue-initial');
    setIssuanceKind(recoveryCodesIssuanceKind(adm));
    setRegeneratedCodes(null);
    setRegeneratedCodesCopied(false);
    setRegeneratedCodesSavedConfirmed(false);
    setRegenerateOpen(true);
  };

  const openReissueActivationDialog = () => {
    setReissuedActivation(null);
    setActivationReissueCopied(false);
    setActivationReissueSavedConfirmed(false);
    setReissueActivationOpen(true);
  };

  const handleCloseRegenerateDialog = () => {
    if (regeneratedCodes && !regeneratedCodesSavedConfirmed) {
      return;
    }
    if (regenerateRecoveryCodesMutation.isPending || issueInitialRecoveryCodesMutation.isPending) {
      return;
    }
    setRegenerateOpen(false);
    setRegeneratedCodes(null);
    setRegeneratedCodesCopied(false);
    setRegeneratedCodesSavedConfirmed(false);
    regenerateRecoveryCodesMutation.reset();
    issueInitialRecoveryCodesMutation.reset();
  };

  const handleCloseReissueActivationDialog = () => {
    if (reissuedActivation != null && !activationReissueSavedConfirmed) {
      return;
    }
    if (reissueActivationCodeMutation.isPending) {
      return;
    }
    setReissueActivationOpen(false);
    setReissuedActivation(null);
    setActivationReissueCopied(false);
    setActivationReissueSavedConfirmed(false);
    reissueActivationCodeMutation.reset();
  };

  const handleCopyRegeneratedCodes = async () => {
    const codes = regeneratedCodes?.recoveryCodes;
    if (codes == null || codes.length === 0) return;
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      setRegeneratedCodesCopied(true);
      setTimeout(() => setRegeneratedCodesCopied(false), 2000);
    } catch {
      /* ignore clipboard API errors */
    }
  };

  const handleCopyReissuedActivationCode = async () => {
    const code = reissuedActivation?.activationCode;
    if (code == null || code === '') return;
    try {
      await navigator.clipboard.writeText(code);
      setActivationReissueCopied(true);
      setTimeout(() => setActivationReissueCopied(false), 2000);
    } catch {
      /* ignore clipboard API errors */
    }
  };

  return (
    <div className="space-y-6" data-testid="admin-record">
      {isPendingActivationAdmin(adm) && (
        <Alert variant="info">{t('detail.pendingActivationNotice')}</Alert>
      )}
      {shouldShowTenantOperationalWarning(adm) && (
        <OperationalWarning message={t('detail.operationalWarning.tenantInactive')} />
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>{t('detail.cardDetails')}</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="space-y-2.5">
                <DetailInfoRow label={t('detail.labelAdminId')} valueClassName="break-all"><span className="font-mono">{adm.adminId}</span></DetailInfoRow>
                <DetailInfoRow label={t('detail.labelUsername')} valueClassName="break-all"><span className="font-medium">{adm.username}</span></DetailInfoRow>
                <DetailInfoRow label={t('detail.labelName')} valueClassName="break-all">{fullName || <span className="text-fg-muted">—</span>}</DetailInfoRow>
                <DetailInfoRow label={t('detail.labelEmail')} valueClassName="break-all">
                  {adm.email || <span className="text-fg-muted">—</span>}
                  <p className="text-xs text-fg-muted mt-0.5" data-testid="admin-email-hint">{t('detail.emailHint')}</p>
                </DetailInfoRow>
                <DetailInfoRow label={t('detail.labelPhone')} valueClassName="break-all">{adm.phoneNumber || <span className="text-fg-muted">—</span>}</DetailInfoRow>
                <DetailInfoRow label={t('detail.labelType')} valueClassName="break-all"><AdminTypeBadge type={adm.adminType} /></DetailInfoRow>
                {adm.tenantId != null && (
                  <DetailInfoRow label={t('detail.labelTenant')} valueClassName="break-all">
                    {adm.tenantName != null && adm.tenantName.trim() !== '' ? (
                      <>
                        <span className="font-medium">{adm.tenantName.trim()}</span>
                        <span className="text-fg-muted font-mono text-xs ml-1.5">(ID {adm.tenantId})</span>
                      </>
                    ) : (
                      <span className="font-mono">{adm.tenantId}</span>
                    )}
                  </DetailInfoRow>
                )}
                {/* #1313: Global only, from the session role. Display rule, not a protection: the id is
                    not a secret, and /enrollments/{id} answers 403 to a Tenant Admin. */}
                {isGlobalAdmin && adm.enrollmentId != null && (
                  <DetailInfoRow label={t('detail.labelEnrollmentId')} valueClassName="break-all">
                    <span data-testid="admin-detail-enrollment-link">
                      <EnrollmentFkLink
                        enrollmentId={adm.enrollmentId}
                        enrollmentName={adm.enrollmentName}
                        fallbackLabel={t('detail.enrollmentFallback', { id: adm.enrollmentId })}
                      />
                    </span>
                  </DetailInfoRow>
                )}
                <DetailInfoRow label={t('detail.labelStatus')} valueClassName="break-all"><AdminStatusBadge admin={adm} scope="detail" /></DetailInfoRow>
                {isPendingActivationAdmin(adm) && (
                  <DetailInfoRow label={t('detail.labelActivationCodeExpiresAt')} valueClassName="break-all">
                    <span data-testid="admin-detail-activation-code-expiry">
                      {adm.activationCodeExpiresAt
                        ? formatDate(adm.activationCodeExpiresAt)
                        : <span className="text-fg-muted">{t('detail.activationCodeNoValidCode')}</span>}
                    </span>
                  </DetailInfoRow>
                )}
                <DetailInfoRow label={t('detail.labelCreated')} valueClassName="break-all">{adm.createdAt ? formatDate(adm.createdAt) : '—'}</DetailInfoRow>
                <DetailInfoRow label={t('detail.labelLastLogin')} valueClassName="break-all">
                  {adm.lastLoginAt ? (
                    <span>
                      {formatDate(adm.lastLoginAt)}
                      <span className="text-fg-muted text-xs ml-1.5">({formatRelativeTime(adm.lastLoginAt)})</span>
                    </span>
                  ) : (
                    <span className="text-fg-muted italic">{t('detail.lastLoginNever')}</span>
                  )}
                </DetailInfoRow>
                <DetailInfoRow label={t('detail.labelEnrollmentCapability')} valueClassName="break-all">
                  {adm.enrollmentId != null
                    ? <Badge variant="success">{t('detail.enrollmentCapabilityLinked')}</Badge>
                    : <Badge variant="muted">{t('detail.enrollmentCapabilityMissing')}</Badge>}
                </DetailInfoRow>
                <DetailInfoRow label={t('detail.labelAdminLoginState')} valueClassName="break-all">
                  {adm.lastLoginAt != null
                    ? <Badge variant="success">{t('detail.adminLoginStateCompleted')}</Badge>
                    : <Badge variant="warning">{t('detail.adminLoginStatePendingFirstLogin')}</Badge>}
                </DetailInfoRow>
              </dl>
            </CardContent>
          </Card>

          {canShowOnboardingCredentials(adm) && adm.adminId != null && (
            <AdminBindCard adminId={adm.adminId} enrollmentStatus={adm.enrollmentStatus} />
          )}
        </div>

        <Card>
          <CardHeader>
            <CardTitle>{t('detail.sectionActions')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              <Button
                variant="secondary"
                size="sm"
                className="w-full justify-start gap-2"
                onClick={openEditDialog}
              >
                <Pencil className="size-3.5" />
                {t('detail.editProfile')}
              </Button>
              {canShowIssueInitialAction && (
                issueInitialCodesDisabledReason ? (
                  <Tooltip content={issueInitialCodesDisabledReason} className="flex w-full">
                    <span className="flex w-full">
                      <Button
                        variant="secondary"
                        size="sm"
                        className="w-full justify-start gap-2"
                        disabled
                      >
                        <KeyRound className="size-3.5" />
                        {issueActionLabel}
                      </Button>
                    </span>
                  </Tooltip>
                ) : (
                  <Button
                    variant="secondary"
                    size="sm"
                    className="w-full justify-start gap-2"
                    onClick={openIssueInitialDialog}
                  >
                    <KeyRound className="size-3.5" />
                    {issueActionLabel}
                  </Button>
                )
              )}
              {canRegenerateAdminRecoveryCodes(adm) && (
                <Button
                  variant="secondary"
                  size="sm"
                  className="w-full justify-start gap-2"
                  onClick={openRegenerateDialog}
                >
                  <RefreshCw className="size-3.5" />
                  {t('detail.regenerateRecoveryCodes')}
                </Button>
              )}
              {isGlobalAdmin && canReissuePendingAdministratorActivationCode(adm) && (
                <Button
                  variant="secondary"
                  size="sm"
                  className="w-full justify-start gap-2"
                  onClick={openReissueActivationDialog}
                >
                  <KeyRound className="size-3.5" />
                  {t('detail.reissueActivationCode')}
                </Button>
              )}

              {isGlobalAdmin && adm.active && (
                <div className="flex items-center gap-2">
                  <Button
                    variant="destructive"
                    size="sm"
                    className="flex-1 justify-start gap-2"
                    onClick={() => setDeactivateOpen(true)}
                  >
                    <PowerOff className="size-3.5" />
                    {t('detail.deactivate')}
                  </Button>
                  <ContextHelp
                    title={t('detail.contextHelp.deactivateTitle')}
                    content={t('detail.contextHelp.deactivateContent')}
                  />
                </div>
              )}

              {isGlobalAdmin && !adm.active && (
                <div className="w-full space-y-2 pt-2">
                  <ReasonFieldRow
                    presetGroup="admin_lifecycle"
                    idPrefix="activate-admin"
                    inputId="activate-admin-reason"
                    value={activateReason}
                    onChange={setActivateReason}
                    label={
                      <>
                        {t('deactivate.reasonLabel')}{' '}
                        <span className="text-fg-muted font-normal">{t('deactivate.reasonHint')}</span>
                      </>
                    }
                    placeholder={t('detail.activateReasonPlaceholder')}
                    showMinLengthError={activateReasonTooShort}
                    childrenAfterInput={<DemoReasonBadges onSelect={setActivateReason} />}
                  />
                  <Button
                    variant="secondary"
                    size="sm"
                    className="w-full justify-start gap-2 text-success border-success/30 hover:bg-success/10"
                    isLoading={activateMutation.isPending}
                    disabled={activateReasonTooShort}
                    onClick={() =>
                      activateMutation.mutate({
                        id: adm.adminId!,
                        reason:
                          activateReason.trim().length >= 10 ? activateReason.trim() : undefined,
                      })
                    }
                  >
                    <Power className="size-3.5" />
                    {t('detail.activate')}
                  </Button>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      <AdminRecoveryCodesDialog
        open={regenerateOpen}
        onClose={handleCloseRegenerateDialog}
        mode={recoveryCodesDialogMode}
        issuanceKind={issuanceKind}
        username={adm.username ?? ''}
        result={regeneratedCodes}
        copied={regeneratedCodesCopied}
        savedConfirmed={regeneratedCodesSavedConfirmed}
        onCopyAll={() => void handleCopyRegeneratedCodes()}
        onSavedConfirmedChange={setRegeneratedCodesSavedConfirmed}
        onConfirm={() => {
          if (recoveryCodesDialogMode === 'issue-initial') {
            issueInitialRecoveryCodesMutation.mutate(adm.adminId!);
            return;
          }
          regenerateRecoveryCodesMutation.mutate(adm.adminId!);
        }}
        pending={regenerateRecoveryCodesMutation.isPending || issueInitialRecoveryCodesMutation.isPending}
        errorMessage={
          regenerateRecoveryCodesMutation.isError || issueInitialRecoveryCodesMutation.isError
            ? getTranslatedApiError(
                regenerateRecoveryCodesMutation.error ?? issueInitialRecoveryCodesMutation.error,
                t,
                t(recoveryCodesDialogCopy(recoveryCodesDialogMode, issuanceKind).error),
              )
            : null
        }
      />
      <Dialog
        open={reissueActivationOpen}
        onClose={handleCloseReissueActivationDialog}
        title={t('detail.reissueActivationCodeDialogTitle', { username: adm.username })}
        size="md"
        dismissible={false}
      >
        {reissuedActivation ? (
          <div className="space-y-4">
            <Alert variant="success">
              {reissuedActivation.message ?? t('detail.reissueActivationCodeSuccess')}
            </Alert>
            <div className="space-y-2 border-2 border-warning/40 bg-bg p-3">
              <div className="border-2 border-warning bg-warning/10 p-3 flex gap-2">
                <AlertTriangle className="size-4 text-warning shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-black text-warning">{t('create.activationCodeWarningTitle')}</p>
                  <p className="text-xs text-warning/80 mt-0.5">{t('create.activationCodeWarningBody')}</p>
                </div>
              </div>
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">{t('create.activationCodeTitle')}</p>
              <p className="text-xs text-fg-muted">{t('create.activationCodeHint')}</p>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => void handleCopyReissuedActivationCode()}
                className="gap-1.5"
              >
                {activationReissueCopied
                  ? <Check className="size-3.5 text-success" />
                  : <Copy className="size-3.5" />}
                {activationReissueCopied ? t('onboarding.copied') : t('create.copyActivationCode')}
              </Button>
              <div className="font-mono text-xs break-all border-2 border-fg/20 p-3 bg-surface">
                {reissuedActivation.activationCode}
              </div>
              {reissuedActivation.activationCodeExpiresAt && (
                <p className="text-xs text-fg-muted">
                  {t('create.activationCodeExpiresAt', {
                    expiresAt: formatDate(reissuedActivation.activationCodeExpiresAt),
                  })}
                </p>
              )}
              <div className="flex items-center gap-2.5 p-3 border-2 border-fg/20 bg-bg">
                <input
                  id="reissued-activation-code-saved"
                  type="checkbox"
                  className="size-4 border-2 border-fg accent-accent"
                  checked={activationReissueSavedConfirmed}
                  onChange={(e) => setActivationReissueSavedConfirmed(e.target.checked)}
                />
                <label htmlFor="reissued-activation-code-saved" className="text-sm font-bold cursor-pointer select-none">
                  {t('create.activationCodeSavedConfirmLabel')}
                </label>
              </div>
            </div>
            <p className="text-sm text-fg-muted">{t('create.activationCodeSuccessHint')}</p>
            <div className="flex justify-end pt-2">
              <Button onClick={handleCloseReissueActivationDialog} disabled={!activationReissueSavedConfirmed}>
                {t('detail.close')}
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <Alert variant="warning">{t('detail.reissueActivationCodeIntro')}</Alert>
            <div className="border-2 border-error bg-error/5 p-3 flex gap-2">
              <AlertTriangle className="size-4 text-error shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-black text-error">{t('detail.reissueActivationCodeWarningTitle')}</p>
                <p className="text-xs text-error/80 mt-0.5">{t('detail.reissueActivationCodeWarningBody')}</p>
              </div>
            </div>
            {reissueActivationCodeMutation.isError && (
              <Alert variant="error">
                {getTranslatedApiError(
                  reissueActivationCodeMutation.error,
                  t,
                  t('detail.errorActivationCodeReissue'),
                )}
              </Alert>
            )}
            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="ghost"
                type="button"
                onClick={handleCloseReissueActivationDialog}
                disabled={reissueActivationCodeMutation.isPending}
              >
                {t('detail.editCancel')}
              </Button>
              <Button
                type="button"
                isLoading={reissueActivationCodeMutation.isPending}
                onClick={() => {
                  const id = adm.adminId;
                  if (id != null) {
                    reissueActivationCodeMutation.mutate(id);
                  }
                }}
              >
                {t('detail.reissueActivationCodeConfirm')}
              </Button>
            </div>
          </div>
        )}
      </Dialog>
      <Dialog
        open={editOpen}
        onClose={() => {
          if (!updateMutation.isPending) setEditOpen(false);
        }}
        title={t('detail.editDialogTitle')}
        size="md"
        dismissible={false}
      >
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="adm-edit-first-name">{t('detail.editFirstName')}</Label>
              <Input
                id="adm-edit-first-name"
                value={editFirstName}
                onChange={(e) => setEditFirstName(e.target.value)}
                maxLength={100}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="adm-edit-last-name">{t('detail.editLastName')}</Label>
              <Input
                id="adm-edit-last-name"
                value={editLastName}
                onChange={(e) => setEditLastName(e.target.value)}
                maxLength={100}
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="adm-edit-email">{t('detail.editEmail')}</Label>
            <Input
              id="adm-edit-email"
              type="email"
              value={editEmail}
              onChange={(e) => setEditEmail(e.target.value)}
              maxLength={255}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="adm-edit-phone">{t('detail.editPhone')}</Label>
            <Input
              id="adm-edit-phone"
              value={editPhone}
              onChange={(e) => setEditPhone(e.target.value)}
              maxLength={50}
            />
            {editPhone.trim() !== '' && !isPhoneNumberInputValid(editPhone) && (
              <p className="text-xs text-error">{t('validation.invalidPhone')}</p>
            )}
          </div>
          {updateMutation.isError && (
            <Alert variant="error">
              {getTranslatedApiError(updateMutation.error, t, t('detail.errorUpdate'))}
            </Alert>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="ghost"
              type="button"
              onClick={() => setEditOpen(false)}
              disabled={updateMutation.isPending}
            >
              {t('detail.editCancel')}
            </Button>
            <Button
              type="button"
              isLoading={updateMutation.isPending}
              disabled={editPhone.trim() !== '' && !isPhoneNumberInputValid(editPhone)}
              onClick={() => {
                const data: AdminUpdateRequestDto = {
                  version: adm.version,
                  firstName: editFirstName.trim() || undefined,
                  lastName: editLastName.trim() || undefined,
                  email: editEmail.trim() || undefined,
                  phoneNumber: normalizePhoneNumberInput(editPhone),
                };
                updateMutation.mutate({ id: adm.adminId!, data });
              }}
            >
              {t('detail.editSave')}
            </Button>
          </div>
        </div>
      </Dialog>
      <DeactivateAdminDialog
        open={deactivateOpen}
        onClose={() => setDeactivateOpen(false)}
        admin={deactivateOpen ? adm : null}
      />
    </div>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

/** Administrator record page `/admins/:id` (#1399), like the other managed entities. */
export default function AdminDetailPage() {
  const { t } = useTranslation('admins');
  const { id } = useParams<{ id: string }>();
  const adminId = Number(id);
  const validId = Number.isInteger(adminId) && adminId > 0;

  const { nav, goPrev, goNext, showEndOfPageHint } = useListDetailPageNavigation({
    currentId: adminId,
    pathPrefix: '/admins',
  });

  const { data: adm, isLoading, isError } = useGetAdminById<RecoveryAwareAdmin>(
    validId ? adminId : 0,
    { query: { enabled: validId } },
  );

  return (
    <AppShell
      title={adm?.username ?? t('list.title')}
      detailNav={
        nav ? (
          <DetailPageNav
            hasPrev={nav.prevId !== undefined}
            hasNext={nav.nextId !== undefined}
            onPrev={goPrev}
            onNext={goNext}
            showEndOfPageHint={showEndOfPageHint}
          />
        ) : undefined
      }
      breadcrumb={[{ label: t('list.title'), path: '/admins' }]}
    >
      {isLoading && (
        <div className="flex items-center justify-center h-32">
          <span className="size-5 border-2 border-fg border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {(!validId || isError) && (
        <Alert variant="error" data-testid="admin-record-not-found">{t('detail.notFound')}</Alert>
      )}
      {adm && <AdminRecord key={adm.adminId} adm={adm} />}
    </AppShell>
  );
}
