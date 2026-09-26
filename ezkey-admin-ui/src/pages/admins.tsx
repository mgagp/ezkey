import { useMemo, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Check, Copy, KeyRound, Pencil, Plus, Power, PowerOff, QrCode, RefreshCw, UserX } from 'lucide-react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { EnrollmentFkLink } from '@/components/feature/fk-detail-links';
import { IntegratedDeliveryNotice } from '@/components/feature/integrated-delivery-notice';
import { OperationalWarning } from '@/components/feature/operational-warning';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ContextHelp } from '@/components/ui/context-help';
import { Dialog } from '@/components/ui/dialog';
import { DetailInfoRow } from '@/components/ui/detail-info-row';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Tooltip } from '@/components/ui/tooltip';
import { useAuth } from '@/context/use-auth';
import { useDemoModeSession } from '@/context/use-demo-mode-session';
import { useToast } from '@/context/use-toast';
import { useDetailNavigation } from '@/hooks/use-detail-navigation';
import { DetailDialogHeaderNav } from '@/components/ui/detail-dialog-header-nav';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { fetchApi, fetchBlobUrl } from '@/lib/api-client';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { isEvaluatorTempSession } from '@/lib/auth';
import { adminDemoPresets, isDemoMode } from '@/lib/demo-mode';
import { isPhoneNumberInputValid, normalizePhoneNumberInput } from '@/lib/phone-number';
import { canReissuePendingAdministratorActivationCode } from '@/lib/admin-pending-activation-eligibility';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import {
  getGetAdminByIdQueryKey,
  listAdmins,
  useCreateGlobalAdmin,
  useCreateTenantAdmin,
  useDeactivateAdmin,
  useGetAdminById,
  useGetAdminOnboarding,
  useUpdateAdmin,
} from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import { useListTenants } from '@/generated/admin-api/tenants/tenants';
import { AdminCreateRequestDtoOnboardingMode } from '@/generated/admin-api/model';
import type {
  AdminCreateRequestDto,
  AdminUpdateRequestDto,
  AdminResponseDto,
  PagedModelAdminResponseDto,
  PagedModelTenantResponseDto,
  TenantResponseDto,
} from '@/generated/admin-api/model';

/** UI-facing shape for admin onboarding (API returns GetAdminOnboarding200). */
interface AdminOnboardingShape {
  enrollmentProofToken?: string;
  enrollmentChallenge?: number;
}

/** UI-facing shape for create admin response (API returns provisioning DTO with optional recovery codes). */
interface AdminProvisioningShape {
  username?: string;
  onboardingMode?: AdminCreateRequestDto['onboardingMode'];
  lifecycleStatus?: AdminResponseDto['lifecycleStatus'];
  activationCode?: string;
  activationCodeExpiresAt?: string;
}

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

function isPendingActivationAdmin(
  admin: Pick<AdminResponseDto, 'lifecycleStatus'> | null | undefined,
): boolean {
  return admin?.lifecycleStatus === 'PENDING_ACTIVATION';
}

function shouldShowTenantOperationalWarning(
  admin: Pick<AdminResponseDto, 'adminType' | 'active' | 'operational' | 'lifecycleStatus'>,
): boolean {
  return (
    admin.adminType !== 'GLOBAL_ADMIN'
    && admin.active === true
    && admin.lifecycleStatus === 'ACTIVE'
    && admin.operational === false
  );
}

function canShowOnboardingCredentials(
  admin: Pick<AdminResponseDto, 'enrollmentId' | 'lifecycleStatus'>,
): boolean {
  return admin.enrollmentId != null && !isPendingActivationAdmin(admin);
}

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

function renderAdminStatusBadge(
  admin: Pick<AdminResponseDto, 'active' | 'lifecycleStatus'>,
  t: ReturnType<typeof useTranslation<'admins'>>['t'],
  scope: 'list' | 'detail',
) {
  if (admin.lifecycleStatus === 'PENDING_ACTIVATION') {
    return <Badge variant="warning">{t(`${scope}.statusPendingActivation`)}</Badge>;
  }
  if (admin.active) {
    return <Badge variant="success">{t(`${scope}.statusActive`)}</Badge>;
  }
  return <Badge variant="muted">{t(`${scope}.statusInactive`)}</Badge>;
}

// ── Admin type badge ───────────────────────────────────────────────────────────

function AdminTypeBadge({ type }: { type: AdminResponseDto['adminType'] }) {
  const { t } = useTranslation('admins');
  if (type === 'GLOBAL_ADMIN') return <Badge variant="warning">{t('adminType.global')}</Badge>;
  if (type === 'INTEGRATION_ADMIN') return <Badge variant="muted">{t('adminType.integration')}</Badge>;
  return <Badge variant="muted">{t('adminType.tenant')}</Badge>;
}

function renderAdminListTenantCell(
  r: AdminResponseDto,
  t: ReturnType<typeof useTranslation<'admins'>>['t'],
) {
  if (r.adminType === 'GLOBAL_ADMIN') {
    return <span className="text-xs text-fg-muted">{t('list.tenantPlatform')}</span>;
  }
  if (r.tenantId == null) {
    return <span className="text-fg-muted">—</span>;
  }
  const label =
    r.tenantName != null && r.tenantName.trim() !== ''
      ? r.tenantName.trim()
      : t('list.tenantFallback', { id: r.tenantId });
  return (
    <Link
      to={`/tenants/${r.tenantId}`}
      className="text-xs font-medium text-accent hover:underline"
    >
      {label}
    </Link>
  );
}

// ── Onboarding credentials dialog ─────────────────────────────────────────────

