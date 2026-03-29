import { useEffect, useMemo, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, Copy, KeyRound, Plus, Power, PowerOff, QrCode, RefreshCw, UserX } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { useAuth } from '@/context/auth-context';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { useToast } from '@/context/toast-context';
import { useDetailNavigation } from '@/hooks/use-detail-navigation';
import { useExpandableRelatedDetails } from '@/hooks/use-expandable-related-details';
import { DetailDialogHeaderNav } from '@/components/ui/detail-dialog-header-nav';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { fetchApi, fetchBlobUrl, getApiErrorMessage } from '@/lib/api-client';
import { adminDemoPresets, isDemoMode } from '@/lib/demo-mode';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import {
  listAdmins,
  useCreateGlobalAdmin,
  useCreateTenantAdmin,
  useDeactivateAdmin,
  useGetAdminById,
  useGetAdminOnboarding,
} from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import { useListTenants } from '@/generated/admin-api/tenants/tenants';
import type {
  AdminCreateRequestDto,
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
  recoveryCodes?: string[];
}

// ── Admin type badge ───────────────────────────────────────────────────────────

function AdminTypeBadge({ type }: { type: AdminResponseDto['adminType'] }) {
  const { t } = useTranslation('admins');
  if (type === 'GLOBAL_ADMIN') return <Badge variant="warning">{t('adminType.global')}</Badge>;
  if (type === 'INTEGRATION_ADMIN') return <Badge variant="muted">{t('adminType.integration')}</Badge>;
  return <Badge variant="muted">{t('adminType.tenant')}</Badge>;
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
          <p className="text-xs text-fg-muted leading-relaxed border-l-2 border-fg/25 pl-3 py-0.5">
            {t('onboarding.previewNotice')}
          </p>

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

  const reasonInvalid = reason.length > 0 && reason.length < 10;
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

          <div className="space-y-1.5">
            <Label htmlFor="deactivate-admin-reason">
              {t('deactivate.reasonLabel')} <span className="text-fg-muted font-normal">{t('deactivate.reasonHint')}</span>
            </Label>
            <Input
              id="deactivate-admin-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t('deactivate.reasonPlaceholder')}
            />
            <DemoReasonBadges onSelect={setReason} />
          </div>

          {deactivateMutation.isError && (
            <Alert variant="error">
              {getApiErrorMessage(deactivateMutation.error, t('deactivate.errorDeactivate'))}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>
              {t('deactivate.cancel')}
            </Button>
            <Button
              variant="destructive"
              isLoading={deactivateMutation.isPending}
              disabled={reasonInvalid}
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
  const queryClient = useQueryClient();
  const { toast } = useToast();

  // Fetch live detail from API to ensure fresh data
  const { data: detail } = useGetAdminById<AdminResponseDto>(
    admin?.adminId ?? 0,
    { query: { enabled: admin !== null } },
  );

  const adm = detail ?? admin;

  const relatedDetails = useExpandableRelatedDetails({
    tenantId: adm?.tenantId ?? undefined,
  });

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
    onError: (e: unknown) => toast(getApiErrorMessage(e, t('detail.errorActivate')), 'error'),
  });

  if (!adm) return null;

  const activateReasonInvalid = activateReason.length > 0 && activateReason.length < 10;

  const fullName = [adm.firstName, adm.lastName].filter(Boolean).join(' ');

  function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div className="flex gap-4">
        <dt className="w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">{label}</dt>
        <dd className="text-sm break-all">{children}</dd>
      </div>
    );
  }

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
        {relatedDetails.hasAnyFk && (
          <div className="flex justify-end">
            <Button
              variant="secondary"
              size="sm"
              onClick={relatedDetails.expand}
              disabled={relatedDetails.isExpanded && relatedDetails.isLoading}
            >
              {relatedDetails.isExpanded && relatedDetails.isLoading
                ? t('common:buttons.loading')
                : t('common:detail.moreDetails')}
            </Button>
          </div>
        )}
        {/* Info section */}
        <dl className="space-y-2.5">
          <InfoRow label={t('detail.labelAdminId')}><span className="font-mono">{adm.adminId}</span></InfoRow>
          <InfoRow label={t('detail.labelUsername')}><span className="font-medium">{adm.username}</span></InfoRow>
          <InfoRow label={t('detail.labelName')}>{fullName || <span className="text-fg-muted">—</span>}</InfoRow>
          <InfoRow label={t('detail.labelEmail')}>{adm.email || <span className="text-fg-muted">—</span>}</InfoRow>
          <InfoRow label={t('detail.labelType')}><AdminTypeBadge type={adm.adminType} /></InfoRow>
          {adm.tenantId != null && (
            <InfoRow label={t('detail.labelTenantId')}><span className="font-mono">{adm.tenantId}</span></InfoRow>
          )}
          {relatedDetails.isExpanded && relatedDetails.tenant && (
            <InfoRow label={t('common:detail.relatedTenant')}>
              <Link
                to={`/tenants/${relatedDetails.tenant.tenantId}`}
                className="font-medium text-accent hover:underline"
              >
                {relatedDetails.tenant.tenantName ?? relatedDetails.tenant.tenantId} (ID {relatedDetails.tenant.tenantId})
              </Link>
            </InfoRow>
          )}
          <InfoRow label={t('detail.labelStatus')}><Badge variant={adm.active ? 'success' : 'muted'}>{adm.active ? t('detail.statusActive') : t('detail.statusInactive')}</Badge></InfoRow>
          <InfoRow label={t('detail.labelCreated')}>{adm.createdAt ? formatDate(adm.createdAt) : '—'}</InfoRow>
          <InfoRow label={t('detail.labelLastLogin')}>
            {adm.lastLoginAt ? (
              <span>
                {formatDate(adm.lastLoginAt)}
                <span className="text-fg-muted text-xs ml-1.5">({formatRelativeTime(adm.lastLoginAt)})</span>
              </span>
            ) : (
              <span className="text-fg-muted italic">{t('detail.lastLoginNever')}</span>
            )}
          </InfoRow>
        </dl>

        {/* Actions */}
        <div className="border-t-2 border-fg/10 pt-4 space-y-3">
          <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">{t('detail.sectionActions')}</h3>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              size="sm"
              className="gap-1.5"
              onClick={() => onShowCredentials(adm.adminId!, adm.username!)}
            >
              <KeyRound className="size-3.5" />
              {t('detail.credentials')}
            </Button>

            {isGlobalAdmin && adm.active && onRequestDeactivate && (
              <Button
                variant="destructive"
                size="sm"
                className="gap-1.5"
                onClick={() => onRequestDeactivate(adm)}
              >
                <PowerOff className="size-3.5" />
                {t('detail.deactivate')}
              </Button>
            )}

            {isGlobalAdmin && !adm.active && (
              <div className="w-full space-y-2">
                <div className="space-y-1.5">
                  <Label htmlFor="activate-admin-reason">
                    {t('deactivate.reasonLabel')}{' '}
                    <span className="text-fg-muted font-normal">{t('deactivate.reasonHint')}</span>
                  </Label>
                  <Input
                    id="activate-admin-reason"
                    value={activateReason}
                    onChange={(e) => setActivateReason(e.target.value)}
                    placeholder={t('detail.activateReasonPlaceholder')}
                  />
                  <DemoReasonBadges onSelect={setActivateReason} />
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  className="gap-1.5 text-success border-success/30 hover:bg-success/10"
                  isLoading={activateMutation.isPending}
                  disabled={activateReasonInvalid}
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
    </Dialog>
  );
}

