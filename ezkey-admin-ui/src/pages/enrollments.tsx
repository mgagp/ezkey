import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Plus, QrCode, RefreshCw, Search } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Tooltip } from '@/components/ui/tooltip';
import { useDemoModeSession } from '@/context/use-demo-mode-session';
import { useAuth } from '@/context/use-auth';
import { useDebounce } from '@/hooks/use-debounce';
import { useIntegrations } from '@/hooks/use-integrations';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { ENROLLMENT_BUCKET_PARAM, type EnrollmentDrilldownBucket } from '@/lib/dashboard-drilldown-links';
import { ApiError, fetchBlobUrl } from '@/lib/api-client';
import { enrollmentDemoPresets, isDemoMode } from '@/lib/demo-mode';
import { buildListDetailNavState } from '@/lib/list-detail-navigation';
import { isPhoneNumberInputValid, normalizePhoneNumberInput } from '@/lib/phone-number';
import { formatDate } from '@/lib/utils';
import { create1, search1 } from '@/generated/admin-api/enrollments/enrollments';
import type {
  EnrollmentCreateRequestDto,
  EnrollmentCreateResponseDto,
  EnrollmentResponseDto,
  PagedModelEnrollmentResponseDto,
  Search1Params,
} from '@/generated/admin-api/model';

