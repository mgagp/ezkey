import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, QrCode, RefreshCw, Search } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { useDebounce } from '@/hooks/use-debounce';
import { useIntegrations } from '@/hooks/use-integrations';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { ApiError, fetchBlobUrl } from '@/lib/api-client';
import { enrollmentDemoPresets, isDemoMode } from '@/lib/demo-mode';
import { formatDate } from '@/lib/utils';
import { create1, search1 } from '@/generated/admin-api/enrollments/enrollments';
import type {
  EnrollmentCreateRequestDto,
  EnrollmentCreateResponseDto,
  EnrollmentResponseDto,
  PagedModelEnrollmentResponseDto,
  Search1Params,
} from '@/generated/admin-api/model';

// ── Create form schema ────────────────────────────────────────────────────────

const createSchema = z.object({
  integrationId: z.string().min(1, 'Select an integration'),
  name: z.string().min(1, 'Name is required').max(100, 'Max 100 characters'),
  authAttemptChallengeRequired: z.boolean(),
  contactEmail: z.string().email('Invalid email address').optional().or(z.literal('')),
  userIdentifier: z.string().max(100).optional().or(z.literal('')),
});
type CreateFormValues = z.infer<typeof createSchema>;

// ── Create dialog ─────────────────────────────────────────────────────────────