// ── Create admin dialog ────────────────────────────────────────────────────────

function CreateAdminDialog({ open, onClose, defaultGlobal = false }: { open: boolean; onClose: () => void; defaultGlobal?: boolean }) {
  const { t } = useTranslation('admins');
  const { session } = useAuth();
  const callerIsGlobal = session?.adminType === 'GLOBAL_ADMIN';
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { sessionDemoOn } = useDemoModeSession();
  const [createdAdmin, setCreatedAdmin] = useState<AdminProvisioningShape | null>(null);
  const [recoveryCodesCopied, setRecoveryCodesCopied] = useState(false);
  const [isGlobalType, setIsGlobalType] = useState(defaultGlobal);

  const [tenantFilter, setTenantFilter] = useState('');

  const { data: tenantsData } = useListTenants({ page: 0, size: 100, sort: ['tenantName,asc'] });
  const allTenants = (tenantsData as PagedModelTenantResponseDto | undefined)?.content ?? [];
  const eligibleTenants = useMemo(
    () => allTenants.filter((t) => !t.isSystemTenant),
    [allTenants],
  );

  const adminSchema = useMemo(
    () => {
      const base = z.object({
        username: z.string().min(3, t('validation.usernameMin')).max(50, t('validation.usernameMax')),
        email: z.string().email(t('validation.invalidEmail')).optional().or(z.literal('')),
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

  const defaultFormValues: AdminFormValues = { username: '', email: '', firstName: '', lastName: '', tenantId: '' };
  const { register, handleSubmit, reset, watch, setValue, getValues, formState: { errors } } = useForm<AdminFormValues>({
    resolver: zodResolver(adminSchema),
    defaultValues: defaultFormValues,
  });

  // Fresh state each time the dialog opens
  useEffect(() => {
    if (open) {
      reset(defaultFormValues);
      setTenantFilter('');
      setCreatedAdmin(null);
      setRecoveryCodesCopied(false);
      setIsGlobalType(defaultGlobal);
    }
  }, [open, defaultGlobal, reset]);

  const watchedTenantId = watch('tenantId');
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
    const codes = r.recoveryCodes;
    const recoveryCodes =
      Array.isArray(codes) && codes.every((c) => typeof c === 'string')
        ? (codes as string[])
        : undefined;
    return {
      username: typeof r.username === 'string' ? r.username : undefined,
      recoveryCodes,
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

  const handleCopyAllRecoveryCodes = async () => {
    const codes = createdAdmin?.recoveryCodes;
    if (codes == null || codes.length === 0) return;
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      setRecoveryCodesCopied(true);
      setTimeout(() => setRecoveryCodesCopied(false), 2000);
    } catch {
      /* ignore clipboard API errors */
    }
  };

  const handleClose = () => {
    reset();
    setCreatedAdmin(null);
    setRecoveryCodesCopied(false);
    setIsGlobalType(defaultGlobal);
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
        firstName: values.firstName || undefined,
        lastName: values.lastName || undefined,
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
          {createdAdmin.recoveryCodes != null && createdAdmin.recoveryCodes.length > 0 && (
            <div className="space-y-2 border-2 border-accent/40 bg-bg p-3">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
                {t('create.recoveryCodesTitle')}
              </p>
              <p className="text-xs text-fg-muted">{t('create.recoveryCodesHint')}</p>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => void handleCopyAllRecoveryCodes()}
                className="gap-1.5"
              >
                {recoveryCodesCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
                {recoveryCodesCopied ? t('onboarding.copied') : t('create.copyAllRecoveryCodes')}
              </Button>
              <ul className="font-mono text-xs space-y-1 break-all max-h-48 overflow-y-auto border-2 border-fg/20 p-2 bg-surface">
                {createdAdmin.recoveryCodes.map((code) => (
                  <li key={code}>{code}</li>
                ))}
              </ul>
            </div>
          )}
          <p className="text-sm text-fg-muted">
            {t('create.successHint')}
          </p>
          <div className="flex justify-end pt-2">
            <Button onClick={handleClose}>{t('create.done')}</Button>
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

          {createMutation.isError && (
            <Alert variant="error">
              {getApiErrorMessage(createMutation.error, t('create.errorCreate'))}
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

  useEffect(() => {
    if (selectedIndex !== null && (selectedIndex >= data.length || data.length === 0)) {
      setSelectedIndex(null);
    }
  }, [selectedIndex, data.length]);

  const columns: ColumnDef<AdminResponseDto>[] = [
    { header: t('list.columns.id'), key: 'adminId', className: 'w-14', sortKey: 'adminId', render: (r) => <span className="font-mono text-xs">{r.adminId}</span> },
    { header: t('list.columns.username'), key: 'username', sortKey: 'username', render: (r) => <span className="font-medium">{r.username}</span> },
    {
      header: t('list.columns.name'),
      key: 'name',
      render: (r) => (
        <span className="text-fg-muted text-xs">
          {[r.firstName, r.lastName].filter(Boolean).join(' ') || '—'}
        </span>
      ),
    },
    { header: t('list.columns.email'), key: 'email', render: (r) => <span className="text-xs text-fg-muted">{r.email ?? '—'}</span> },
    { header: t('list.columns.type'), key: 'adminType', sortKey: 'adminType', render: (r) => <AdminTypeBadge type={r.adminType} /> },
    { header: t('list.columns.active'), key: 'active', sortKey: 'active', render: (r) => <Badge variant={r.active ? 'success' : 'muted'}>{r.active ? t('list.activeYes') : t('list.activeNo')}</Badge> },
    { header: t('list.columns.created'), key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.createdAt ?? '')}</span> },
    {
      header: t('list.columns.actions'),
      key: 'actions',
      render: (r) => (
        <div className="flex items-center gap-1.5">
          <Button
            variant="secondary"
            size="sm"
            className="gap-1"
            onClick={(e) => { e.stopPropagation(); setOnboardingTarget({ id: r.adminId!, username: r.username! }); }}
          >
            <KeyRound className="size-3" />
            {t('list.credentials')}
          </Button>
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
    },
  ];

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
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5">
            <Plus className="size-3.5" />
            {t('list.newAdmin')}
          </Button>
        </div>

        <div>
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => {
              const idx = data.findIndex((r) => r.adminId === row.adminId);
              setSelectedIndex(idx >= 0 ? idx : null);
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
        admin={selectedAdmin}
        onClose={() => setSelectedIndex(null)}
        onShowCredentials={(id, username) => {
          setSelectedIndex(null);
          setOnboardingTarget({ id, username });
        }}
        onRequestDeactivate={(a) => {
          setSelectedIndex(null);
          setDeactivateTarget(a);
        }}
        onPrev={goPrevAdmin}
        onNext={goNextAdmin}
        hasPrev={hasPrev}
        hasNext={hasNext}
        showNav={showRowNav}
        showEndOfPageHint={showEndOfPageHint}
      />
      <CreateAdminDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        defaultGlobal={isGlobalAdmin}
      />
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