function parseEnrollmentBucket(value: string | null): EnrollmentDrilldownBucket | '' {
  if (value === 'inProgress' || value === 'unavailable' || value === 'incidents') {
    return value;
  }
  return '';
}

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
  const { t } = useTranslation('enrollments');
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { sessionDemoOn } = useDemoModeSession();
  const { list: integrations, isLoading: loadingIntegrations } = useIntegrations();
  const [createdEnrollment, setCreatedEnrollment] = useState<EnrollmentCreateResponseDto | null>(null);
  const [createdEnrollmentName, setCreatedEnrollmentName] = useState<string | null>(null);
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
  const [qrLoading, setQrLoading] = useState(false);

  const createSchema = useMemo(
    () =>
      z
        .object({
          integrationId: z.string().min(1, t('validation.selectIntegration')),
          name: z.string().min(1, t('validation.nameRequired')).max(100, t('validation.nameMax', { n: 100 })),
          authAttemptChallengeRequired: z.boolean(),
          contactEmail: z.string().email(t('validation.invalidEmail')).optional().or(z.literal('')),
          contactPhoneNumber: z
            .string()
            .refine((value) => value === '' || isPhoneNumberInputValid(value), t('validation.invalidPhone'))
            .optional()
            .or(z.literal('')),
          userIdentifier: z.string().max(100).optional().or(z.literal('')),
          useCustomInvitationExpiry: z.boolean(),
          invitationExpiresLocal: z.string().optional(),
        })
        .superRefine((data, ctx) => {
          if (!data.useCustomInvitationExpiry) {
            return;
          }
          const raw = data.invitationExpiresLocal?.trim() ?? '';
          if (raw === '') {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('validation.invitationExpiryRequired'),
              path: ['invitationExpiresLocal'],
            });
            return;
          }
          const parsed = new Date(raw);
          if (Number.isNaN(parsed.getTime())) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('validation.invitationExpiryInvalid'),
              path: ['invitationExpiresLocal'],
            });
            return;
          }
          if (parsed.getTime() <= Date.now()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('validation.invitationExpiryFuture'),
              path: ['invitationExpiresLocal'],
            });
          }
        }),
    [t],
  );
  type CreateFormValues = z.infer<typeof createSchema>;

  const freshDefaults = useMemo(
    (): CreateFormValues => ({
      integrationId: initialIntegrationId ?? '',
      name: '',
      contactEmail: '',
      contactPhoneNumber: '',
      userIdentifier: '',
      authAttemptChallengeRequired: false,
      useCustomInvitationExpiry: false,
      invitationExpiresLocal: '',
    }),
    [initialIntegrationId],
  );

  const {
    register,
    handleSubmit,
    reset,
    getValues,
    watch,
    formState: { errors },
  } = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
    defaultValues: freshDefaults,
  });

  useEffect(() => {
    if (!open) return;
    reset(freshDefaults);
  }, [open, freshDefaults, reset]);

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
    reset(freshDefaults);
    setCreatedEnrollment(null);
    setCreatedEnrollmentName(null);
    setQrCodeUrl(null);
    setQrLoading(false);
    createMutation.reset();
    onClose();
  };

  const onSubmit = (values: CreateFormValues) => {
    const payload: EnrollmentCreateRequestDto = {
      integrationId: parseInt(values.integrationId, 10),
      name: values.name,
      authAttemptChallengeRequired: values.authAttemptChallengeRequired,
      contactEmail: values.contactEmail || undefined,
      contactPhoneNumber: normalizePhoneNumberInput(values.contactPhoneNumber),
      userIdentifier: values.userIdentifier || undefined,
    };
    if (values.useCustomInvitationExpiry && values.invitationExpiresLocal?.trim()) {
      payload.expiresAt = new Date(values.invitationExpiresLocal.trim()).toISOString();
    }
    createMutation.mutate(payload);
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
    <Dialog open={open} onClose={handleClose} title={t('create.title')} size="md" dismissible={false}>
      {createdEnrollment ? (
        /* ── Success state: QR on demand + challenge ───────── */
        <div className="space-y-4">
          <Alert variant="success">
            {t('create.successMessage', { name: createdEnrollmentName ?? 'created' })}
          </Alert>

          <p className="text-xs text-fg-muted leading-relaxed border-l-2 border-fg/25 pl-3 py-0.5">
            {t('create.previewNotice')}
          </p>

          <div className="space-y-3">
            <p className="text-xs font-black uppercase tracking-widest text-fg-muted">
              {t('create.qrHint')}
            </p>
            <Button
              variant="secondary"
              size="sm"
              isLoading={qrLoading}
              onClick={handleToggleQr}
              className="gap-1.5"
            >
              <QrCode className="size-3.5" />
              {qrCodeUrl ? t('create.hideQrCode') : t('create.showQrCode')}
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
                {t('create.bindingChallengeCode')}
              </p>
              <p className="font-mono text-2xl font-black tracking-widest">
                {createdEnrollment.enrollmentChallenge}
              </p>
            </div>
          )}

          {createdEnrollment.expiresAt != null && createdEnrollment.expiresAt !== '' && (
            <div className="border-2 border-fg/30 p-3 bg-bg">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                {t('create.invitationExpiresAt')}
              </p>
              <p className="text-sm font-medium">{formatDate(createdEnrollment.expiresAt)}</p>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={handleViewEnrollment}>
              {t('create.viewEnrollment')}
            </Button>
            <Button onClick={handleClose}>{t('create.done')}</Button>
          </div>
        </div>
      ) : !loadingIntegrations && integrations.length === 0 ? (
        <Alert variant="warning">
          {t('create.noIntegration')}{' '}
          <Link to="/integrations" className="font-medium text-accent underline">{t('create.createIntegrationFirst')}</Link>.
        </Alert>
      ) : (
        /* ── Form state ──────────────────────────────────── */
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {isDemoMode && sessionDemoOn && (
            <div className="flex flex-wrap items-center gap-2 p-2 border-2 border-accent/30 bg-accent/5">
              <span className="text-xs font-bold text-fg-muted uppercase tracking-wider">{t('create.fillDemo')}</span>
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
                      useCustomInvitationExpiry: false,
                      invitationExpiresLocal: '',
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
            <Label htmlFor="enr-integration">{t('create.integration')}</Label>
            <Select
              id="enr-integration"
              disabled={loadingIntegrations}
              error={errors.integrationId?.message}
              {...register('integrationId')}
            >
              <option value="">{t('create.integrationPlaceholder')}</option>
              {integrations.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.code} — {i.name ?? i.code}
                </option>
              ))}
            </Select>
          </div>

          {/* Name */}
          <div className="space-y-1">
            <Label htmlFor="enr-name">{t('create.name')}</Label>
            <Input
              id="enr-name"
              placeholder={t('create.namePlaceholder')}
              error={errors.name?.message}
              {...register('name')}
            />
          </div>

          {/* Contact email */}
          <div className="space-y-1">
            <Label htmlFor="enr-email">{t('create.contactEmail')} <span className="text-fg-muted font-normal">{t('create.contactEmailOptional')}</span></Label>
            <Input
              id="enr-email"
              type="email"
              placeholder={t('create.contactEmailPlaceholder')}
              error={errors.contactEmail?.message}
              {...register('contactEmail')}
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="enr-phone">{t('create.contactPhone')} <span className="text-fg-muted font-normal">{t('create.contactEmailOptional')}</span></Label>
            <Input
              id="enr-phone"
              placeholder={t('create.contactPhonePlaceholder')}
              error={errors.contactPhoneNumber?.message}
              {...register('contactPhoneNumber')}
            />
          </div>

          {/* User identifier */}
          <div className="space-y-1">
            <Label htmlFor="enr-uid">{t('create.userIdentifier')} <span className="text-fg-muted font-normal">{t('create.contactEmailOptional')}</span></Label>
            <Input
              id="enr-uid"
              placeholder={t('create.userIdentifierPlaceholder')}
              error={errors.userIdentifier?.message}
              {...register('userIdentifier')}
            />
          </div>

          {/* Invitation expiry (pending phase) */}
          <div className="space-y-2 border-2 border-fg/20 p-3">
            <label className="flex items-start gap-3 cursor-pointer select-none">
              <input
                id="enr-custom-invite-expiry"
                type="checkbox"
                className="mt-0.5 size-4 accent-accent"
                {...register('useCustomInvitationExpiry')}
              />
              <div>
                <p className="text-sm font-bold">{t('create.customInvitationExpiry')}</p>
                <p className="text-xs text-fg-muted mt-0.5 leading-relaxed">
                  {t('create.customInvitationExpiryHint')}
                </p>
              </div>
            </label>
            {watch('useCustomInvitationExpiry') && (
              <div className="space-y-1 pt-1">
                <Label htmlFor="enr-invite-exp">{t('create.invitationExpiryDatetime')}</Label>
                <Input
                  id="enr-invite-exp"
                  type="datetime-local"
                  error={errors.invitationExpiresLocal?.message}
                  {...register('invitationExpiresLocal')}
                />
              </div>
            )}
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
              {t('create.challengeRequired')}
            </Label>
          </div>

          {/* API error */}
          {createMutation.isError && (
            <Alert variant="error">
              {createMutation.error instanceof ApiError
                ? createMutation.error.message
                : t('create.errorCreate')}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleClose}>{t('create.cancel')}</Button>
            <Button type="submit" isLoading={createMutation.isPending}>{t('create.submit')}</Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function EnrollmentsPage() {
  const { t } = useTranslation('enrollments');
  const { session } = useAuth();
  const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const enrollmentBucket = parseEnrollmentBucket(searchParams.get(ENROLLMENT_BUCKET_PARAM));
  const bucketKey = enrollmentBucket ?? 'none';
  const [bucketPageState, setBucketPageState] = useState(() => ({ bucketKey, page: 0 }));
  const bucketPage = bucketPageState.bucketKey === bucketKey ? bucketPageState.page : 0;
  const setBucketPage = useCallback(
    (nextPage: number | ((previousPage: number) => number)) => {
      setBucketPageState((previous) => {
        const currentPage = previous.bucketKey === bucketKey ? previous.page : 0;
        const page =
          typeof nextPage === 'function' ? nextPage(currentPage) : nextPage;
        return { bucketKey, page };
      });
    },
    [bucketKey],
  );
  const bucketPageSize = 20;

  const [nameInput, setNameInput] = useState('');
  const [statusFilter, setStatusFilter] = useState(() =>
    enrollmentBucket ? '' : (searchParams.get('status') ?? ''),
  );
  const [integrationFilter, setIntegrationFilter] = useState(searchParams.get('integrationId') ?? '');
  const [activeFilter, setActiveFilter] = useState(() => searchParams.get('active') ?? '');
  const [dialogOpen, setDialogOpen] = useState(false);

  const debouncedName = useDebounce(nameInput, 300);
  const { list: integrations } = useIntegrations();

  const clearBucketInUrl = useCallback(() => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete(ENROLLMENT_BUCKET_PARAM);
        return next;
      },
      { replace: true },
    );
  }, [setSearchParams]);

  useEffect(() => {
    if (debouncedName) {
      clearBucketInUrl();
    }
  }, [debouncedName, clearBucketInUrl]);

  useEffect(() => {
    if (enrollmentBucket) {
      return;
    }
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (statusFilter) {
          next.set('status', statusFilter);
        } else {
          next.delete('status');
        }
        if (activeFilter) {
          next.set('active', activeFilter);
        } else {
          next.delete('active');
        }
        if (integrationFilter) {
          next.set('integrationId', integrationFilter);
        } else {
          next.delete('integrationId');
        }
        return next;
      },
      { replace: true },
    );
  }, [activeFilter, enrollmentBucket, integrationFilter, setSearchParams, statusFilter]);

  const bucketQueries = useQueries({
    queries: [
      {
        queryKey: ['enrollments', 'bucket', 'CREATED', integrationFilter],
        queryFn: () =>
          search1({
            status: 'CREATED',
            page: 0,
            size: 500,
            sort: ['createdAt,DESC'],
            integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
          } as Search1Params) as Promise<PagedModelEnrollmentResponseDto>,
        enabled: enrollmentBucket === 'inProgress',
      },
      {
        queryKey: ['enrollments', 'bucket', 'BOUND', integrationFilter],
        queryFn: () =>
          search1({
            status: 'BOUND',
            page: 0,
            size: 500,
            sort: ['createdAt,DESC'],
            integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
          } as Search1Params) as Promise<PagedModelEnrollmentResponseDto>,
        enabled: enrollmentBucket === 'inProgress',
      },
      {
        queryKey: ['enrollments', 'bucket', 'INVALID', integrationFilter],
        queryFn: () =>
          search1({
            status: 'INVALID',
            active: true,
            page: 0,
            size: 500,
            sort: ['createdAt,DESC'],
            integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
          } as Search1Params) as Promise<PagedModelEnrollmentResponseDto>,
        enabled: enrollmentBucket === 'unavailable',
      },
      {
        queryKey: ['enrollments', 'bucket', 'REVOKED', integrationFilter],
        queryFn: () =>
          search1({
            status: 'REVOKED',
            active: true,
            page: 0,
            size: 500,
            sort: ['createdAt,DESC'],
            integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
          } as Search1Params) as Promise<PagedModelEnrollmentResponseDto>,
        enabled: enrollmentBucket === 'unavailable',
      },
      {
        queryKey: ['enrollments', 'bucket', 'INVALID-incidents', integrationFilter],
        queryFn: () =>
          search1({
            status: 'INVALID',
            page: 0,
            size: 500,
            sort: ['createdAt,DESC'],
            integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
          } as Search1Params) as Promise<PagedModelEnrollmentResponseDto>,
        enabled: enrollmentBucket === 'incidents',
      },
      {
        queryKey: ['enrollments', 'bucket', 'REVOKED-incidents', integrationFilter],
        queryFn: () =>
          search1({
            status: 'REVOKED',
            page: 0,
            size: 500,
            sort: ['createdAt,DESC'],
            integrationId: integrationFilter ? parseInt(integrationFilter, 10) : undefined,
          } as Search1Params) as Promise<PagedModelEnrollmentResponseDto>,
        enabled: enrollmentBucket === 'incidents',
      },
    ],
  });

  const mergedBucketRows = useMemo(() => {
    if (enrollmentBucket === 'inProgress') {
      const a = bucketQueries[0].data?.content ?? [];
      const b = bucketQueries[1].data?.content ?? [];
      return [...a, ...b].sort((x, y) => (y.createdAt ?? '').localeCompare(x.createdAt ?? ''));
    }
    if (enrollmentBucket === 'unavailable') {
      const a = bucketQueries[2].data?.content ?? [];
      const b = bucketQueries[3].data?.content ?? [];
      return [...a, ...b].sort((x, y) => (y.createdAt ?? '').localeCompare(x.createdAt ?? ''));
    }
    if (enrollmentBucket === 'incidents') {
      const a = bucketQueries[4].data?.content ?? [];
      const b = bucketQueries[5].data?.content ?? [];
      return [...a, ...b].sort((x, y) => (y.createdAt ?? '').localeCompare(x.createdAt ?? ''));
    }
    return [];
  }, [bucketQueries, enrollmentBucket]);

  const bucketTruncated =
    (enrollmentBucket === 'inProgress'
      && ((bucketQueries[0].data?.page?.totalElements ?? 0) > 500
        || (bucketQueries[1].data?.page?.totalElements ?? 0) > 500))
    || (enrollmentBucket === 'unavailable'
      && ((bucketQueries[2].data?.page?.totalElements ?? 0) > 500
        || (bucketQueries[3].data?.page?.totalElements ?? 0) > 500))
    || (enrollmentBucket === 'incidents'
      && ((bucketQueries[4].data?.page?.totalElements ?? 0) > 500
        || (bucketQueries[5].data?.page?.totalElements ?? 0) > 500));

  const bucketSlice = useMemo(() => {
    const start = bucketPage * bucketPageSize;
    return mergedBucketRows.slice(start, start + bucketPageSize);
  }, [bucketPage, mergedBucketRows]);

  const bucketTotalPages = Math.max(1, Math.ceil(mergedBucketRows.length / bucketPageSize) || 1);

  const bucketIsLoading = enrollmentBucket
    ? bucketQueries.some((q, i) => {
        const enabled =
          (enrollmentBucket === 'inProgress' && i < 2)
          || (enrollmentBucket === 'unavailable' && i >= 2 && i < 4)
          || (enrollmentBucket === 'incidents' && i >= 4);
        return enabled && q.isLoading;
      })
    : false;

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
      active: activeFilter === '' ? undefined : activeFilter === 'true' ? true : activeFilter === 'false' ? false : undefined,
    },
    fetchPage: (params) => search1(params as Search1Params) as Promise<PagedModelEnrollmentResponseDto>,
    enabled: !enrollmentBucket,
  });

  const columns: ColumnDef<EnrollmentResponseDto>[] = useMemo(() => {
    const statusCol: ColumnDef<EnrollmentResponseDto> = {
      header: t('list.columns.status'),
      key: 'enrollmentStatus',
      sortKey: 'status',
      render: (r) => {
        const showWarning =
          r.enrollmentStatus === 'VERIFIED' && r.operational === false;
        const warningTooltip =
          r.enrollmentActive === false
            ? t('list.deactivatedWarningTooltip')
            : t('list.operationalWarningTooltip');
        return showWarning ? (
          <Tooltip content={warningTooltip}>
            <span className="inline-flex items-center gap-1.5 border border-warning/40 rounded-sm px-1.5">
              <EnrollmentStatusBadge status={r.enrollmentStatus} />
              <AlertTriangle className="size-4 text-warning" aria-hidden />
            </span>
          </Tooltip>
        ) : (
          <EnrollmentStatusBadge status={r.enrollmentStatus} />
        );
      },
    };

    const integrationCol: ColumnDef<EnrollmentResponseDto> = {
      header: t('list.columns.integration'),
      key: 'integrationName',
      sortKey: 'integrationId',
      render: (r) => {
        if (r.integrationId == null) {
          return <span className="text-fg-muted">—</span>;
        }
        const label =
          r.integrationName != null && r.integrationName.trim() !== ''
            ? r.integrationName.trim()
            : t('list.integrationFallback', { id: r.integrationId });
        return (
          <Link
            to={`/integrations/${r.integrationId}`}
            className="text-xs font-medium text-accent hover:underline max-w-[12rem] truncate block"
            title={label}
            onClick={(e) => e.stopPropagation()}
          >
            {label}
          </Link>
        );
      },
    };

    const tenantCol: ColumnDef<EnrollmentResponseDto> = {
      header: t('list.columns.tenant'),
      key: 'tenantName',
      render: (r) => {
        const row = r as EnrollmentResponseDto & {
          tenantId?: number | null;
          tenantName?: string | null;
        };
        if (row.tenantId == null) {
          return <span className="text-fg-muted">—</span>;
        }
        const label =
          row.tenantName != null && row.tenantName.trim() !== ''
            ? row.tenantName.trim()
            : t('list.tenantFallback', { id: row.tenantId });
        return (
          <Link
            to={`/tenants/${row.tenantId}`}
            className="text-xs font-medium text-accent hover:underline"
            onClick={(e) => e.stopPropagation()}
          >
            {label}
          </Link>
        );
      },
    };

    const userIdCol: ColumnDef<EnrollmentResponseDto> = {
      header: t('list.columns.userIdentifier'),
      key: 'userIdentifier',
      sortKey: 'userIdentifier',
      render: (r) => (
        <span
          className="text-xs text-fg-muted max-w-[10rem] truncate block"
          title={r.userIdentifier ?? undefined}
        >
          {r.userIdentifier ?? '—'}
        </span>
      ),
    };

    const createdCol: ColumnDef<EnrollmentResponseDto> = {
      header: t('list.columns.created'),
      key: 'createdAt',
      sortKey: 'createdAt',
      render: (r) => (
        <span className="text-xs text-fg-muted whitespace-nowrap">
          {r.createdAt ? formatDate(r.createdAt) : '—'}
        </span>
      ),
    };

    const lastUsedCol: ColumnDef<EnrollmentResponseDto> = {
      header: t('list.columns.lastUsed'),
      key: 'lastUsedAt',
      sortKey: 'lastUsedAt',
      render: (r) => (
        <span className="text-xs text-fg-muted whitespace-nowrap">
          {r.lastUsedAt ? formatDate(r.lastUsedAt) : '—'}
        </span>
      ),
    };

    const base: ColumnDef<EnrollmentResponseDto>[] = [
      {
        header: t('list.columns.id'),
        key: 'enrollmentId',
        className: 'w-14',
        sortKey: 'enrollmentId',
        render: (r) => <span className="font-mono text-xs">{r.enrollmentId}</span>,
      },
      {
        header: t('list.columns.name'),
        key: 'enrollmentName',
        sortKey: 'enrollmentName',
        render: (r) => <span className="font-medium">{r.enrollmentName}</span>,
      },
      statusCol,
      integrationCol,
      userIdCol,
      ...(isGlobalAdmin ? [tenantCol] : []),
      createdCol,
      lastUsedCol,
    ];

    return base;
  }, [isGlobalAdmin, t]);

  return (
    <AppShell title={t('list.title')}>
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="flex-1 min-w-48 relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-fg-muted pointer-events-none" />
            <Input
              placeholder={t('list.searchPlaceholder')}
              value={nameInput}
              onChange={(e) => setNameInput(e.target.value)}
              className="pl-8"
            />
          </div>

          <div className="w-36">
            <Select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                clearBucketInUrl();
              }}
            >
              <option value="">{t('list.filterStatusAll')}</option>
              <option value="CREATED">{t('list.filterStatusCreated')}</option>
              <option value="BOUND">{t('list.filterStatusBound')}</option>
              <option value="VERIFIED">{t('list.filterStatusVerified')}</option>
              <option value="INVALID">{t('list.filterStatusInvalid')}</option>
              <option value="REVOKED">{t('list.filterStatusRevoked')}</option>
              <option value="EXPIRED">{t('list.filterStatusExpired')}</option>
            </Select>
          </div>

          <div className="w-52">
            <Select
              value={integrationFilter}
              onChange={(e) => {
                setIntegrationFilter(e.target.value);
                clearBucketInUrl();
              }}
            >
              <option value="">{t('list.filterIntegrationAll')}</option>
              {integrations.map((i) => (
                <option key={i.id} value={String(i.id)}>{i.code}</option>
              ))}
            </Select>
          </div>

          <div className="w-32">
            <Select
              value={activeFilter}
              onChange={(e) => {
                setActiveFilter(e.target.value);
                clearBucketInUrl();
              }}
            >
              <option value="">{t('list.filterActiveAny')}</option>
              <option value="true">{t('list.filterActiveActive')}</option>
              <option value="false">{t('list.filterActiveInactive')}</option>
            </Select>
          </div>

          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="size-3.5" />
            {t('list.refresh')}
          </Button>

          <Button size="sm" onClick={() => setDialogOpen(true)} className="gap-1.5 ml-auto">
            <Plus className="size-3.5" />
            {t('list.newEnrollment')}
          </Button>
        </div>

        {enrollmentBucket && bucketTruncated && (
          <Alert variant="warning">{t('list.bucketTruncationWarning')}</Alert>
        )}

        {/* Table */}
        <div>
          {enrollmentBucket ? (
            <>
              <PaginatedTable
                columns={columns}
                data={bucketSlice}
                isLoading={bucketIsLoading}
                onRowClick={(row) => {
                  const st = buildListDetailNavState(
                    bucketSlice,
                    (r) => r.enrollmentId ?? 0,
                    row,
                    bucketPage < bucketTotalPages - 1,
                  );
                  navigate(`/enrollments/${row.enrollmentId}`, { state: st ?? undefined });
                }}
                keyExtractor={(row, i) => row.enrollmentId ?? i}
                emptyMessage={t('list.emptyMessage')}
                currentSort="createdAt,DESC"
                pagination={{
                  page: bucketPage,
                  size: bucketPageSize,
                  totalPages: bucketTotalPages,
                  totalElements: mergedBucketRows.length,
                  isFirst: bucketPage === 0,
                  isLast: bucketPage >= bucketTotalPages - 1,
                  firstPage: () => {
                    setBucketPage(0);
                  },
                  lastPage: () => {
                    setBucketPage(Math.max(0, bucketTotalPages - 1));
                  },
                  nextPage: () => {
                    setBucketPage((p) => Math.min(bucketTotalPages - 1, p + 1));
                  },
                  prevPage: () => {
                    setBucketPage((p) => Math.max(0, p - 1));
                  },
                  setPageSize: () => {},
                }}
              />
            </>
          ) : (
            <PaginatedTable
              columns={columns}
              data={data}
              isLoading={isLoading}
              onRowClick={(row) => {
                const st = buildListDetailNavState(data, (r) => r.enrollmentId ?? 0, row, !pagination.isLast);
                navigate(`/enrollments/${row.enrollmentId}`, { state: st ?? undefined });
              }}
              keyExtractor={(row, i) => row.enrollmentId ?? i}
              emptyMessage={t('list.emptyMessage')}
              currentSort={pagination.sort}
              onSort={pagination.setSort}
              pagination={pagination}
            />
          )}
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
