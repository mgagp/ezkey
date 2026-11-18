import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Check, Copy, Plus, RefreshCw, UserX } from 'lucide-react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { AdminStatusBadge, AdminTypeBadge } from '@/components/feature/admin-badges';
import { DeactivateAdminDialog } from '@/components/feature/admin-deactivate-dialog';
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
import { Tooltip } from '@/components/ui/tooltip';
import { useAuth } from '@/context/use-auth';
import { useDemoModeSession } from '@/context/use-demo-mode-session';
import { useToast } from '@/context/use-toast';
import { asOrvalPagedBody, usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { adminDemoPresets, isDemoMode } from '@/lib/demo-mode';
import { isPhoneNumberInputValid, normalizePhoneNumberInput } from '@/lib/phone-number';
import { isPendingActivationAdmin, shouldShowTenantOperationalWarning } from '@/lib/admin-record-rules';
import { adminDetailHref, buildListDetailNavState } from '@/lib/list-detail-navigation';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import {
  listAdmins,
  useCreateGlobalAdmin,
  useCreateTenantAdmin,
} from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import { useListTenants } from '@/generated/admin-api/tenants/tenants';
import { AdminCreateRequestDtoOnboardingMode } from '@/generated/admin-api/model';
import type {
  AdminCreateRequestDto,
  AdminResponseDto,
  TenantResponseDto,
} from '@/generated/admin-api/model';

/** UI-facing shape for create admin response (API returns provisioning DTO with optional recovery codes). */
interface AdminProvisioningShape {
  adminId?: number;
  username?: string;
  onboardingMode?: AdminCreateRequestDto['onboardingMode'];
  lifecycleStatus?: AdminResponseDto['lifecycleStatus'];
  activationCode?: string;
  activationCodeExpiresAt?: string;
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

// ── Create admin dialog ────────────────────────────────────────────────────────

function CreateAdminDialog({
  open,
  onClose,
  onLandOnRecord,
  defaultGlobal = false,
  defaultTenantId,
}: {
  open: boolean;
  onClose: () => void;
  /** #1313: a creation ends on the administrator record (design-principles §10). */
  onLandOnRecord: (adminId: number) => void;
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
    () => asOrvalPagedBody<TenantResponseDto>(tenantsData)?.content ?? [],
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

  const resetDialog = () => {
    reset();
    setCreatedAdmin(null);
    setProvisioningMaterialCopied(false);
    setProvisioningMaterialSavedConfirmed(false);
    setIsGlobalType(defaultTenantId != null && defaultTenantId > 0 ? false : defaultGlobal);
    setOnboardingMode(AdminCreateRequestDtoOnboardingMode.IMMEDIATE);
    setTenantFilter('');
    createMutation.reset();
  };

  const onCreated = (admin: AdminProvisioningShape, isGlobal: boolean) => {
    void queryClient.invalidateQueries({ queryKey: ['admins'] });
    const hasActivationCode = admin.activationCode != null && admin.activationCode.length > 0;
    if (!hasActivationCode && admin.adminId != null) {
      // IMMEDIATE: no intermediate screen; the record carries the bind card (#1313). The toast is
      // the only confirmation, since the dialog closes.
      const typeLabel = isGlobal ? t('adminType.global') : t('adminType.tenant');
      toast(t('create.toastCreated', { type: typeLabel, username: admin.username }));
      resetDialog();
      onLandOnRecord(admin.adminId);
      return;
    }
    // ACTIVATION_CODE: the code step's success alert confirms the creation (no toast on top), the
    // one-time code is shown once, then Done lands on the record.
    setCreatedAdmin(admin);
  };

  const mapProvisioningResponse = (data: unknown): AdminProvisioningShape => {
    const r = data as Record<string, unknown>;
    return {
      adminId: typeof r.adminId === 'number' ? r.adminId : undefined,
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
    const landOn = createdAdmin?.adminId;
    resetDialog();
    if (landOn != null) {
      onLandOnRecord(landOn);
      return;
    }
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
          <p className="text-sm text-fg-muted">{t('create.activationCodeSuccessHint')}</p>
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
  const navigate = useNavigate();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const [searchParams, setSearchParams] = useSearchParams();

  const defaultTenantIdFromUrl = useMemo(() => {
    const flag = searchParams.get('createTenantAdmin');
    if (flag !== '1' && flag !== 'true') return null;
    const n = Number(searchParams.get('tenantId'));
    return Number.isFinite(n) && n > 0 ? n : null;
  }, [searchParams]);

  /** #1399: the record moved to /admins/:id; former ?adminId=N links (bookmarks, history) redirect. */
  const legacyAdminId = useMemo(() => {
    const raw = searchParams.get('adminId');
    if (raw == null || raw === '') return null;
    const n = Number(raw);
    return Number.isInteger(n) && n > 0 ? n : null;
  }, [searchParams]);

  const [createOpen, setCreateOpen] = useState(false);
  const [deactivateTarget, setDeactivateTarget] = useState<AdminResponseDto | null>(null);

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<AdminResponseDto, Record<string, never>>({
    queryKey: ['admins'],
    baseParams: {},
    fetchPage: (params) => listAdmins(params),
  });

  const handleCloseCreateDialog = () => {
    setCreateOpen(false);
    if (searchParams.has('createTenantAdmin') || searchParams.has('tenantId')) {
      const next = new URLSearchParams(searchParams);
      next.delete('createTenantAdmin');
      next.delete('tenantId');
      setSearchParams(next, { replace: true });
    }
  };

  /**
   * #1313: after a creation, open the new administrator's record. A creation opened from another
   * screen (?createTenantAdmin=1) replaces that URL, so Back does not reopen the create dialog.
   */
  const handleLandOnRecord = (adminId: number) => {
    setCreateOpen(false);
    navigate(adminDetailHref(adminId), { replace: defaultTenantIdFromUrl != null });
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
          {<AdminStatusBadge admin={r} scope="list" />}
        </Tooltip>
      ) : shouldShowTenantOperationalWarning(r) ? (
        <Tooltip content={t('list.operationalWarningTooltip')}>
          <span className="inline-flex items-center gap-1 border border-warning/40 rounded-sm px-1.5">
            <Badge variant="success">{t('list.activeYes')}</Badge>
            <AlertTriangle className="size-4 text-warning" aria-hidden />
          </span>
        </Tooltip>
      ) : (
        <AdminStatusBadge admin={r} scope="list" />
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
  }, [isGlobalAdmin, t]);

  if (legacyAdminId != null) {
    return <Navigate to={adminDetailHref(legacyAdminId)} replace />;
  }

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
          <Button
            size="sm"
            onClick={() => setCreateOpen(true)}
            className="gap-1.5"
            data-testid="admins-new-admin"
          >
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
              if (row.adminId == null) return;
              const st = buildListDetailNavState(data, (r) => r.adminId ?? 0, row, !pagination.isLast);
              navigate(adminDetailHref(row.adminId), { state: st ?? undefined });
            }}
            keyExtractor={(r, i) => r.adminId ?? i}
            emptyMessage={t('list.emptyMessage')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>
      </div>

      {(createOpen || defaultTenantIdFromUrl != null) && (
        <CreateAdminDialog
          open
          onClose={handleCloseCreateDialog}
          onLandOnRecord={handleLandOnRecord}
          defaultGlobal={defaultTenantIdFromUrl != null ? false : isGlobalAdmin}
          defaultTenantId={defaultTenantIdFromUrl}
        />
      )}
      <DeactivateAdminDialog
        open={deactivateTarget !== null}
        onClose={() => setDeactivateTarget(null)}
        admin={deactivateTarget}
      />
    </AppShell>
  );
}