function OnboardingDialog({
  open,
  onClose,
  adminId,
  adminUsername,
}: {
  open: boolean;
  onClose: () => void;
  adminId: number | null;
  adminUsername: string;
}) {
  const { t } = useTranslation('admins');
  const [tokenCopied, setTokenCopied] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
  const [qrLoading, setQrLoading] = useState(false);

  const { data: rawOnboarding, isLoading, isError } = useGetAdminOnboarding<AdminOnboardingShape>(
    adminId ?? 0,
    { query: { enabled: open && adminId !== null } },
  );
  const onboarding = rawOnboarding as unknown as AdminOnboardingShape | undefined;

  const handleCopy = async () => {
    const token = onboarding?.enrollmentProofToken;
    if (token == null || token === '') return;
    try {
      await navigator.clipboard.writeText(String(token));
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 2000);
    } catch { /* ignore */ }
  };

  const handleToggleQr = async () => {
    if (qrCodeUrl) { URL.revokeObjectURL(qrCodeUrl); setQrCodeUrl(null); return; }
    setQrLoading(true);
    try {
      const url = await fetchBlobUrl(`/api/v1/admins/${adminId}/onboarding/qrcode`);
      setQrCodeUrl(url);
    } catch { /* ignore */ } finally { setQrLoading(false); }
  };

  const handleClose = () => {
    if (qrCodeUrl) { URL.revokeObjectURL(qrCodeUrl); setQrCodeUrl(null); }
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} title={t('onboarding.title', { username: adminUsername })} size="md">
      {isLoading && (
        <div className="flex justify-center py-8">
          <span className="size-5 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
        </div>
      )}
      {isError && <Alert variant="error">{t('onboarding.errorLoad')}</Alert>}
      {onboarding && (
        <div className="space-y-4">
          <Alert variant="info">
            {t('onboarding.alertShare')}
          </Alert>
          <IntegratedDeliveryNotice summary={t('common:integratedDelivery.summary')}>
            {t('onboarding.previewNotice')}
          </IntegratedDeliveryNotice>

          {/* Proof Token */}
          <div className="space-y-1.5">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
              {t('onboarding.enrollmentProofToken')}
            </p>
            <div className="border-2 border-fg p-3 font-mono text-xs break-all bg-bg leading-relaxed">
              {String(onboarding.enrollmentProofToken ?? '')}
            </div>
            <Button variant="secondary" size="sm" onClick={handleCopy} className="gap-1.5">
              {tokenCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
              {tokenCopied ? t('onboarding.copied') : t('onboarding.copyToken')}
            </Button>
          </div>

          {/* Challenge */}
          <div className="border-2 border-fg/30 p-3 bg-bg">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
              {t('onboarding.bindingChallenge')}
            </p>
            <p className="font-mono text-2xl font-black tracking-widest">
              {String(onboarding.enrollmentChallenge ?? '')}
            </p>
          </div>

          {/* QR Code */}
          <div className="space-y-2">
            <Button
              variant="secondary"
              size="sm"
              isLoading={qrLoading}
              onClick={handleToggleQr}
              className="gap-1.5"
            >
              <QrCode className="size-3.5" />
              {qrCodeUrl ? t('onboarding.hideQrCode') : t('onboarding.showQrCode')}
            </Button>
            {qrCodeUrl && (
              <div className="border-2 border-fg p-3 inline-block">
                <img src={qrCodeUrl} alt={t('onboarding.qrCodeAlt')} className="size-48" />
              </div>
            )}
          </div>

          <div className="flex justify-end pt-2">
            <Button onClick={handleClose}>{t('onboarding.close')}</Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}

// ── Deactivate admin dialog ────────────────────────────────────────────────────

function DeactivateAdminDialog({
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

// ── Admin detail dialog ────────────────────────────────────────────────────────

function AdminDetailDialog({
  admin,
  onClose,
  onShowCredentials,
  onRequestDeactivate,
  onPrev,
  onNext,
  hasPrev,
  hasNext,
  showNav,
  showEndOfPageHint,
}: {
  admin: AdminResponseDto | null;
  onClose: () => void;
  onShowCredentials: (id: number, username: string) => void;
  onRequestDeactivate?: (admin: AdminResponseDto) => void;
  onPrev: () => void;
  onNext: () => void;
  hasPrev: boolean;
  hasNext: boolean;
  showNav: boolean;
  showEndOfPageHint: boolean;
}) {
  const { t } = useTranslation('admins');
  const { t: tc } = useTranslation('common');

  useDetailNavigation(admin !== null && showNav, {
    hasPrev: hasPrev && showNav,
    hasNext: hasNext && showNav,
    onPrev,
    onNext,
  });
  const { session } = useAuth();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const isTemporarySelfView =
    session?.tokenPurpose === 'EVALUATOR_TEMP' &&
    session.adminId != null &&
    admin?.adminId === session.adminId;
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
  const [reissueActivationOpen, setReissueActivationOpen] = useState(false);
  const [reissuedActivation, setReissuedActivation] = useState<AdminActivationCodeReissueShape | null>(null);
  const [activationReissueCopied, setActivationReissueCopied] = useState(false);
  const [activationReissueSavedConfirmed, setActivationReissueSavedConfirmed] = useState(false);

  // Fetch live detail from API to ensure fresh data
  const { data: detail } = useGetAdminById<RecoveryAwareAdmin>(
    admin?.adminId ?? 0,
    { query: { enabled: admin !== null } },
  );

  const adm = (detail ?? admin) as RecoveryAwareAdmin | null;

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
      toast(t('detail.toastActivated', { username: adm!.username }), 'success');
      setActivateReason('');
      void queryClient.invalidateQueries({ queryKey: ['admins'] });
      void queryClient.invalidateQueries({ queryKey: ['admin-detail', adm!.adminId] });
    },
    onError: (e: unknown) => toast(getTranslatedApiError(e, t, t('detail.errorActivate')), 'error'),
  });

  const updateMutation = useUpdateAdmin({
    mutation: {
      onSuccess: async () => {
        toast(t('detail.toastUpdated'), 'success');
        await queryClient.invalidateQueries({ queryKey: ['admins'] });
        await queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm!.adminId!) });
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
      toast(t('detail.toastRecoveryCodesRegenerated', { username: adm!.username }), 'success');
      await queryClient.invalidateQueries({ queryKey: ['admins'] });
      await queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm!.adminId!) });
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
      toast(t('detail.toastRecoveryCodesIssued', { username: adm!.username }), 'success');
      await queryClient.invalidateQueries({ queryKey: ['admins'] });
      await queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm!.adminId!) });
    },
    onError: (e: unknown) =>
      toast(getTranslatedApiError(e, t, t('detail.errorRecoveryCodesIssue')), 'error'),
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
      toast(t('detail.toastActivationCodeReissued', { username: adm!.username }), 'success');
      await queryClient.invalidateQueries({ queryKey: ['admins'] });
      await queryClient.invalidateQueries({ queryKey: getGetAdminByIdQueryKey(adm!.adminId!) });
    },
    onError: (e: unknown) =>
      toast(getTranslatedApiError(e, t, t('detail.errorActivationCodeReissue')), 'error'),
  });

  if (!adm) return null;

  const activateReasonTooShort = activateReason.trim().length > 0 && activateReason.trim().length < 10;

  const fullName = [adm.firstName, adm.lastName].filter(Boolean).join(' ');
  const canShowIssueInitialAction = canShowIssueInitialAdminRecoveryCodesAction(adm);
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
    <Dialog
      open={admin !== null}
      onClose={onClose}
      title={t('detail.title', { username: adm.username })}
      size="lg"
      headerActions={
        showNav ? (
          <DetailDialogHeaderNav
            hasPrev={hasPrev}
            hasNext={hasNext}
            onPrev={onPrev}
            onNext={onNext}
          />
        ) : undefined
      }
    >
      <div className="space-y-5">
        {showEndOfPageHint && (
          <p className="text-xs text-fg-muted italic border border-fg/20 bg-fg/[0.03] px-3 py-2">
            {tc('detailNav.endOfPageMore')}
          </p>
        )}
        {/* Info section */}
        <dl className="space-y-2.5">
          <DetailInfoRow label={t('detail.labelAdminId')} valueClassName="break-all"><span className="font-mono">{adm.adminId}</span></DetailInfoRow>
          <DetailInfoRow label={t('detail.labelUsername')} valueClassName="break-all"><span className="font-medium">{adm.username}</span></DetailInfoRow>
          <DetailInfoRow label={t('detail.labelName')} valueClassName="break-all">{fullName || <span className="text-fg-muted">—</span>}</DetailInfoRow>
          <DetailInfoRow label={t('detail.labelEmail')} valueClassName="break-all">{adm.email || <span className="text-fg-muted">—</span>}</DetailInfoRow>
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
          {adm.enrollmentId != null && (
            <DetailInfoRow label={t('detail.labelEnrollmentId')} valueClassName="break-all">
              <EnrollmentFkLink
                enrollmentId={adm.enrollmentId}
                enrollmentName={adm.enrollmentName}
                fallbackLabel={t('detail.enrollmentFallback', { id: adm.enrollmentId })}
              />
            </DetailInfoRow>
          )}
          <DetailInfoRow label={t('detail.labelStatus')} valueClassName="break-all">{renderAdminStatusBadge(adm, t, 'detail')}</DetailInfoRow>
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

        {isTemporarySelfView && (
          <Alert variant="info" data-testid="temporary-session-identity-bind-hint">
            {t('detail.temporarySessionBindHint')}
          </Alert>
        )}

        {isPendingActivationAdmin(adm) && (
          <Alert variant="info">{t('detail.pendingActivationNotice')}</Alert>
        )}

        {shouldShowTenantOperationalWarning(adm) && (
          <OperationalWarning message={t('detail.operationalWarning.tenantInactive')} />
        )}

        {/* Actions */}
        <div className="border-t-2 border-fg/10 pt-4 space-y-3">
          <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('detail.sectionActions')}</h3>
          <div className="flex flex-wrap gap-2">
            {canShowOnboardingCredentials(adm) && (
              <Button
                variant="secondary"
                size="sm"
                className="gap-1.5"
                onClick={() => onShowCredentials(adm.adminId!, adm.username!)}
              >
                <KeyRound className="size-3.5" />
                {t('detail.credentials')}
              </Button>
            )}
            <Button
              variant="secondary"
              size="sm"
              className="gap-1.5"
              onClick={openEditDialog}
            >
              <Pencil className="size-3.5" />
              {t('detail.editProfile')}
            </Button>
            {canShowIssueInitialAction && (
              issueInitialCodesDisabledReason ? (
                <Tooltip content={issueInitialCodesDisabledReason}>
                  <span className="inline-flex">
                    <Button
                      variant="secondary"
                      size="sm"
                      className="gap-1.5"
                      disabled
                    >
                      <KeyRound className="size-3.5" />
                      {t('detail.issueInitialRecoveryCodes')}
                    </Button>
                  </span>
                </Tooltip>
              ) : (
                <Button
                  variant="secondary"
                  size="sm"
                  className="gap-1.5"
                  onClick={openIssueInitialDialog}
                >
                  <KeyRound className="size-3.5" />
                  {t('detail.issueInitialRecoveryCodes')}
                </Button>
              )
            )}
            {canRegenerateAdminRecoveryCodes(adm) && (
              <Button
                variant="secondary"
                size="sm"
                className="gap-1.5"
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
                className="gap-1.5"
                onClick={openReissueActivationDialog}
              >
                <KeyRound className="size-3.5" />
                {t('detail.reissueActivationCode')}
              </Button>
            )}

            {isGlobalAdmin && adm.active && onRequestDeactivate && (
              <div className="flex items-center gap-2">
                <Button
                  variant="destructive"
                  size="sm"
                  className="gap-1.5"
                  onClick={() => onRequestDeactivate(adm)}
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
              <div className="w-full space-y-2">
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
                  className="gap-1.5 text-success border-success/30 hover:bg-success/10"
                  isLoading={activateMutation.isPending}
                  disabled={activateReasonTooShort}
                  onClick={() =>
                    activateMutation.mutate({
                      id: adm!.adminId!,
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
        </div>

        <div className="flex justify-end pt-2">
          <Button onClick={onClose}>{t('detail.close')}</Button>
        </div>
      </div>
      <Dialog
        open={regenerateOpen}
        onClose={handleCloseRegenerateDialog}
        title={
          recoveryCodesDialogMode === 'issue-initial'
            ? t('detail.issueInitialRecoveryCodesDialogTitle', { username: adm.username })
            : t('detail.regenerateRecoveryCodesDialogTitle', { username: adm.username })
        }
        size="md"
        dismissible={false}
      >
        {regeneratedCodes ? (
          <div className="space-y-4">
            <Alert variant="success">
              {regeneratedCodes.message
                ?? (recoveryCodesDialogMode === 'issue-initial'
                  ? t('detail.issueInitialRecoveryCodesSuccess')
                  : t('detail.regenerateRecoveryCodesSuccess'))}
            </Alert>
            <div className="border-2 border-error bg-error/5 p-3 flex gap-2">
              <AlertTriangle className="size-4 text-error shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-black text-error">
                  {recoveryCodesDialogMode === 'issue-initial'
                    ? t('detail.issueInitialRecoveryCodesWarningTitle')
                    : t('detail.regenerateRecoveryCodesWarningTitle')}
                </p>
                <p className="text-xs text-error/80 mt-0.5">
                  {recoveryCodesDialogMode === 'issue-initial'
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
                onClick={() => void handleCopyRegeneratedCodes()}
                className="gap-1.5"
              >
                {regeneratedCodesCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
                {regeneratedCodesCopied ? t('onboarding.copied') : t('detail.copyAllRecoveryCodes')}
              </Button>
              <ul className="font-mono text-xs space-y-1 break-all max-h-48 overflow-y-auto border-2 border-fg/20 p-2 bg-surface">
                {(regeneratedCodes.recoveryCodes ?? []).map((code) => (
                  <li key={code}>{code}</li>
                ))}
              </ul>
              <div className="flex items-center gap-2.5 p-3 border-2 border-fg/20 bg-bg">
                <input
                  id="regenerated-recovery-codes-saved"
                  type="checkbox"
                  className="size-4 border-2 border-fg accent-accent"
                  checked={regeneratedCodesSavedConfirmed}
                  onChange={(e) => setRegeneratedCodesSavedConfirmed(e.target.checked)}
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
              <Button onClick={handleCloseRegenerateDialog} disabled={!regeneratedCodesSavedConfirmed}>
                {t('detail.close')}
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <Alert variant="warning">
              {recoveryCodesDialogMode === 'issue-initial'
                ? t('detail.issueInitialRecoveryCodesIntro')
                : t('detail.regenerateRecoveryCodesIntro')}
            </Alert>
            <div className="border-2 border-error bg-error/5 p-3 flex gap-2">
              <AlertTriangle className="size-4 text-error shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-black text-error">
                  {recoveryCodesDialogMode === 'issue-initial'
                    ? t('detail.issueInitialRecoveryCodesWarningTitle')
                    : t('detail.regenerateRecoveryCodesWarningTitle')}
                </p>
                <p className="text-xs text-error/80 mt-0.5">
                  {recoveryCodesDialogMode === 'issue-initial'
                    ? t('detail.issueInitialRecoveryCodesWarningBody')
                    : t('detail.regenerateRecoveryCodesWarningBody')}
                </p>
              </div>
            </div>
            {(regenerateRecoveryCodesMutation.isError || issueInitialRecoveryCodesMutation.isError) && (
              <Alert variant="error">
                {getTranslatedApiError(
                  regenerateRecoveryCodesMutation.error ?? issueInitialRecoveryCodesMutation.error,
                  t,
                  recoveryCodesDialogMode === 'issue-initial'
                    ? t('detail.errorRecoveryCodesIssue')
                    : t('detail.errorRecoveryCodesRegenerate'),
                )}
              </Alert>
            )}
            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="ghost"
                type="button"
                onClick={handleCloseRegenerateDialog}
                disabled={regenerateRecoveryCodesMutation.isPending || issueInitialRecoveryCodesMutation.isPending}
              >
                {t('detail.editCancel')}
              </Button>
              <Button
                type="button"
                isLoading={regenerateRecoveryCodesMutation.isPending || issueInitialRecoveryCodesMutation.isPending}
                onClick={() => {
                  if (recoveryCodesDialogMode === 'issue-initial') {
                    issueInitialRecoveryCodesMutation.mutate(adm.adminId!);
                    return;
                  }
                  regenerateRecoveryCodesMutation.mutate(adm.adminId!);
                }}
              >
                {recoveryCodesDialogMode === 'issue-initial'
                  ? t('detail.issueInitialRecoveryCodesConfirm')
                  : t('detail.regenerateRecoveryCodesConfirm')}
              </Button>
            </div>
          </div>
        )}
      </Dialog>
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
    </Dialog>
  );
}

// ── Create admin dialog ────────────────────────────────────────────────────────

function CreateAdminDialog({
  open,
  onClose,
  defaultGlobal = false,
  defaultTenantId,
}: {
  open: boolean;
  onClose: () => void;
  defaultGlobal?: boolean;
  /** When set (e.g. from tenant detail deep link), opens tenant-admin flow with this tenant pre-selected. */
  defaultTenantId?: number | null;
}) {
  const { t } = useTranslation('admins');
  const { session } = useAuth();
  const callerIsGlobal = session?.adminType === 'GLOBAL_ADMIN';
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { sessionDemoOn } = useDemoModeSession();
  const [createdAdmin, setCreatedAdmin] = useState<AdminProvisioningShape | null>(null);
  const [provisioningMaterialCopied, setProvisioningMaterialCopied] = useState(false);
  const [provisioningMaterialSavedConfirmed, setProvisioningMaterialSavedConfirmed] = useState(false);
  const [isGlobalType, setIsGlobalType] = useState(
    defaultTenantId != null && defaultTenantId > 0 ? false : defaultGlobal,
  );
  const [onboardingMode, setOnboardingMode] = useState<AdminCreateRequestDto['onboardingMode']>(
    AdminCreateRequestDtoOnboardingMode.IMMEDIATE,
  );

  const [tenantFilter, setTenantFilter] = useState('');

  const { data: tenantsData } = useListTenants({ page: 0, size: 100, sort: ['tenantName,asc'] });
  const allTenants = useMemo(
    () => (tenantsData as PagedModelTenantResponseDto | undefined)?.content ?? [],
    [tenantsData],
  );
  const eligibleTenants = useMemo(
    () => allTenants.filter((t) => !t.isSystemTenant),
    [allTenants],
  );

  const adminSchema = useMemo(
    () => {
      const base = z.object({
        username: z.string().min(3, t('validation.usernameMin')).max(50, t('validation.usernameMax')),
        email: z.string().email(t('validation.invalidEmail')).optional().or(z.literal('')),
        phoneNumber: z
          .string()
          .refine((value) => value === '' || isPhoneNumberInputValid(value), t('validation.invalidPhone'))
          .optional()
          .or(z.literal('')),
        firstName: z.string().max(100).optional().or(z.literal('')),
        lastName: z.string().max(100).optional().or(z.literal('')),
        tenantId: z.union([z.number(), z.string()]).optional().or(z.literal('')),
      });
      return base
        .refine(
          (data) => !(callerIsGlobal && !isGlobalType) || (data.tenantId != null && data.tenantId !== ''),
          { message: t('validation.selectTenant'), path: ['tenantId'] },
        )
        .refine(
          (data) => !isGlobalType || (typeof data.email === 'string' && data.email.trim().length > 0),
          { message: t('validation.emailRequired'), path: ['email'] },
        )
        .refine(
          (data) => !isGlobalType || (typeof data.firstName === 'string' && data.firstName.trim().length > 0),
          { message: t('validation.firstNameRequired'), path: ['firstName'] },
        )
        .refine(
          (data) => !isGlobalType || (typeof data.lastName === 'string' && data.lastName.trim().length > 0),
          { message: t('validation.lastNameRequired'), path: ['lastName'] },
        )
        .refine(
          (data) =>
            !isGlobalType ||
            !(typeof data.email === 'string' && data.email.trim().length > 0) ||
            /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email.trim()),
          { message: t('validation.invalidEmail'), path: ['email'] },
        );
    },
    [callerIsGlobal, isGlobalType, t],
  );
  type AdminFormValues = z.infer<typeof adminSchema>;

  const defaultFormValues = useMemo<AdminFormValues>(
    () => ({
      username: '',
      email: '',
      phoneNumber: '',
      firstName: '',
      lastName: '',
      tenantId: defaultTenantId != null && defaultTenantId > 0 ? String(defaultTenantId) : '',
    }),
    [defaultTenantId],
  );
  const { register, handleSubmit, reset, control, setValue, getValues, formState: { errors } } = useForm<AdminFormValues>({
    resolver: zodResolver(adminSchema),
    defaultValues: defaultFormValues,
  });

  const watchedTenantId = useWatch({ control, name: 'tenantId' });
  const tenantAdminNeedsTenant = callerIsGlobal && !isGlobalType;

  const filteredTenants = useMemo(() => {
    const q = tenantFilter.trim().toLowerCase();
    if (!q) return eligibleTenants;
    const matches = (t: TenantResponseDto) => {
      const searchable = [
        t.tenantName,
        t.organizationName,
        t.organizationDomain,
        t.tenantId?.toString(),
      ];
      return searchable.some((v) => v != null && String(v).toLowerCase().includes(q));
    };
    const list = eligibleTenants.filter(matches);
    const selected = watchedTenantId != null && watchedTenantId !== ''
      ? eligibleTenants.find((t) => t.tenantId === Number(watchedTenantId))
      : undefined;
    if (selected != null && !list.some((t) => t.tenantId === selected.tenantId)) {
      return [selected, ...list];
    }
    return list;
  }, [eligibleTenants, tenantFilter, watchedTenantId]);

  const canSubmitTenantAdmin =
    !tenantAdminNeedsTenant ||
    (eligibleTenants.length > 0 && watchedTenantId != null && watchedTenantId !== '');

  const onCreated = (admin: AdminProvisioningShape, isGlobal: boolean) => {
    setCreatedAdmin(admin);
    void queryClient.invalidateQueries({ queryKey: ['admins'] });
    const typeLabel = isGlobal ? t('adminType.global') : t('adminType.tenant');
    toast(t('create.toastCreated', { type: typeLabel, username: admin.username }));
  };

  const mapProvisioningResponse = (data: unknown): AdminProvisioningShape => {
    const r = data as Record<string, unknown>;
    return {
      username: typeof r.username === 'string' ? r.username : undefined,
      onboardingMode:
        r.onboardingMode === 'IMMEDIATE' || r.onboardingMode === 'ACTIVATION_CODE'
          ? (r.onboardingMode as AdminCreateRequestDto['onboardingMode'])
          : undefined,
      lifecycleStatus:
        r.lifecycleStatus === 'PENDING_ACTIVATION'
          || r.lifecycleStatus === 'ACTIVE'
          || r.lifecycleStatus === 'DEACTIVATED'
          ? (r.lifecycleStatus as AdminResponseDto['lifecycleStatus'])
          : undefined,
      activationCode: typeof r.activationCode === 'string' ? r.activationCode : undefined,
      activationCodeExpiresAt:
        typeof r.activationCodeExpiresAt === 'string' ? r.activationCodeExpiresAt : undefined,
    };
  };

  const createGlobalMutation = useCreateGlobalAdmin({
    mutation: {
      onSuccess: (data) => {
        onCreated(mapProvisioningResponse(data), true);
      },
    },
  });

  const createTenantMutation = useCreateTenantAdmin({
    mutation: {
      onSuccess: (data) => {
        onCreated(mapProvisioningResponse(data), false);
      },
    },
  });

  const createMutation = {
    mutate: ({ body, isGlobal }: { body: AdminCreateRequestDto; isGlobal: boolean }) => {
      if (isGlobal) createGlobalMutation.mutate({ data: body });
      else createTenantMutation.mutate({ data: body });
    },
    isPending: createGlobalMutation.isPending || createTenantMutation.isPending,
    isError: createGlobalMutation.isError || createTenantMutation.isError,
    error: createGlobalMutation.error ?? createTenantMutation.error,
    reset: () => {
      createGlobalMutation.reset();
      createTenantMutation.reset();
    },
  };

  const handleCopyProvisioningMaterial = async () => {
    const activationCode = createdAdmin?.activationCode;
    const text =
      typeof activationCode === 'string' && activationCode.length > 0
        ? activationCode
        : null;
    if (text == null) return;
    try {
      await navigator.clipboard.writeText(text);
      setProvisioningMaterialCopied(true);
      setTimeout(() => setProvisioningMaterialCopied(false), 2000);
    } catch {
      /* ignore clipboard API errors */
    }
  };

  const createdAdminHasActivationCode =
    createdAdmin?.activationCode != null && createdAdmin.activationCode.length > 0;
  const requiresProvisioningMaterialConfirmation = createdAdminHasActivationCode;

  const handleClose = () => {
    if (createdAdmin && requiresProvisioningMaterialConfirmation && !provisioningMaterialSavedConfirmed) {
      return;
    }
    reset();
    setCreatedAdmin(null);
    setProvisioningMaterialCopied(false);
    setProvisioningMaterialSavedConfirmed(false);
    setIsGlobalType(defaultTenantId != null && defaultTenantId > 0 ? false : defaultGlobal);
    setOnboardingMode(AdminCreateRequestDtoOnboardingMode.IMMEDIATE);
    setTenantFilter('');
    createMutation.reset();
    onClose();
  };

  const onSubmit = (values: AdminFormValues) => {
    const tenantId =
      callerIsGlobal && !isGlobalType && values.tenantId != null && values.tenantId !== ''
        ? Number(values.tenantId)
        : undefined;
    createMutation.mutate({
      body: {
        username: values.username.trim(),
        email: values.email || undefined,
        phoneNumber: normalizePhoneNumberInput(values.phoneNumber),
        firstName: values.firstName || undefined,
        lastName: values.lastName || undefined,
        onboardingMode,
        ...(tenantId !== undefined ? { tenantId } : {}),
      },
      isGlobal: isGlobalType,
    });
  };

  return (
    <Dialog open={open} onClose={handleClose} title={isGlobalType ? t('create.titleGlobal') : t('create.titleTenant')} size="md" dismissible={false}>
      {createdAdmin ? (
        <div className="space-y-4">
          <Alert variant="success">
            {t('create.successMessage', { username: String(createdAdmin.username ?? '') })}
          </Alert>
          {createdAdminHasActivationCode && (
            <div className="space-y-2 border-2 border-warning/40 bg-bg p-3">
              <div className="border-2 border-warning bg-warning/10 p-3 flex gap-2">
                <AlertTriangle className="size-4 text-warning shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-black text-warning">{t('create.activationCodeWarningTitle')}</p>
                  <p className="text-xs text-warning/80 mt-0.5">{t('create.activationCodeWarningBody')}</p>
                </div>
              </div>
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
                {t('create.activationCodeTitle')}
              </p>
              <p className="text-xs text-fg-muted">{t('create.activationCodeHint')}</p>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => void handleCopyProvisioningMaterial()}
                className="gap-1.5"
              >
                {provisioningMaterialCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
                {provisioningMaterialCopied ? t('onboarding.copied') : t('create.copyActivationCode')}
              </Button>
              <div className="font-mono text-xs break-all border-2 border-fg/20 p-3 bg-surface">
                {createdAdmin.activationCode}
              </div>
              {createdAdmin.activationCodeExpiresAt && (
                <p className="text-xs text-fg-muted">
                  {t('create.activationCodeExpiresAt', {
                    expiresAt: formatDate(createdAdmin.activationCodeExpiresAt),
                  })}
                </p>
              )}
              <div className="flex items-center gap-2.5 p-3 border-2 border-fg/20 bg-bg">
                <input
                  id="activation-code-saved"
                  type="checkbox"
                  className="size-4 border-2 border-fg accent-accent"
                  checked={provisioningMaterialSavedConfirmed}
                  onChange={(e) => setProvisioningMaterialSavedConfirmed(e.target.checked)}
                />
                <label htmlFor="activation-code-saved" className="text-sm font-bold cursor-pointer select-none">
                  {t('create.activationCodeSavedConfirmLabel')}
                </label>
              </div>
            </div>
          )}
          <p className="text-sm text-fg-muted">
            {createdAdminHasActivationCode ? t('create.activationCodeSuccessHint') : t('create.successHint')}
          </p>
          <div className="flex justify-end pt-2">
            <Button
              onClick={handleClose}
              disabled={requiresProvisioningMaterialConfirmation && !provisioningMaterialSavedConfirmed}
            >
              {t('create.done')}
            </Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {isDemoMode && sessionDemoOn && (
            <div className="flex flex-wrap items-center gap-2 p-2 border-2 border-accent/30 bg-accent/5">
              <span className="text-xs font-bold text-fg-muted uppercase tracking-wider">{t('create.fillDemo')}</span>
              {adminDemoPresets.map((preset) => (
                <Button
                  key={preset.id}
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setIsGlobalType(preset.isGlobal);
                    reset({
                      ...preset.values,
                      tenantId: preset.isGlobal ? '' : getValues('tenantId') ?? '',
                    });
                  }}
                >
                  {preset.label}
                </Button>
              ))}
            </div>
          )}
          {/* Admin type toggle — only visible when the caller is a GLOBAL_ADMIN */}
          {callerIsGlobal && (
            <div className="flex items-center gap-3 p-3 border-2 border-fg/20 bg-bg">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name="adminType"
                  checked={!isGlobalType}
                  onChange={() => setIsGlobalType(false)}
                  className="accent-accent"
                />
                <span className="text-sm font-medium">{t('create.typeTenant')}</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name="adminType"
                  checked={isGlobalType}
                  onChange={() => {
                    setIsGlobalType(true);
                    setValue('tenantId', '');
                    setTenantFilter('');
                  }}
                  className="accent-accent"
                />
                <span className="text-sm font-medium">{t('create.typeGlobal')}</span>
              </label>
            </div>
          )}
          <div className="space-y-1">
            <Label htmlFor="adm-onboarding-mode">{t('create.onboardingModeLabel')}</Label>
            <Select
              id="adm-onboarding-mode"
              value={onboardingMode}
              onChange={(e) =>
                setOnboardingMode(e.target.value as AdminCreateRequestDto['onboardingMode'])
              }
            >
              <option value={AdminCreateRequestDtoOnboardingMode.IMMEDIATE}>
                {t('create.onboardingModeImmediate')}
              </option>
              <option value={AdminCreateRequestDtoOnboardingMode.ACTIVATION_CODE}>
                {t('create.onboardingModeActivationCode')}
              </option>
            </Select>
            <p className="text-xs text-fg-muted">
              {onboardingMode === AdminCreateRequestDtoOnboardingMode.ACTIVATION_CODE
                ? t('create.onboardingModeActivationCodeHelp')
                : t('create.onboardingModeImmediateHelp')}
            </p>
          </div>
          {tenantAdminNeedsTenant && (
            <>
              {eligibleTenants.length === 0 ? (
                <Alert variant="warning">
                  {t('create.noTenantAvailable')}{' '}
                  <Link to="/tenants" className="font-medium text-accent underline">{t('create.createTenantLink')}</Link>.
                </Alert>
              ) : (
                <div className="space-y-2">
                  <div className="space-y-1">
                    <Label htmlFor="adm-tenant-filter">{t('create.filterTenants')}</Label>
                    <Input
                      id="adm-tenant-filter"
                      type="text"
                      placeholder={t('create.filterTenantsPlaceholder')}
                      value={tenantFilter}
                      onChange={(e) => setTenantFilter(e.target.value)}
                      className="text-sm"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="adm-tenant">{t('create.assignTenant')}</Label>
                    <Select id="adm-tenant" error={errors.tenantId?.message} {...register('tenantId')}>
                      <option value="">{t('create.selectTenant')}</option>
                      {filteredTenants.map((tenant) => (
                        <option key={tenant.tenantId} value={tenant.tenantId}>
                          {tenant.tenantName ?? ''}
                        </option>
                      ))}
                      {filteredTenants.length === 0 && tenantFilter.trim() !== '' && (
                        <option value="" disabled>{t('create.noTenantMatches')}</option>
                      )}
                    </Select>
                  </div>
                </div>
              )}
            </>
          )}
          <div className="space-y-1">
            <Label htmlFor="adm-username">{t('create.usernameLabel')}</Label>
            <Input id="adm-username" placeholder={t('create.usernamePlaceholder')} error={errors.username?.message} {...register('username')} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label htmlFor="adm-fname">{t('create.firstNameLabel')}{isGlobalType ? ' *' : ''}</Label>
              <Input id="adm-fname" placeholder={t('create.firstNamePlaceholder')} error={errors.firstName?.message} {...register('firstName')} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="adm-lname">{t('create.lastNameLabel')}{isGlobalType ? ' *' : ''}</Label>
              <Input id="adm-lname" placeholder={t('create.lastNamePlaceholder')} error={errors.lastName?.message} {...register('lastName')} />
            </div>
          </div>
          <div className="space-y-1">
            <Label htmlFor="adm-email">
              {t('create.emailLabel')} {isGlobalType ? '*' : <span className="text-fg-muted font-normal">{t('create.emailOptional')}</span>}
            </Label>
            <Input id="adm-email" type="email" placeholder={t('create.emailPlaceholder')} error={errors.email?.message} {...register('email')} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="adm-phone">
              {t('create.phoneLabel')} <span className="text-fg-muted font-normal">{t('create.emailOptional')}</span>
            </Label>
            <Input id="adm-phone" placeholder={t('create.phonePlaceholder')} error={errors.phoneNumber?.message} {...register('phoneNumber')} />
          </div>

          {createMutation.isError && (
            <Alert variant="error">
              {getTranslatedApiError(createMutation.error, t, t('create.errorCreate'))}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>{t('create.cancel')}</Button>
            <Button
              type="submit"
              isLoading={createMutation.isPending}
              disabled={!canSubmitTenantAdmin}
            >
              {t('create.submit')}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AdminsPage() {
  const { t } = useTranslation('admins');
  const { session } = useAuth();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const isTempSession = isEvaluatorTempSession(session);
  const canCreateAdmin = !isTempSession;
  const [searchParams, setSearchParams] = useSearchParams();

  const defaultTenantIdFromUrl = useMemo(() => {
    if (!canCreateAdmin) return null;
    const flag = searchParams.get('createTenantAdmin');
    if (flag !== '1' && flag !== 'true') return null;
    const n = Number(searchParams.get('tenantId'));
    return Number.isFinite(n) && n > 0 ? n : null;
  }, [searchParams, canCreateAdmin]);

  /** Deep-link from other screens (e.g. tenant admins table, enrollment “created by”). */
  const adminIdFromUrl = useMemo(() => {
    const raw = searchParams.get('adminId');
    if (raw == null || raw === '') return null;
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? n : null;
  }, [searchParams]);

  const { data: adminFromUrl } = useGetAdminById<AdminResponseDto>(
    adminIdFromUrl ?? 0,
    { query: { enabled: adminIdFromUrl != null } },
  );

  const [createOpen, setCreateOpen] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [onboardingTarget, setOnboardingTarget] = useState<{ id: number; username: string } | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<AdminResponseDto | null>(null);

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<AdminResponseDto, Record<string, never>>({
    queryKey: ['admins'],
    baseParams: {},
    fetchPage: (params) => listAdmins(params) as Promise<PagedModelAdminResponseDto>,
  });

  const selectedAdmin = selectedIndex !== null ? data[selectedIndex] ?? null : null;
  const dialogAdmin = selectedAdmin ?? (adminFromUrl != null ? adminFromUrl : null);

  const clearAdminIdFromUrl = useCallback(() => {
    if (!searchParams.has('adminId')) return;
    const next = new URLSearchParams(searchParams);
    next.delete('adminId');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const handleCloseDetailDialog = useCallback(() => {
    setSelectedIndex(null);
    clearAdminIdFromUrl();
  }, [clearAdminIdFromUrl]);
  const showRowNav = data.length > 1;
  const hasPrev = selectedIndex !== null && selectedIndex > 0;
  const hasNext = selectedIndex !== null && selectedIndex < data.length - 1;
  const showEndOfPageHint =
    selectedIndex !== null &&
    data.length > 0 &&
    selectedIndex === data.length - 1 &&
    !pagination.isLast;

  const goPrevAdmin = useCallback(() => {
    setSelectedIndex((i) => (i !== null && i > 0 ? i - 1 : i));
  }, []);

  const goNextAdmin = useCallback(() => {
    setSelectedIndex((i) => {
      if (i === null) return i;
      return i < data.length - 1 ? i + 1 : i;
    });
  }, [data.length]);

  const handleCloseCreateDialog = () => {
    setCreateOpen(false);
    if (searchParams.has('createTenantAdmin') || searchParams.has('tenantId')) {
      const next = new URLSearchParams(searchParams);
      next.delete('createTenantAdmin');
      next.delete('tenantId');
      setSearchParams(next, { replace: true });
    }
  };

  const columns: ColumnDef<AdminResponseDto>[] = useMemo(() => {
    const idCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.id'),
      key: 'adminId',
      className: 'w-14',
      sortKey: 'adminId',
      render: (r) => <span className="font-mono text-xs">{r.adminId}</span>,
    };
    const usernameCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.username'),
      key: 'username',
      sortKey: 'username',
      render: (r) => <span className="font-medium">{r.username}</span>,
    };
    const nameCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.name'),
      key: 'name',
      render: (r) => (
        <span className="text-fg-muted text-xs">
          {[r.firstName, r.lastName].filter(Boolean).join(' ') || '—'}
        </span>
      ),
    };
    const emailCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.email'),
      key: 'email',
      render: (r) => <span className="text-xs text-fg-muted">{r.email ?? '—'}</span>,
    };
    const typeCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.type'),
      key: 'adminType',
      sortKey: 'adminType',
      render: (r) => <AdminTypeBadge type={r.adminType} />,
    };
    const statusCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.status'),
      key: 'active',
      sortKey: 'active',
      render: (r) => (isPendingActivationAdmin(r) ? (
        <Tooltip content={t('list.pendingActivationTooltip')}>
          {renderAdminStatusBadge(r, t, 'list')}
        </Tooltip>
      ) : shouldShowTenantOperationalWarning(r) ? (
        <Tooltip content={t('list.operationalWarningTooltip')}>
          <span className="inline-flex items-center gap-1 border border-warning/40 rounded-sm px-1.5">
            <Badge variant="success">{t('list.activeYes')}</Badge>
            <AlertTriangle className="size-4 text-warning" aria-hidden />
          </span>
        </Tooltip>
      ) : (
        renderAdminStatusBadge(r, t, 'list')
      )),
    };
    const lastLoginCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.lastLogin'),
      key: 'lastLoginAt',
      render: (r) => (r.lastLoginAt ? (
        <span className="text-xs text-fg-muted">
          {formatDate(r.lastLoginAt)}
          <span className="text-fg-muted/80 ml-1">({formatRelativeTime(r.lastLoginAt)})</span>
        </span>
      ) : (
        <span className="text-xs text-fg-muted italic">{t('detail.lastLoginNever')}</span>
      )),
    };
    const createdCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.created'),
      key: 'createdAt',
      sortKey: 'createdAt',
      render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.createdAt ?? '')}</span>,
    };
    const tenantCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.tenant'),
      key: 'tenantId',
      render: (r) => renderAdminListTenantCell(r, t),
    };
    const actionsCol: ColumnDef<AdminResponseDto> = {
      header: t('list.columns.actions'),
      key: 'actions',
      render: (r) => (
        <div className="flex items-center gap-1.5">
          {canShowOnboardingCredentials(r) && (
            <Button
              variant="secondary"
              size="sm"
              className="gap-1"
              onClick={(e) => { e.stopPropagation(); setOnboardingTarget({ id: r.adminId!, username: r.username! }); }}
            >
              <KeyRound className="size-3" />
              {t('list.credentials')}
            </Button>
          )}
          {isGlobalAdmin && r.active && (
            <Button
              variant="ghost"
              size="sm"
              className="gap-1 text-error hover:bg-error/10 border border-error/30 hover:border-error"
              onClick={(e) => { e.stopPropagation(); setDeactivateTarget(r); }}
            >
              <UserX className="size-3" />
              {t('list.deactivate')}
            </Button>
          )}
        </div>
      ),
    };

    if (isGlobalAdmin) {
      return [
        idCol,
        usernameCol,
        nameCol,
        emailCol,
        tenantCol,
        typeCol,
        statusCol,
        lastLoginCol,
        createdCol,
        actionsCol,
      ];
    }
    return [
      idCol,
      usernameCol,
      nameCol,
      emailCol,
      typeCol,
      statusCol,
      lastLoginCol,
      createdCol,
      actionsCol,
    ];
  }, [isGlobalAdmin, t, setOnboardingTarget]);

  return (
    <AppShell title={t('list.title')}>
      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
              <RefreshCw className="size-3.5" />
              {t('list.refresh')}
            </Button>
          </div>
          {canCreateAdmin && (
            <Button
              size="sm"
              onClick={() => setCreateOpen(true)}
              className="gap-1.5"
              data-testid="admins-new-admin"
            >
              <Plus className="size-3.5" />
              {t('list.newAdmin')}
            </Button>
          )}
        </div>

        <div>
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => {
              const idx = data.findIndex((r) => r.adminId === row.adminId);
              setSelectedIndex(idx >= 0 ? idx : null);
              clearAdminIdFromUrl();
            }}
            keyExtractor={(r, i) => r.adminId ?? i}
            emptyMessage={t('list.emptyMessage')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>
      </div>

      <AdminDetailDialog
        admin={dialogAdmin}
        onClose={handleCloseDetailDialog}
        onShowCredentials={(id, username) => {
          setSelectedIndex(null);
          clearAdminIdFromUrl();
          setOnboardingTarget({ id, username });
        }}
        onRequestDeactivate={(a) => {
          setSelectedIndex(null);
          clearAdminIdFromUrl();
          setDeactivateTarget(a);
        }}
        onPrev={goPrevAdmin}
        onNext={goNextAdmin}
        hasPrev={hasPrev}
        hasNext={hasNext}
        showNav={showRowNav}
        showEndOfPageHint={showEndOfPageHint}
      />
      {canCreateAdmin && (createOpen || defaultTenantIdFromUrl != null) && (
        <CreateAdminDialog
          open
          onClose={handleCloseCreateDialog}
          defaultGlobal={defaultTenantIdFromUrl != null ? false : isGlobalAdmin}
          defaultTenantId={defaultTenantIdFromUrl}
        />
      )}
      <OnboardingDialog
        open={onboardingTarget !== null}
        onClose={() => setOnboardingTarget(null)}
        adminId={onboardingTarget?.id ?? null}
        adminUsername={onboardingTarget?.username ?? ''}
      />
      <DeactivateAdminDialog
        open={deactivateTarget !== null}
        onClose={() => setDeactivateTarget(null)}
        admin={deactivateTarget}
      />
    </AppShell>
  );
}