function EnrollmentCreateDialog({
  open,
  initialIntegrationId,
  onClose,
}: {
  open: boolean;
  initialIntegrationId: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { sessionDemoOn } = useDemoModeSession();
  const { list: integrations, isLoading: loadingIntegrations } = useIntegrations();
  const [createdEnrollment, setCreatedEnrollment] = useState<EnrollmentCreateResponseDto | null>(null);
  const [createdEnrollmentName, setCreatedEnrollmentName] = useState<string | null>(null);
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
  const [qrLoading, setQrLoading] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    getValues,
    formState: { errors },
  } = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      authAttemptChallengeRequired: false,
      integrationId: initialIntegrationId ?? '',
    },
  });

  const createMutation = useMutation({
    mutationFn: async (data: EnrollmentCreateRequestDto) => {
      const res = await create1(data);
      return res as unknown as EnrollmentCreateResponseDto;
    },
    onSuccess: (enrollment, variables) => {
      setCreatedEnrollment(enrollment);
      setCreatedEnrollmentName(variables?.name ?? null);
      void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
      void queryClient.invalidateQueries({ queryKey: ['stats'] });
    },
  });

  const handleClose = () => {
    if (qrCodeUrl) URL.revokeObjectURL(qrCodeUrl);
    reset();
    setCreatedEnrollment(null);
    setCreatedEnrollmentName(null);
    setQrCodeUrl(null);
    setQrLoading(false);
    createMutation.reset();
    onClose();
  };

  const onSubmit = (values: CreateFormValues) => {
    createMutation.mutate({
      integrationId: parseInt(values.integrationId, 10),
      name: values.name,
      authAttemptChallengeRequired: values.authAttemptChallengeRequired,
      contactEmail: values.contactEmail || undefined,
      userIdentifier: values.userIdentifier || undefined,
    });
  };

  // Revoke blob URL on unmount or when QR is hidden
  useEffect(() => {
    return () => {
      if (qrCodeUrl) URL.revokeObjectURL(qrCodeUrl);
    };
  }, [qrCodeUrl]);

  const handleToggleQr = async () => {
    if (!createdEnrollment) return;
    if (qrCodeUrl) {
      URL.revokeObjectURL(qrCodeUrl);
      setQrCodeUrl(null);
      return;
    }
    setQrLoading(true);
    try {
      const url = await fetchBlobUrl(`/api/v1/enrollments/${createdEnrollment.enrollmentId}/qrcode`);
      setQrCodeUrl(url);
    } catch {
      // QR load failed — silently ignore, user can retry
    } finally {
      setQrLoading(false);
    }
  };

  const handleViewEnrollment = () => {
    if (createdEnrollment) {
      navigate(`/enrollments/${createdEnrollment.enrollmentId}`);
      handleClose();
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} title="New Enrollment" size="md" dismissible={false}>
      {createdEnrollment ? (
        /* ── Success state: QR on demand + challenge ───────── */
        <div className="space-y-4">
          <Alert variant="success">
            Enrollment <strong>{createdEnrollmentName ?? 'created'}</strong> created successfully.
          </Alert>

          <div className="space-y-3">
            <p className="text-xs font-black uppercase tracking-widest text-fg-muted">
              User can scan the QR code below to set up their device.
            </p>
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
                <img src={qrCodeUrl} alt="Enrollment QR Code" className="size-48" />
              </div>
            )}
          </div>

          {createdEnrollment.enrollmentChallenge != null && (
            <div className="border-2 border-fg/30 p-3 bg-bg">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                Binding Challenge Code
              </p>
              <p className="font-mono text-2xl font-black tracking-widest">
                {createdEnrollment.enrollmentChallenge}
              </p>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={handleViewEnrollment}>
              View enrollment
            </Button>
            <Button onClick={handleClose}>Done</Button>
          </div>
        </div>
      ) : !loadingIntegrations && integrations.length === 0 ? (
        <Alert variant="warning">
          No integration available. <Link to="/integrations" className="font-medium text-accent underline">Create an integration first</Link>.
        </Alert>
      ) : (
        /* ── Form state ──────────────────────────────────── */
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {isDemoMode && sessionDemoOn && (
            <div className="flex flex-wrap items-center gap-2 p-2 border-2 border-accent/30 bg-accent/5">
              <span className="text-xs font-bold text-fg-muted uppercase tracking-wider">Fill demo:</span>
              {enrollmentDemoPresets.map((preset) => (
                <Button
                  key={preset.id}
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() =>
                    reset({
                      ...preset.values,
                      integrationId: getValues('integrationId') || initialIntegrationId || '',
                    })
                  }
                >
                  {preset.label}
                </Button>
              ))}
            </div>
          )}
          {/* Integration */}
          <div className="space-y-1">
            <Label htmlFor="enr-integration">Integration *</Label>
            <Select
              id="enr-integration"
              disabled={loadingIntegrations}
              error={errors.integrationId?.message}
              {...register('integrationId')}
            >
              <option value="">Select an integration...</option>
              {integrations.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.code} — {i.name ?? i.code}
                </option>
              ))}
            </Select>
          </div>

          {/* Name */}
          <div className="space-y-1">
            <Label htmlFor="enr-name">Enrollment Name *</Label>
            <Input
              id="enr-name"
              placeholder="e.g. Marie Dupont — iPhone 15"
              error={errors.name?.message}
              {...register('name')}
            />
          </div>

          {/* Contact email */}
          <div className="space-y-1">
            <Label htmlFor="enr-email">Contact Email <span className="text-fg-muted font-normal">(optional)</span></Label>
            <Input
              id="enr-email"
              type="email"
              placeholder="user@garageducoin.com"
              error={errors.contactEmail?.message}
              {...register('contactEmail')}
            />
          </div>

          {/* User identifier */}
          <div className="space-y-1">
            <Label htmlFor="enr-uid">User Identifier <span className="text-fg-muted font-normal">(optional)</span></Label>
            <Input
              id="enr-uid"
              placeholder="Internal reference (HR ID, username…)"
              error={errors.userIdentifier?.message}
              {...register('userIdentifier')}
            />
          </div>

          {/* Challenge required */}
          <div className="flex items-center gap-2.5">
            <input
              id="enr-challenge"
              type="checkbox"
              className="size-4 border-2 border-fg accent-accent"
              {...register('authAttemptChallengeRequired')}
            />
            <Label htmlFor="enr-challenge" className="cursor-pointer">
              Require challenge code on each authentication
            </Label>
          </div>

          {/* API error */}
          {createMutation.isError && (
            <Alert variant="error">
              {createMutation.error instanceof ApiError
                ? createMutation.error.message
                : 'Failed to create enrollment. Please try again.'}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>Cancel</Button>
            <Button type="submit" isLoading={createMutation.isPending}>Create Enrollment</Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function EnrollmentsPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [nameInput, setNameInput] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [integrationFilter, setIntegrationFilter] = useState(searchParams.get('integrationId') ?? '');
  const [activeFilter, setActiveFilter] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);

  const debouncedName = useDebounce(nameInput, 300);
  const { list: integrations } = useIntegrations();

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<EnrollmentResponseDto, {
    enrollmentName?: string;
    status?: string;
    integrationId?: number;
    active?: boolean;
  }>({
    queryKey: ['enrollments', debouncedName, statusFilter, integrationFilter, activeFilter],
    baseParams: {
      enrollmentName: debouncedName || undefined,
      status: statusFilter || undefined,
      integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
      active: activeFilter === 'all' ? undefined : activeFilter === 'active' ? true : activeFilter === 'inactive' ? false : undefined,
    },
    fetchPage: (params) => search1(params as Search1Params) as Promise<PagedModelEnrollmentResponseDto>,
  });

  const columns: ColumnDef<EnrollmentResponseDto>[] = [
    { header: 'ID', key: 'enrollmentId', className: 'w-14', sortKey: 'enrollmentId', render: (r) => <span className="font-mono text-xs">{r.enrollmentId}</span> },
    { header: 'Name', key: 'enrollmentName', sortKey: 'enrollmentName', render: (r) => <span className="font-medium">{r.enrollmentName}</span> },
    { header: 'Status', key: 'enrollmentStatus', sortKey: 'enrollmentStatus', render: (r) => <EnrollmentStatusBadge status={r.enrollmentStatus} /> },
    { header: 'Active', key: 'enrollmentActive', sortKey: 'enrollmentActive', render: (r) => <Badge variant={r.enrollmentActive ? 'success' : 'muted'}>{r.enrollmentActive ? 'Yes' : 'No'}</Badge> },
    {
      header: 'Integration',
      key: 'integrationId',
      render: (r) => {
        const integration = integrations.find((i) => i.id === r.integrationId);
        return (
          <span className="text-xs text-fg-muted">
            {integration ? `${integration.code}` : `#${r.integrationId}`}
          </span>
        );
      },
    },
    { header: 'Challenge', key: 'authAttemptChallengeRequired', render: (r) => <Badge variant={r.authAttemptChallengeRequired ? 'warning' : 'muted'}>{r.authAttemptChallengeRequired ? 'Yes' : 'No'}</Badge> },
    { header: 'Verified', key: 'verifiedAt', sortKey: 'verifiedAt', render: (r) => <span className="text-xs text-fg-muted">{r.verifiedAt ? formatDate(r.verifiedAt) : '—'}</span> },
  ];

  return (
    <AppShell title="Enrollments">
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="flex-1 min-w-48 relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-fg-muted pointer-events-none" />
            <Input
              placeholder="Search by name..."
              value={nameInput}
              onChange={(e) => setNameInput(e.target.value)}
              className="pl-8"
            />
          </div>

          <div className="w-36">
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="">All Status</option>
              <option value="CREATED">Created</option>
              <option value="BOUND">Bound</option>
              <option value="VERIFIED">Verified</option>
              <option value="INVALID">Invalid</option>
            </Select>
          </div>

          <div className="w-44">
            <Select value={integrationFilter} onChange={(e) => setIntegrationFilter(e.target.value)}>
              <option value="">All Integrations</option>
              {integrations.map((i) => (
                <option key={i.id} value={String(i.id)}>{i.code}</option>
              ))}
            </Select>
          </div>

          <div className="w-32">
            <Select value={activeFilter} onChange={(e) => setActiveFilter(e.target.value)}>
              <option value="">Any Active</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </Select>
          </div>

          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>

          <Button size="sm" onClick={() => setDialogOpen(true)} className="gap-1.5 ml-auto">
            <Plus className="size-3.5" />
            New Enrollment
          </Button>
        </div>

        {/* Table */}
        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => navigate(`/enrollments/${row.enrollmentId}`)}
            keyExtractor={(row, i) => row.enrollmentId ?? i}
            emptyMessage="No enrollments found. Create your first enrollment to get started."
            currentSort={pagination.sort}
            onSort={pagination.setSort}
          />
          <Pagination
            page={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            isFirst={pagination.isFirst}
            isLast={pagination.isLast}
            onFirstPage={pagination.firstPage}
            onLastPage={pagination.lastPage}
            onPrevPage={pagination.prevPage}
            onNextPage={pagination.nextPage}
            pageSize={pagination.size}
            onPageSizeChange={pagination.setPageSize}
          />
        </div>
      </div>

      <EnrollmentCreateDialog
        open={dialogOpen}
        initialIntegrationId={integrationFilter}
        onClose={() => setDialogOpen(false)}
      />
    </AppShell>
  );
}
