import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { Check, Copy, KeyRound, Plus, Power, PowerOff, QrCode, RefreshCw, UserX } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
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
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { fetchBlobUrl, getApiErrorMessage } from '@/lib/api-client';
import { adminDemoPresets, isDemoMode } from '@/lib/demo-mode';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import {
  listAdmins,
  useActivateAdmin,
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

/** UI-facing shape for create admin response (API returns CreateGlobalAdmin201). */
interface AdminProvisioningShape {
  username?: string;
}

// ── Admin type badge ───────────────────────────────────────────────────────────

function AdminTypeBadge({ type }: { type: AdminResponseDto['adminType'] }) {
  if (type === 'GLOBAL_ADMIN') return <Badge variant="warning">Global</Badge>;
  if (type === 'INTEGRATION_ADMIN') return <Badge variant="muted">Integration</Badge>;
  return <Badge variant="muted">Tenant</Badge>;
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
    <Dialog open={open} onClose={handleClose} title={`Onboarding — ${adminUsername}`} size="md">
      {isLoading && (
        <div className="flex justify-center py-8">
          <span className="size-5 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
        </div>
      )}
      {isError && <Alert variant="error">Could not load onboarding credentials.</Alert>}
      {onboarding && (
        <div className="space-y-4">
          <Alert variant="info">
            Share these credentials with the admin to set up their EZKey mobile app.
          </Alert>

          {/* Proof Token */}
          <div className="space-y-1.5">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
              Enrollment Proof Token
            </p>
            <div className="border-2 border-fg p-3 font-mono text-xs break-all bg-bg leading-relaxed">
              {String(onboarding.enrollmentProofToken ?? '')}
            </div>
            <Button variant="secondary" size="sm" onClick={handleCopy} className="gap-1.5">
              {tokenCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
              {tokenCopied ? 'Copied!' : 'Copy Token'}
            </Button>
          </div>

          {/* Challenge */}
          <div className="border-2 border-fg/30 p-3 bg-bg">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
              Binding Challenge Code
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
              {qrCodeUrl ? 'Hide QR Code' : 'Show QR Code'}
            </Button>
            {qrCodeUrl && (
              <div className="border-2 border-fg p-3 inline-block">
                <img src={qrCodeUrl} alt="Admin onboarding QR Code" className="size-48" />
              </div>
            )}
          </div>

          <div className="flex justify-end pt-2">
            <Button onClick={handleClose}>Close</Button>
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
    <Dialog open={open} onClose={handleClose} title="Deactivate Admin" size="sm">
      {admin && (
        <div className="space-y-4">
          <p className="text-sm text-fg">
            Are you sure you want to deactivate{' '}
            <strong>{admin.username}</strong>? All active tokens will be revoked immediately.
          </p>
          <p className="text-xs text-fg-muted">This action cannot be undone from this interface.</p>

          <div className="space-y-1.5">
            <Label htmlFor="deactivate-admin-reason">
              Reason <span className="text-fg-muted font-normal">(min 10 chars, for audit trail)</span>
            </Label>
            <Input
              id="deactivate-admin-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Justification for this action..."
            />
          </div>

          {deactivateMutation.isError && (
            <Alert variant="error">
              {getApiErrorMessage(deactivateMutation.error, 'Failed to deactivate admin.')}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>
              Cancel
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
              Deactivate
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
}: {
  admin: AdminResponseDto | null;
  onClose: () => void;
  onShowCredentials: (id: number, username: string) => void;
  onRequestDeactivate?: (admin: AdminResponseDto) => void;
}) {
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

  const activateMutation = useActivateAdmin({
    mutation: {
      onSuccess: () => {
        toast(`Admin "${adm!.username}" activated.`, 'success');
        void queryClient.invalidateQueries({ queryKey: ['admins'] });
        void queryClient.invalidateQueries({ queryKey: ['admin-detail', adm!.adminId] });
      },
      onError: (e) => toast(getApiErrorMessage(e, 'Activation failed'), 'error'),
    },
  });

  if (!adm) return null;

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
    <Dialog open={admin !== null} onClose={onClose} title={`Admin — ${adm.username}`} size="lg">
      <div className="space-y-5">
        {/* Info section */}
        <dl className="space-y-2.5">
          <InfoRow label="Admin ID"><span className="font-mono">{adm.adminId}</span></InfoRow>
          <InfoRow label="Username"><span className="font-medium">{adm.username}</span></InfoRow>
          <InfoRow label="Name">{fullName || <span className="text-fg-muted">—</span>}</InfoRow>
          <InfoRow label="Email">{adm.email || <span className="text-fg-muted">—</span>}</InfoRow>
          <InfoRow label="Type"><AdminTypeBadge type={adm.adminType} /></InfoRow>
          {adm.tenantId != null && (
            <InfoRow label="Tenant ID"><span className="font-mono">{adm.tenantId}</span></InfoRow>
          )}
          <InfoRow label="Status"><Badge variant={adm.active ? 'success' : 'muted'}>{adm.active ? 'Active' : 'Inactive'}</Badge></InfoRow>
          <InfoRow label="Created">{adm.createdAt ? formatDate(adm.createdAt) : '—'}</InfoRow>
          <InfoRow label="Last Login">
            {adm.lastLoginAt ? (
              <span>
                {formatDate(adm.lastLoginAt)}
                <span className="text-fg-muted text-xs ml-1.5">({formatRelativeTime(adm.lastLoginAt)})</span>
              </span>
            ) : (
              <span className="text-fg-muted italic">Never</span>
            )}
          </InfoRow>
        </dl>

        {/* Actions */}
        <div className="border-t-2 border-fg/10 pt-4 space-y-3">
          <h3 className="font-bold text-xs uppercase tracking-wider text-fg-muted">Actions</h3>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              size="sm"
              className="gap-1.5"
              onClick={() => onShowCredentials(adm.adminId!, adm.username!)}
            >
              <KeyRound className="size-3.5" />
              Credentials
            </Button>

            {isGlobalAdmin && adm.active && onRequestDeactivate && (
              <Button
                variant="destructive"
                size="sm"
                className="gap-1.5"
                onClick={() => onRequestDeactivate(adm)}
              >
                <PowerOff className="size-3.5" />
                Deactivate
              </Button>
            )}

            {isGlobalAdmin && !adm.active && (
              <Button
                variant="secondary"
                size="sm"
                className="gap-1.5 text-success border-success/30 hover:bg-success/10"
                isLoading={activateMutation.isPending}
                onClick={() => activateMutation.mutate({ id: adm!.adminId! })}
              >
                <Power className="size-3.5" />
                Activate
              </Button>
            )}
          </div>
        </div>

        <div className="flex justify-end pt-2">
          <Button onClick={onClose}>Close</Button>
        </div>
      </div>
    </Dialog>
  );
}

// ── Create admin dialog ────────────────────────────────────────────────────────

const adminSchemaBase = z.object({
  username: z.string().min(3, 'Min 3 characters').max(50, 'Max 50 characters'),
  email: z.string().email('Invalid email').optional().or(z.literal('')),
  firstName: z.string().max(100).optional().or(z.literal('')),
  lastName: z.string().max(100).optional().or(z.literal('')),
  tenantId: z.union([z.number(), z.string()]).optional().or(z.literal('')),
});

function CreateAdminDialog({ open, onClose, defaultGlobal = false }: { open: boolean; onClose: () => void; defaultGlobal?: boolean }) {
  const { session } = useAuth();
  const callerIsGlobal = session?.adminType === 'GLOBAL_ADMIN';
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { sessionDemoOn } = useDemoModeSession();
  const [createdAdmin, setCreatedAdmin] = useState<AdminProvisioningShape | null>(null);
  const [isGlobalType, setIsGlobalType] = useState(defaultGlobal);

  const [tenantFilter, setTenantFilter] = useState('');

  const { data: tenantsData } = useListTenants({ page: 0, size: 100, sort: ['tenantName,asc'] });
  const allTenants = (tenantsData as PagedModelTenantResponseDto | undefined)?.content ?? [];
  const eligibleTenants = useMemo(
    () => allTenants.filter((t) => !t.isSystemTenant),
    [allTenants],
  );

  const adminSchema = useMemo(
    () =>
      adminSchemaBase.refine(
        (data) => !(callerIsGlobal && !isGlobalType) || (data.tenantId != null && data.tenantId !== ''),
        { message: 'Select a tenant', path: ['tenantId'] },
      ),
    [callerIsGlobal, isGlobalType],
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
    toast(`${isGlobal ? 'Global' : 'Tenant'} Admin "${admin.username}" created.`);
  };

  const createGlobalMutation = useCreateGlobalAdmin({
    mutation: {
      onSuccess: (data) => {
        onCreated(data as unknown as AdminProvisioningShape, true);
      },
    },
  });

  const createTenantMutation = useCreateTenantAdmin({
    mutation: {
      onSuccess: (data) => {
        onCreated(data as unknown as AdminProvisioningShape, false);
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

  const handleClose = () => {
    reset();
    setCreatedAdmin(null);
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
    <Dialog open={open} onClose={handleClose} title={isGlobalType ? 'New Global Admin' : 'New Tenant Admin'} size="md" dismissible={false}>
      {createdAdmin ? (
        <div className="space-y-4">
          <Alert variant="success">
            Admin <strong>{String(createdAdmin.username ?? '')}</strong> created successfully.
          </Alert>
          <p className="text-sm text-fg-muted">
            Use the <strong>Credentials</strong> button in the list to retrieve the onboarding token and QR code to share with the new admin.
          </p>
          <div className="flex justify-end pt-2">
            <Button onClick={handleClose}>Done</Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {isDemoMode && sessionDemoOn && (
            <div className="flex flex-wrap items-center gap-2 p-2 border-2 border-accent/30 bg-accent/5">
              <span className="text-xs font-bold text-fg-muted uppercase tracking-wider">Fill demo:</span>
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
                <span className="text-sm font-medium">Tenant Admin</span>
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
                <span className="text-sm font-medium">Global Admin</span>
              </label>
            </div>
          )}
          {tenantAdminNeedsTenant && (
            <>
              {eligibleTenants.length === 0 ? (
                <Alert variant="warning">
                  No tenant available. <Link to="/tenants" className="font-medium text-accent underline">Create a tenant first</Link>.
                </Alert>
              ) : (
                <div className="space-y-2">
                  <div className="space-y-1">
                    <Label htmlFor="adm-tenant-filter">Filter tenants</Label>
                    <Input
                      id="adm-tenant-filter"
                      type="text"
                      placeholder="By name, organization or domain..."
                      value={tenantFilter}
                      onChange={(e) => setTenantFilter(e.target.value)}
                      className="text-sm"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="adm-tenant">Assign to tenant *</Label>
                    <Select id="adm-tenant" error={errors.tenantId?.message} {...register('tenantId')}>
                      <option value="">Select a tenant</option>
                      {filteredTenants.map((t) => (
                        <option key={t.tenantId} value={t.tenantId}>
                          {t.tenantName ?? ''}
                        </option>
                      ))}
                      {filteredTenants.length === 0 && tenantFilter.trim() !== '' && (
                        <option value="" disabled>No tenant matches your filter</option>
                      )}
                    </Select>
                  </div>
                </div>
              )}
            </>
          )}
          <div className="space-y-1">
            <Label htmlFor="adm-username">Username *</Label>
            <Input id="adm-username" placeholder="marie.dupont" error={errors.username?.message} {...register('username')} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label htmlFor="adm-fname">First Name</Label>
              <Input id="adm-fname" placeholder="Marie" error={errors.firstName?.message} {...register('firstName')} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="adm-lname">Last Name</Label>
              <Input id="adm-lname" placeholder="Dupont" error={errors.lastName?.message} {...register('lastName')} />
            </div>
          </div>
          <div className="space-y-1">
            <Label htmlFor="adm-email">Email <span className="text-fg-muted font-normal">(optional)</span></Label>
            <Input id="adm-email" type="email" placeholder="marie@garageducoin.com" error={errors.email?.message} {...register('email')} />
          </div>

          {createMutation.isError && (
            <Alert variant="error">
              {getApiErrorMessage(createMutation.error, 'Failed to create admin.')}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>Cancel</Button>
            <Button
              type="submit"
              isLoading={createMutation.isPending}
              disabled={!canSubmitTenantAdmin}
            >
              Create Admin
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AdminsPage() {
  const { session } = useAuth();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';

  const [createOpen, setCreateOpen] = useState(false);
  const [selectedAdmin, setSelectedAdmin] = useState<AdminResponseDto | null>(null);
  const [onboardingTarget, setOnboardingTarget] = useState<{ id: number; username: string } | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<AdminResponseDto | null>(null);

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<AdminResponseDto, Record<string, never>>({
    queryKey: ['admins'],
    baseParams: {},
    fetchPage: (params) => listAdmins(params) as Promise<PagedModelAdminResponseDto>,
  });

  const columns: ColumnDef<AdminResponseDto>[] = [
    { header: 'ID', key: 'adminId', className: 'w-14', sortKey: 'adminId', render: (r) => <span className="font-mono text-xs">{r.adminId}</span> },
    { header: 'Username', key: 'username', sortKey: 'username', render: (r) => <span className="font-medium">{r.username}</span> },
    {
      header: 'Name',
      key: 'name',
      render: (r) => (
        <span className="text-fg-muted text-xs">
          {[r.firstName, r.lastName].filter(Boolean).join(' ') || '—'}
        </span>
      ),
    },
    { header: 'Email', key: 'email', render: (r) => <span className="text-xs text-fg-muted">{r.email ?? '—'}</span> },
    { header: 'Type', key: 'adminType', sortKey: 'adminType', render: (r) => <AdminTypeBadge type={r.adminType} /> },
    { header: 'Active', key: 'active', sortKey: 'active', render: (r) => <Badge variant={r.active ? 'success' : 'muted'}>{r.active ? 'Yes' : 'No'}</Badge> },
    { header: 'Created', key: 'createdAt', sortKey: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.createdAt ?? '')}</span> },
    {
      header: 'Actions',
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
            Credentials
          </Button>
          {isGlobalAdmin && r.active && (
            <Button
              variant="ghost"
              size="sm"
              className="gap-1 text-error hover:bg-error/10 border border-error/30 hover:border-error"
              onClick={(e) => { e.stopPropagation(); setDeactivateTarget(r); }}
            >
              <UserX className="size-3" />
              Deactivate
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <AppShell title="Admins">
      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
              <RefreshCw className="size-3.5" />
              Refresh
            </Button>
          </div>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5">
            <Plus className="size-3.5" />
            New Admin
          </Button>
        </div>

        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedAdmin(row)}
            keyExtractor={(r, i) => r.adminId ?? i}
            emptyMessage="No admins found."
            currentSort={pagination.sort}
            onSort={pagination.setSort}
          />
          <Pagination
            page={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            isFirst={pagination.isFirst}
            isLast={pagination.isLast}
            onPrevPage={pagination.prevPage}
            onNextPage={pagination.nextPage}
            pageSize={pagination.size}
            onPageSizeChange={pagination.setPageSize}
          />
        </div>
      </div>

      <AdminDetailDialog
        admin={selectedAdmin}
        onClose={() => setSelectedAdmin(null)}
        onShowCredentials={(id, username) => {
          setSelectedAdmin(null);
          setOnboardingTarget({ id, username });
        }}
        onRequestDeactivate={(a) => {
          setSelectedAdmin(null);
          setDeactivateTarget(a);
        }}
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
