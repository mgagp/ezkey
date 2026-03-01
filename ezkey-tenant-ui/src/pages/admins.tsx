import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, Copy, KeyRound, Plus, QrCode, RefreshCw, UserX } from 'lucide-react';
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
import { useAuth } from '@/context/auth-context';
import { usePaginatedQuery } from '@/hooks/use-paginated-query';
import { ApiError, api, fetchBlobUrl } from '@/lib/api-client';
import { formatDate } from '@/lib/utils';
import type { AdminCreateRequest, PageResponse } from '@/types/api';
import type { Admin, AdminOnboarding, AdminProvisioning } from '@/types/models';

// ── Admin type badge ───────────────────────────────────────────────────────────

function AdminTypeBadge({ type }: { type: Admin['adminType'] }) {
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

  const { data: onboarding, isLoading, isError } = useQuery({
    queryKey: ['admin-onboarding', adminId],
    queryFn: () => api.get<AdminOnboarding>(`/api/v1/admins/${adminId}/onboarding`),
    enabled: open && adminId !== null,
  });

  const handleCopy = async () => {
    if (!onboarding?.enrollmentProofToken) return;
    try {
      await navigator.clipboard.writeText(onboarding.enrollmentProofToken);
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
              {onboarding.enrollmentProofToken}
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
              {onboarding.enrollmentChallenge}
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
  admin: Admin | null;
}) {
  const queryClient = useQueryClient();

  const deactivateMutation = useMutation({
    mutationFn: (adminId: number) => api.post<void>(`/api/v1/admins/${adminId}/deactivate`, {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admins'] });
      onClose();
    },
  });

  const handleClose = () => {
    deactivateMutation.reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} title="Deactivate Admin" size="sm">
      {admin && (
        <div className="space-y-4">
          <p className="text-sm text-fg">
            Are you sure you want to deactivate{' '}
            <strong>{admin.username}</strong>? All active tokens will be revoked immediately.
          </p>
          <p className="text-xs text-fg-muted">This action cannot be undone from this interface.</p>

          {deactivateMutation.isError && (
            <Alert variant="error">
              {deactivateMutation.error instanceof ApiError
                ? deactivateMutation.error.message
                : 'Failed to deactivate admin.'}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              isLoading={deactivateMutation.isPending}
              onClick={() => admin && deactivateMutation.mutate(admin.adminId)}
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

// ── Create admin dialog ────────────────────────────────────────────────────────

const adminSchema = z.object({
  username: z.string().min(3, 'Min 3 characters').max(50, 'Max 50 characters'),
  email: z.string().email('Invalid email').optional().or(z.literal('')),
  firstName: z.string().max(100).optional().or(z.literal('')),
  lastName: z.string().max(100).optional().or(z.literal('')),
});
type AdminFormValues = z.infer<typeof adminSchema>;

function CreateAdminDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [createdAdmin, setCreatedAdmin] = useState<AdminProvisioning | null>(null);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<AdminFormValues>({
    resolver: zodResolver(adminSchema),
  });

  const createMutation = useMutation({
    mutationFn: (data: AdminCreateRequest) =>
      api.post<AdminProvisioning>('/api/v1/admins/tenant', data),
    onSuccess: (admin) => {
      setCreatedAdmin(admin);
      void queryClient.invalidateQueries({ queryKey: ['admins'] });
    },
  });

  const handleClose = () => {
    reset();
    setCreatedAdmin(null);
    createMutation.reset();
    onClose();
  };

  const onSubmit = (values: AdminFormValues) => {
    createMutation.mutate({
      username: values.username.trim(),
      email: values.email || undefined,
      firstName: values.firstName || undefined,
      lastName: values.lastName || undefined,
    });
  };

  return (
    <Dialog open={open} onClose={handleClose} title="New Tenant Admin" size="md">
      {createdAdmin ? (
        <div className="space-y-4">
          <Alert variant="success">
            Admin <strong>{createdAdmin.username}</strong> created successfully.
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
          <div className="space-y-1">
            <Label htmlFor="adm-username">Username *</Label>
            <Input id="adm-username" placeholder="jsmith" error={errors.username?.message} {...register('username')} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label htmlFor="adm-fname">First Name</Label>
              <Input id="adm-fname" placeholder="John" error={errors.firstName?.message} {...register('firstName')} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="adm-lname">Last Name</Label>
              <Input id="adm-lname" placeholder="Smith" error={errors.lastName?.message} {...register('lastName')} />
            </div>
          </div>
          <div className="space-y-1">
            <Label htmlFor="adm-email">Email <span className="text-fg-muted font-normal">(optional)</span></Label>
            <Input id="adm-email" type="email" placeholder="jsmith@example.com" error={errors.email?.message} {...register('email')} />
          </div>

          {createMutation.isError && (
            <Alert variant="error">
              {createMutation.error instanceof ApiError ? createMutation.error.message : 'Failed to create admin.'}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>Cancel</Button>
            <Button type="submit" isLoading={createMutation.isPending}>Create Admin</Button>
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
  const [onboardingTarget, setOnboardingTarget] = useState<{ id: number; username: string } | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<Admin | null>(null);

  const { data, pagination, isLoading, refetch } = usePaginatedQuery<Admin>({
    queryKey: ['admins'],
    queryFn: ({ page, size, sort }) =>
      api.get<PageResponse<Admin>>(`/api/v1/admins?page=${page}&size=${size}&sort=${sort}`),
  });

  const columns: ColumnDef<Admin>[] = [
    { header: 'ID', key: 'adminId', className: 'w-14', render: (r) => <span className="font-mono text-xs">{r.adminId}</span> },
    { header: 'Username', key: 'username', render: (r) => <span className="font-medium">{r.username}</span> },
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
    { header: 'Type', key: 'adminType', render: (r) => <AdminTypeBadge type={r.adminType} /> },
    { header: 'Active', key: 'active', render: (r) => <Badge variant={r.active ? 'success' : 'muted'}>{r.active ? 'Yes' : 'No'}</Badge> },
    { header: 'Created', key: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatDate(r.createdAt)}</span> },
    {
      header: 'Actions',
      key: 'actions',
      render: (r) => (
        <div className="flex items-center gap-1.5">
          <Button
            variant="secondary"
            size="sm"
            className="gap-1"
            onClick={(e) => { e.stopPropagation(); setOnboardingTarget({ id: r.adminId, username: r.username }); }}
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
            keyExtractor={(r) => r.adminId}
            emptyMessage="No admins found."
          />
          <Pagination
            page={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            isFirst={pagination.isFirst}
            isLast={pagination.isLast}
            onPrevPage={pagination.prevPage}
            onNextPage={pagination.nextPage}
          />
        </div>
      </div>

      <CreateAdminDialog open={createOpen} onClose={() => setCreateOpen(false)} />
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
