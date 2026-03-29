import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Check, Copy, Eye, EyeOff, Pencil, Power, PowerOff, QrCode, ShieldOff, Trash2, Zap } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip } from '@/components/ui/tooltip';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { useToast } from '@/context/toast-context';
import { useListDetailPageNavigation } from '@/hooks/use-list-detail-page-navigation';
import { useExpandableRelatedDetails } from '@/hooks/use-expandable-related-details';
import { DetailPageNav } from '@/components/ui/detail-page-nav';
import { getIntegrationName, useIntegrations } from '@/hooks/use-integrations';
import { ApiError, fetchBlobUrl, getApiErrorMessage } from '@/lib/api-client';
import { authContextDemoPresets, isDemoMode } from '@/lib/demo-mode';
import { formatChallengeCode, formatCountdown, formatDate } from '@/lib/utils';
import { useCancel, useCreate2, useGetById2 } from '@/generated/admin-api/auth-attempts/auth-attempts';
import {
  getGetByIdQueryKey,
  useDeactivate,
  useDelete,
  useGetById,
  useReactivate,
  useRevoke,
  useUpdate,
} from '@/generated/admin-api/enrollments/enrollments';
import type {
  AuthAttemptCreateRequestDto,
  AuthAttemptDto,
  AuthAttemptDtoAuthAttemptStatus,
  EnrollmentResponseDto,
  EnrollmentUpdateRequestDto,
} from '@/generated/admin-api/model';

// ── Local types (not yet in OpenAPI spec) ────────────────────────────────────

/** Response from POST /api/v1/auth-attempts — not yet specified in the OpenAPI schema. */
interface AuthAttemptCreateResponse {
  authAttemptId: number;
  authAttemptChallenge?: number;
  timeoutSeconds: number;
  expiresAt: string;
}

// ── ISO datetime-local (for invitation expiresAt PATCH) ───────────────────────

function isoToDatetimeLocal(iso: string | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// ── Info row helper ──────────────────────────────────────────────────────────

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-4">
      <dt className="w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">
        {label}
      </dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

// ── Test Auth Dialog ──────────────────────────────────────────────────────────

const FINAL_STATUSES: AuthAttemptDtoAuthAttemptStatus[] = ['ACCEPTED', 'REJECTED', 'EXPIRED', 'INVALID'];

function authStatusVariant(s: AuthAttemptDtoAuthAttemptStatus): 'success' | 'error' | 'warning' | 'muted' {
  if (s === 'ACCEPTED') return 'success';
  if (s === 'REJECTED' || s === 'INVALID') return 'error';
  if (s === 'READ') return 'warning';
  return 'muted';
}

function authStatusLabel(s: AuthAttemptDtoAuthAttemptStatus, t: (key: string) => string): string {
  switch (s) {
    case 'PENDING': return t('testAuth.statusPending');
    case 'READ':    return t('testAuth.statusRead');
    case 'ACCEPTED': return t('testAuth.statusAccepted');
    case 'REJECTED': return t('testAuth.statusRejected');
    case 'EXPIRED':  return t('testAuth.statusExpired');
    case 'INVALID':  return t('testAuth.statusInvalid');
  }
}

function TestAuthDialog({
  open,
  onClose,
  enrollment,
}: {
  open: boolean;
  onClose: () => void;
  enrollment: EnrollmentResponseDto;
}) {
  const { t } = useTranslation('enrollments');
  type Step = 'configure' | 'live' | 'done';
  type DoneReason = 'accepted' | 'rejected' | 'expired' | 'invalid' | 'cancelled';

  const { sessionDemoOn } = useDemoModeSession();
  const [step, setStep] = useState<Step>('configure');
  const [challengeRequested, setChallengeRequested] = useState(
    enrollment.authAttemptChallengeRequired ?? false,
  );
  const [contextTitle, setContextTitle] = useState('');
  const [contextMessage, setContextMessage] = useState('');
  const [demoMitmSignatureRequested, setDemoMitmSignatureRequested] = useState(false);
  const [createdAttempt, setCreatedAttempt] = useState<AuthAttemptCreateResponse | null>(null);
  const [isFinal, setIsFinal] = useState(false);
  const [doneReason, setDoneReason] = useState<DoneReason | null>(null);
  const [countdown, setCountdown] = useState(0);

  // Countdown drives from expiresAt, stops when final
  useEffect(() => {
    if (step !== 'live' || !createdAttempt || isFinal) return;
    const tick = () => {
      const rem = Math.max(
        0,
        Math.floor((new Date(createdAttempt.expiresAt).getTime() - Date.now()) / 1000),
      );
      setCountdown(rem);
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [step, createdAttempt, isFinal]);

  // Live status — polls every 3 s, stops automatically on final state
  const { data: liveStatus } = useGetById2<AuthAttemptDto>(
    createdAttempt?.authAttemptId ?? 0,
    {
      query: {
        enabled: step === 'live' && createdAttempt !== null,
        refetchInterval: isFinal ? false : 3_000,
      },
    },
  );

  // Transition to done when status becomes final
  useEffect(() => {
    if (!liveStatus || isFinal) return;
    if (FINAL_STATUSES.includes(liveStatus.authAttemptStatus)) {
      setIsFinal(true);
      setDoneReason(liveStatus.authAttemptStatus.toLowerCase() as DoneReason);
      setStep('done');
    }
  }, [liveStatus, isFinal]);

  const createMutation = useCreate2({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as AuthAttemptCreateResponse;
        setCreatedAttempt(res);
        setCountdown(res.timeoutSeconds);
        setStep('live');
      },
    },
  });

  const cancelMutation = useCancel({
    mutation: {
      onSuccess: () => {
        setIsFinal(true);
        setDoneReason('cancelled');
        setStep('done');
      },
    },
  });

  const resetState = () => {
    setStep('configure');
    setChallengeRequested(enrollment.authAttemptChallengeRequired ?? false);
    setContextTitle('');
    setContextMessage('');
    setDemoMitmSignatureRequested(false);
    setCreatedAttempt(null);
    setIsFinal(false);
    setDoneReason(null);
    setCountdown(0);
    createMutation.reset();
    cancelMutation.reset();
  };

  const handleClose = () => { resetState(); onClose(); };

  return (
    <Dialog open={open} onClose={handleClose} title={t('testAuth.title')} size="md" dismissible={false}>

      {/* ── Step 1: Configure ── */}
      {step === 'configure' && (
        <div className="space-y-4">
          <p className="text-sm text-fg">
            {t('testAuth.intro', { name: enrollment.enrollmentName })}
          </p>

          <label className="flex items-start gap-3 cursor-pointer select-none p-3 border-2 border-fg/20 hover:border-fg/40 transition-colors">
            <input
              type="checkbox"
              className="mt-0.5 size-4 accent-accent"
              checked={challengeRequested}
              onChange={(e) => setChallengeRequested(e.target.checked)}
            />
            <div>
              <p className="text-sm font-bold">{t('testAuth.requestChallenge')}</p>
              <p className="text-xs text-fg-muted mt-0.5">
                {t('testAuth.challengeHint')}
              </p>
            </div>
          </label>

          <div className="space-y-2 border-2 border-fg/20 p-3">
            <p className="text-xs font-bold uppercase tracking-wider text-fg-muted">
              {t('testAuth.contextOptional')}
            </p>
            <div className="grid gap-2">
              <Label htmlFor="testAuth-contextTitle" className="text-sm">
                {t('testAuth.contextTitle')}
              </Label>
              <Input
                id="testAuth-contextTitle"
                value={contextTitle}
                onChange={(e) => setContextTitle(e.target.value)}
                placeholder={t('testAuth.contextTitlePlaceholder')}
                maxLength={200}
                className="border-2 border-fg/30"
              />
              <Label htmlFor="testAuth-contextMessage" className="text-sm">
                {t('testAuth.contextMessage')}
              </Label>
              <Textarea
                id="testAuth-contextMessage"
                value={contextMessage}
                onChange={(e) => setContextMessage(e.target.value)}
                placeholder={t('testAuth.contextMessagePlaceholder')}
                maxLength={2000}
                rows={3}
                className="border-fg/30"
              />
            </div>
            {isDemoMode && sessionDemoOn && (
              <div className="flex flex-wrap items-center gap-1.5 pt-2">
                <span className="text-xs font-bold text-fg-muted uppercase tracking-wider mr-1">
                  {t('testAuth.contextFillDemo')}
                </span>
                {authContextDemoPresets.map((preset) => (
                  <Button
                    key={preset.id}
                    type="button"
                    variant="secondary"
                    size="sm"
                    className="text-xs h-7"
                    onClick={() => {
                      setContextTitle(preset.contextTitle);
                      setContextMessage(preset.contextMessage);
                    }}
                  >
                    {preset.label}
                  </Button>
                ))}
              </div>
            )}
          </div>

          {isDemoMode && sessionDemoOn && (
            <label className="flex items-start gap-3 cursor-pointer select-none p-3 border-2 border-dashed border-warning/50 bg-warning/5 hover:border-warning/70 transition-colors">
              <input
                type="checkbox"
                className="mt-0.5 size-4 accent-warning"
                checked={demoMitmSignatureRequested}
                onChange={(e) => setDemoMitmSignatureRequested(e.target.checked)}
              />
              <div>
                <p className="text-sm font-bold">{t('testAuth.demoMitmLabel')}</p>
                <p className="text-xs text-fg-muted mt-0.5">{t('testAuth.demoMitmHint')}</p>
              </div>
            </label>
          )}

          {createMutation.isError && (
            <Alert variant="error">
              {getApiErrorMessage(createMutation.error, t('testAuth.errorCreateAttempt'))}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={handleClose}>{t('testAuth.cancel')}</Button>
            <Button
              isLoading={createMutation.isPending}
              onClick={() => {
                const data: AuthAttemptCreateRequestDto = {
                  enrollmentId: enrollment.enrollmentId,
                  challengeRequested,
                };
                if (contextTitle.trim()) data.contextTitle = contextTitle.trim();
                if (contextMessage.trim()) data.contextMessage = contextMessage.trim();
                if (isDemoMode && sessionDemoOn && demoMitmSignatureRequested) {
                  data.demoMitmSignatureRequested = true;
                }
                createMutation.mutate({ data });
              }}
              className="gap-1.5"
            >
              <Zap className="size-3.5" />
              {t('testAuth.launchTest')}
            </Button>
          </div>
        </div>
      )}

      {/* ── Step 2: Live ── */}
      {step === 'live' && createdAttempt && (
        <div className="space-y-4">
          {liveStatus?.demoMitmSignatureEnabled && (
            <Alert variant="warning">{t('testAuth.demoMitmLiveHint')}</Alert>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div className="border-2 border-fg/30 p-3">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                {t('testAuth.attemptId')}
              </p>
              <p className="font-mono font-black text-xl">{createdAttempt.authAttemptId}</p>
            </div>
            <div className="border-2 border-fg/30 p-3">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                {t('testAuth.expiresIn')}
              </p>
              <p
                className={`font-mono font-black text-xl ${
                  countdown <= 10 ? 'text-error animate-pulse' : ''
                }`}
              >
                {formatCountdown(countdown)}
              </p>
            </div>
          </div>

          {createdAttempt.authAttemptChallenge != null && (
            <div className="border-2 border-fg p-4 bg-bg text-center shadow-brutal">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-2">
                {t('testAuth.challengeCode')}
              </p>
              <p className="font-mono text-5xl font-black tracking-[0.5em] text-accent">
                {formatChallengeCode(createdAttempt.authAttemptChallenge)}
              </p>
              <p className="text-xs text-fg-muted mt-2">
                {t('testAuth.challengeHintLive')}
              </p>
            </div>
          )}

          <div className="flex items-center gap-2 py-1">
            <span className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
              {t('testAuth.status')}
            </span>
            {liveStatus ? (
              <Badge variant={authStatusVariant(liveStatus.authAttemptStatus)}>
                {authStatusLabel(liveStatus.authAttemptStatus, t)}
              </Badge>
            ) : (
              <span className="size-3.5 border-2 border-fg/30 border-t-fg rounded-full animate-spin inline-block" />
            )}
          </div>

          <div className="space-y-3 pt-2">
            <p className="text-xs text-fg-muted leading-relaxed">{t('testAuth.abandonHint')}</p>
            <div className="flex justify-end gap-2 flex-wrap">
              <Button
                variant="ghost"
                size="sm"
                onClick={handleClose}
                disabled={cancelMutation.isPending}
                className="border border-fg/30 hover:border-fg/50 hover:bg-fg/5"
              >
                {t('testAuth.abandon')}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                isLoading={cancelMutation.isPending}
                onClick={() => cancelMutation.mutate({ id: createdAttempt!.authAttemptId! })}
                className="gap-1 border border-error/30 text-error hover:border-error hover:bg-error/10"
              >
                {t('testAuth.cancelAttempt')}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 3: Done ── */}
      {step === 'done' && (
        <div className="space-y-4">
          {doneReason === 'accepted' && (
            <Alert variant="success">{t('testAuth.doneAccepted')}</Alert>
          )}
          {doneReason === 'rejected' && (
            <Alert variant="error">{t('testAuth.doneRejected')}</Alert>
          )}
          {doneReason === 'expired' && (
            <Alert variant="warning">{t('testAuth.doneExpired')}</Alert>
          )}
          {doneReason === 'invalid' && (
            <Alert variant="error">{t('testAuth.doneInvalid')}</Alert>
          )}
          {doneReason === 'cancelled' && (
            <Alert variant="info">{t('testAuth.doneCancelled')}</Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={resetState}>
              {t('testAuth.runAnotherTest')}
            </Button>
            <Button onClick={handleClose}>{t('testAuth.close')}</Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function EnrollmentDetailPage() {
  const { t } = useTranslation('enrollments');
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const enrollmentId = Number(id);

  const { nav: enrollmentListNav, goPrev: goPrevEnrollment, goNext: goNextEnrollment, showEndOfPageHint: showEnrollmentListEndHint } =
    useListDetailPageNavigation({
      currentId: enrollmentId,
      pathPrefix: '/enrollments',
    });

  const [tokenVisible, setTokenVisible] = useState(false);
  const [tokenCopied, setTokenCopied] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
  const [qrLoading, setQrLoading] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [revokeConfirm, setRevokeConfirm] = useState(false);
  const [revokeReason, setRevokeReason] = useState('');
  const [lifecycleConfirm, setLifecycleConfirm] = useState<'deactivate' | 'reactivate' | null>(null);
  const [lifecycleReason, setLifecycleReason] = useState('');
  const [testAuthOpen, setTestAuthOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editName, setEditName] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [editUserId, setEditUserId] = useState('');
  const [editChallenge, setEditChallenge] = useState(false);
  const [editExpiresLocal, setEditExpiresLocal] = useState('');

  const { toast } = useToast();
  const { lookup } = useIntegrations();

  const { data: enrollment, isLoading } = useGetById<EnrollmentResponseDto>(
    isNaN(enrollmentId) ? 0 : enrollmentId,
    { query: { enabled: !isNaN(enrollmentId) } },
  );

  const relatedDetails = useExpandableRelatedDetails({
    integrationId: enrollment?.integrationId ?? undefined,
  });

  const deleteMutation = useDelete({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        void queryClient.invalidateQueries({ queryKey: ['stats'] });
        toast(t('detail.toastDeleted'));
        navigate('/enrollments');
      },
    },
  });

  const deactivateMutation = useDeactivate({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetByIdQueryKey(enrollmentId) });
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast(t('detail.toastDeactivated'));
      },
    },
  });

  const reactivateMutation = useReactivate({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetByIdQueryKey(enrollmentId) });
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast(t('detail.toastReactivated'));
      },
    },
  });

  const revokeMutation = useRevoke({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetByIdQueryKey(enrollmentId) });
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast(t('detail.toastRevoked'), 'error');
        setRevokeConfirm(false);
        setRevokeReason('');
      },
    },
  });

  const updateMutation = useUpdate({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetByIdQueryKey(enrollmentId) });
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast(t('detail.toastUpdated'));
        setEditOpen(false);
      },
      onError: (err) => {
        if (err instanceof ApiError && err.status === 409) {
          void queryClient.invalidateQueries({ queryKey: getGetByIdQueryKey(enrollmentId) });
        }
      },
    },
  });

  const openEditMetadata = () => {
    if (!enrollment) return;
    setEditName(enrollment.enrollmentName ?? '');
    setEditEmail(enrollment.contactEmail ?? '');
    setEditUserId(enrollment.userIdentifier ?? '');
    setEditChallenge(Boolean(enrollment.authAttemptChallengeRequired));
    setEditExpiresLocal(isoToDatetimeLocal(enrollment.expiresAt));
    setEditOpen(true);
  };

  const handleSaveMetadata = () => {
    if (!enrollment?.enrollmentId) return;
    const name = editName.trim();
    if (!name) return;
    const data: EnrollmentUpdateRequestDto = {
      version: enrollment.version,
      enrollmentName: name,
      authAttemptChallengeRequired: editChallenge,
      userIdentifier: editUserId.trim(),
    };
    const email = editEmail.trim();
    if (email) {
      data.contactEmail = email;
    }
    if (editExpiresLocal) {
      data.expiresAt = new Date(editExpiresLocal).toISOString();
    }
    updateMutation.mutate({ id: enrollment.enrollmentId, data });
  };

  const canEditMetadata =
    enrollment != null &&
    enrollment.enrollmentStatus === 'VERIFIED' &&
    enrollment.enrollmentActive === true;

  // Revoke blob URL on unmount or when QR is hidden
  useEffect(() => {
    return () => {
      if (qrCodeUrl) URL.revokeObjectURL(qrCodeUrl);
    };
  }, [qrCodeUrl]);

  const handleCopyToken = async () => {
    if (!enrollment?.enrollmentProofToken) return;
    try {
      await navigator.clipboard.writeText(enrollment.enrollmentProofToken);
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 2000);
    } catch {
      // ignore clipboard API errors
    }
  };

  const handleToggleQr = async () => {
    if (qrCodeUrl) {
      URL.revokeObjectURL(qrCodeUrl);
      setQrCodeUrl(null);
      return;
    }
    setQrLoading(true);
    try {
      const url = await fetchBlobUrl(`/api/v1/enrollments/${enrollmentId}/qrcode`);
      setQrCodeUrl(url);
    } catch {
      // QR load failed — silently ignore, user can retry
    } finally {
      setQrLoading(false);
    }
  };

  return (
    <AppShell
      title={enrollment?.enrollmentName ?? t('detail.fallbackTitle')}
      detailNav={
        enrollmentListNav ? (
          <DetailPageNav
            hasPrev={enrollmentListNav.prevId !== undefined}
            hasNext={enrollmentListNav.nextId !== undefined}
            onPrev={goPrevEnrollment}
            onNext={goNextEnrollment}
            showEndOfPageHint={showEnrollmentListEndHint}
          />
        ) : undefined
      }
      breadcrumb={[
        { label: t('detail.breadcrumbEnrollments'), path: '/enrollments' },
        ...(enrollment?.integrationId
          ? [{
              label:
                (enrollment as { integrationName?: string }).integrationName ??
                lookup.get(enrollment.integrationId) ??
                `Integration #${enrollment.integrationId}`,
              ...((enrollment as { isSystemIntegration?: boolean }).isSystemIntegration
                ? {}
                : { path: `/integrations/${enrollment.integrationId}` }),
            }]
          : []),
      ]}
    >
      <div className="space-y-6">

        {/* Test Auth */}
        <div className="flex items-center justify-end">
          {enrollment?.enrollmentStatus === 'VERIFIED' && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setTestAuthOpen(true)}
              className="gap-1.5"
            >
              <Zap className="size-3.5" />
              {t('detail.testAuthentication')}
            </Button>
          )}
        </div>

        {isLoading && (
          <div className="flex justify-center py-12">
            <span className="size-6 border-2 border-fg/30 border-t-fg rounded-full animate-spin" />
          </div>
        )}

        {enrollment && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

            {/* Info Card */}
            <Card>
              <CardHeader className="flex flex-row items-center justify-between gap-2">
                <CardTitle>{t('detail.enrollmentInfo')}</CardTitle>
                <div className="flex flex-wrap items-center gap-2 justify-end">
                  {canEditMetadata && (
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={openEditMetadata}
                      className="gap-1.5"
                    >
                      <Pencil className="size-3.5" />
                      {t('detail.editMetadata')}
                    </Button>
                  )}
                  {relatedDetails.hasAnyFk && (
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
                  )}
                </div>
              </CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label={t('detail.infoId')}><span className="font-mono">{enrollment.enrollmentId}</span></InfoRow>
                  <InfoRow label={t('detail.infoName')}><span className="font-medium">{enrollment.enrollmentName}</span></InfoRow>
                  <InfoRow label={t('detail.infoStatus')}><EnrollmentStatusBadge status={enrollment.enrollmentStatus} /></InfoRow>
                  <InfoRow label={t('detail.infoActive')}>
                    <Badge variant={enrollment.enrollmentActive ? 'success' : 'muted'}>
                      {enrollment.enrollmentActive ? t('list.activeYes') : t('list.activeNo')}
                    </Badge>
                  </InfoRow>
                  <InfoRow label={t('detail.infoIntegration')}>
                    {(() => {
                      const displayName =
                        relatedDetails.isExpanded && relatedDetails.integration
                          ? `${getIntegrationName(relatedDetails.integration)} (ID ${relatedDetails.integration.id})`
                          : (enrollment as { integrationName?: string }).integrationName ??
                              lookup.get(enrollment.integrationId!) ??
                              `#${enrollment.integrationId ?? '?'}`;
                      return (enrollment as { isSystemIntegration?: boolean }).isSystemIntegration ? (
                        <span className="font-medium">{displayName}</span>
                      ) : (
                        <button
                          className="font-mono text-sm text-accent hover:underline"
                          onClick={() => navigate(`/integrations/${enrollment.integrationId}`)}
                        >
                          {displayName}
                        </button>
                      );
                    })()}
                  </InfoRow>
                  <InfoRow label={t('detail.infoChallenge')}>
                    <Badge variant={enrollment.authAttemptChallengeRequired ? 'warning' : 'muted'}>
                      {enrollment.authAttemptChallengeRequired ? t('detail.challengeRequired') : t('detail.challengeNotRequired')}
                    </Badge>
                  </InfoRow>
                  {enrollment.contactEmail && (
                    <InfoRow label={t('detail.infoContactEmail')}>{enrollment.contactEmail}</InfoRow>
                  )}
                  {enrollment.userIdentifier && (
                    <InfoRow label={t('detail.infoUserId')}>{enrollment.userIdentifier}</InfoRow>
                  )}
                  {enrollment.verifiedAt && (
                    <InfoRow label={t('detail.infoVerified')}><span className="text-fg-muted">{formatDate(enrollment.verifiedAt)}</span></InfoRow>
                  )}
                  {enrollment.lastUsedAt && (
                    <InfoRow label={t('detail.infoLastUsed')}><span className="text-fg-muted">{formatDate(enrollment.lastUsedAt)}</span></InfoRow>
                  )}
                  {enrollment.expiresAt && (
                    <InfoRow label={t('detail.infoExpiresAt')}>
                      <span className="text-fg-muted">{formatDate(enrollment.expiresAt)}</span>
                    </InfoRow>
                  )}

                </dl>
              </CardContent>
            </Card>

            {/* Token + QR + Danger */}
            <div className="space-y-4">

              {/* Proof Token */}
              <Card>
                <CardHeader>
                  <CardTitle>
                    <Tooltip content={t('detail.help.proofToken')}>
                      <span>{t('detail.enrollmentToken')}</span>
                    </Tooltip>
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  <p className="text-xs text-fg-muted">
                    {t('detail.tokenHint')}
                  </p>
                  {enrollment.enrollmentProofToken ? (
                    <>
                      <div className="border-2 border-fg p-3 font-mono text-xs break-all bg-bg leading-relaxed">
                        {tokenVisible ? enrollment.enrollmentProofToken : '•'.repeat(20) + ' …'}
                      </div>
                      <div className="flex gap-2 flex-wrap">
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => setTokenVisible(!tokenVisible)}
                          className="gap-1.5"
                        >
                          {tokenVisible ? <EyeOff className="size-3.5" /> : <Eye className="size-3.5" />}
                          {tokenVisible ? t('detail.hide') : t('detail.reveal')}
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={handleCopyToken}
                          className="gap-1.5"
                        >
                          {tokenCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
                          {tokenCopied ? t('detail.copied') : t('detail.copy')}
                        </Button>
                      </div>
                      {enrollment.enrollmentChallenge != null && (
                        <div className="border-2 border-fg/30 p-3 bg-bg">
                          <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                            <Tooltip content={t('detail.help.bindingChallengeCode')}>
                              <span>{t('create.bindingChallengeCode')}</span>
                            </Tooltip>
                          </p>
                          <p className="font-mono text-2xl font-black tracking-widest">
                            {enrollment.enrollmentChallenge}
                          </p>
                        </div>
                      )}
                    </>
                  ) : (
                    <Alert variant="info">{t('detail.tokenNotAvailable')}</Alert>
                  )}
                </CardContent>
              </Card>

              {/* QR Code */}
              <Card>
                <CardHeader><CardTitle>{t('detail.qrCode')}</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                  <p className="text-xs text-fg-muted">
                    {t('detail.qrCodeHint')}
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
                </CardContent>
              </Card>

              {/* Danger Zone */}
              <Card className="border-error">
                <CardHeader><CardTitle>{t('detail.dangerZone')}</CardTitle></CardHeader>
                <CardContent className="space-y-4">

                  {/* Deactivate / Reactivate */}
                  {enrollment.enrollmentStatus !== 'REVOKED' && (
                    <div className="space-y-2">
                      {enrollment.enrollmentActive ? (
                        <>
                          <p className="text-xs text-fg-muted">
                            {t('detail.deactivateIntro')}
                          </p>
                          <Button
                            variant="destructive"
                            size="sm"
                            className="gap-1.5"
                            isLoading={deactivateMutation.isPending}
                            onClick={() => setLifecycleConfirm('deactivate')}
                          >
                            <PowerOff className="size-3.5" />
                            {t('detail.deactivateEnrollment')}
                          </Button>
                        </>
                      ) : (
                        <>
                          <p className="text-xs text-fg-muted">
                            {t('detail.reactivateIntro')}
                          </p>
                          <Button
                            variant="primary"
                            size="sm"
                            className="gap-1.5"
                            isLoading={reactivateMutation.isPending}
                            onClick={() => setLifecycleConfirm('reactivate')}
                          >
                            <Power className="size-3.5" />
                            {t('detail.reactivateEnrollment')}
                          </Button>
                        </>
                      )}
                      {deactivateMutation.isError && (
                        <Alert variant="error">
                          {getApiErrorMessage(deactivateMutation.error, t('detail.errorDeactivate'))}
                        </Alert>
                      )}
                      {reactivateMutation.isError && (
                        <Alert variant="error">
                          {getApiErrorMessage(reactivateMutation.error, t('detail.errorReactivate'))}
                        </Alert>
                      )}
                    </div>
                  )}

                  {/* Divider */}
                  {enrollment.enrollmentStatus !== 'REVOKED' && (
                    <div className="border-t-2 border-error/20" />
                  )}

                  {/* Revoke */}
                  {enrollment.enrollmentStatus !== 'REVOKED' && (
                    <div className="space-y-2">
                      {!revokeConfirm ? (
                        <>
                          <p className="text-xs text-fg-muted">
                            {t('detail.revokeIntro')}
                          </p>
                          <Button
                            variant="destructive"
                            size="sm"
                            className="gap-1.5"
                            onClick={() => setRevokeConfirm(true)}
                          >
                            <ShieldOff className="size-3.5" />
                            {t('detail.revokeEnrollment')}
                          </Button>
                        </>
                      ) : (
                        <>
                          <p className="text-sm font-bold text-error">
                            {t('detail.revokeConfirmTitle')}
                          </p>
                          <div className="space-y-1.5">
                            <Label htmlFor="revoke-reason">{t('detail.revokeReasonLabel')}</Label>
                            <Input
                              id="revoke-reason"
                              value={revokeReason}
                              onChange={(e) => setRevokeReason(e.target.value)}
                              placeholder={t('detail.revokeReasonPlaceholder')}
                            />
                            <DemoReasonBadges onSelect={setRevokeReason} />
                          </div>
                          {revokeMutation.isError && (
                            <Alert variant="error">
                              {getApiErrorMessage(revokeMutation.error, t('detail.errorRevoke'))}
                            </Alert>
                          )}
                          <div className="flex gap-2">
                            <Button
                              variant="destructive"
                              size="sm"
                              isLoading={revokeMutation.isPending}
                              disabled={revokeReason.length < 10}
                              onClick={() => revokeMutation.mutate({ id: enrollmentId, params: { reason: revokeReason } })}
                              className="gap-1.5"
                            >
                              <ShieldOff className="size-3.5" />
                              {t('detail.yesRevokePermanently')}
                            </Button>
                            <Button variant="ghost" size="sm" onClick={() => { setRevokeConfirm(false); setRevokeReason(''); }}>{t('lifecycleDialog.cancel')}</Button>
                          </div>
                        </>
                      )}
                    </div>
                  )}

                  {enrollment.enrollmentStatus === 'REVOKED' && (
                    <Alert variant="error">{t('detail.revokedAlert')}</Alert>
                  )}

                  {/* Divider */}
                  <div className="border-t-2 border-error/20" />

                  {/* Delete */}
                  {!deleteConfirm ? (
                    <>
                      <p className="text-xs text-fg-muted">
                        {t('detail.deleteIntro')}
                      </p>
                      <Button
                        variant="destructive"
                        size="sm"
                        className="gap-1.5"
                        onClick={() => setDeleteConfirm(true)}
                      >
                        <Trash2 className="size-3.5" />
                        {t('detail.deleteEnrollment')}
                      </Button>
                    </>
                  ) : (
                    <>
                      <p className="text-sm font-bold text-error">
                        {t('detail.deleteConfirmTitle')}
                      </p>
                      {deleteMutation.isError && (
                        <Alert variant="error">
                          {getApiErrorMessage(deleteMutation.error, t('detail.errorDelete'))}
                        </Alert>
                      )}
                      <div className="flex gap-2">
                        <Button
                          variant="destructive"
                          size="sm"
                          isLoading={deleteMutation.isPending}
                          onClick={() => deleteMutation.mutate({ id: enrollmentId })}
                          className="gap-1.5"
                        >
                          <Trash2 className="size-3.5" />
                          {t('detail.yesDelete')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDeleteConfirm(false)}
                        >
                          {t('lifecycleDialog.cancel')}
                        </Button>
                      </div>
                    </>
                  )}
                </CardContent>
              </Card>
            </div>
          </div>
        )}
      </div>

      {/* Deactivate / Reactivate confirm with optional reason */}
      <Dialog
        open={lifecycleConfirm !== null}
        onClose={() => { setLifecycleConfirm(null); setLifecycleReason(''); }}
        title={lifecycleConfirm === 'deactivate' ? t('lifecycleDialog.deactivateTitle') : t('lifecycleDialog.reactivateTitle')}
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-sm text-fg">
            {lifecycleConfirm === 'deactivate'
              ? t('lifecycleDialog.deactivateMessage')
              : t('lifecycleDialog.reactivateMessage')}
          </p>
          <div className="space-y-1.5">
            <Label htmlFor="lifecycle-reason">
              {t('lifecycleDialog.reasonLabel')} <span className="text-fg-muted font-normal">{t('lifecycleDialog.reasonHint')}</span>
            </Label>
            <Input
              id="lifecycle-reason"
              value={lifecycleReason}
              onChange={(e) => setLifecycleReason(e.target.value)}
              placeholder={t('lifecycleDialog.reasonPlaceholder')}
            />
            <DemoReasonBadges onSelect={setLifecycleReason} />
          </div>
          {(lifecycleConfirm === 'deactivate' ? deactivateMutation.isError : reactivateMutation.isError) && (
            <Alert variant="error">
              {getApiErrorMessage(
                lifecycleConfirm === 'deactivate' ? deactivateMutation.error : reactivateMutation.error,
                lifecycleConfirm === 'deactivate' ? t('detail.errorDeactivate') : t('detail.errorReactivate'),
              )}
            </Alert>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="ghost"
              onClick={() => { setLifecycleConfirm(null); setLifecycleReason(''); }}
            >
              {t('lifecycleDialog.cancel')}
            </Button>
            <Button
              variant={lifecycleConfirm === 'deactivate' ? 'destructive' : 'primary'}
              isLoading={
                lifecycleConfirm === 'deactivate'
                  ? deactivateMutation.isPending
                  : reactivateMutation.isPending
              }
              disabled={lifecycleReason.length > 0 && lifecycleReason.length < 10}
              onClick={() => {
                if (lifecycleConfirm === null) return;
                const params = lifecycleReason.trim().length >= 10
                  ? { reason: lifecycleReason.trim() }
                  : undefined;
                if (lifecycleConfirm === 'deactivate') {
                  deactivateMutation.mutate({ id: enrollmentId, params }, {
                    onSuccess: () => {
                      setLifecycleConfirm(null);
                      setLifecycleReason('');
                    },
                  });
                } else {
                  reactivateMutation.mutate({ id: enrollmentId, params }, {
                    onSuccess: () => {
                      setLifecycleConfirm(null);
                      setLifecycleReason('');
                    },
                  });
                }
              }}
            >
              {lifecycleConfirm === 'deactivate' ? (
                <>
                  <PowerOff className="size-3.5 mr-1.5" />
                  {t('lifecycleDialog.deactivate')}
                </>
              ) : (
                <>
                  <Power className="size-3.5 mr-1.5" />
                  {t('lifecycleDialog.reactivate')}
                </>
              )}
            </Button>
          </div>
        </div>
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
          <div className="space-y-1.5">
            <Label htmlFor="edit-name">{t('detail.editName')}</Label>
            <Input
              id="edit-name"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="border-2 border-fg/30"
              maxLength={255}
              autoComplete="off"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="edit-email">{t('detail.editContactEmail')}</Label>
            <Input
              id="edit-email"
              type="email"
              value={editEmail}
              onChange={(e) => setEditEmail(e.target.value)}
              className="border-2 border-fg/30"
              maxLength={255}
              autoComplete="off"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="edit-user-id">{t('detail.editUserIdentifier')}</Label>
            <Input
              id="edit-user-id"
              value={editUserId}
              onChange={(e) => setEditUserId(e.target.value)}
              className="border-2 border-fg/30"
              maxLength={255}
              autoComplete="off"
            />
          </div>
          <label className="flex items-start gap-3 cursor-pointer select-none p-3 border-2 border-fg/20">
            <input
              type="checkbox"
              className="mt-0.5 size-4 accent-accent"
              checked={editChallenge}
              onChange={(e) => setEditChallenge(e.target.checked)}
            />
            <span className="text-sm font-bold">{t('detail.editChallengeRequired')}</span>
          </label>
          <div className="space-y-1.5">
            <Label htmlFor="edit-expires">{t('detail.editExpiresAt')}</Label>
            <p className="text-xs text-fg-muted">{t('detail.editExpiresHint')}</p>
            <Input
              id="edit-expires"
              type="datetime-local"
              value={editExpiresLocal}
              onChange={(e) => setEditExpiresLocal(e.target.value)}
              className="border-2 border-fg/30"
            />
          </div>
          {updateMutation.isError && (
            <Alert variant="error">
              {getApiErrorMessage(
                updateMutation.error,
                updateMutation.error instanceof ApiError && updateMutation.error.status === 409
                  ? t('detail.errorConflict')
                  : t('detail.errorUpdate'),
              )}
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
              onClick={handleSaveMetadata}
              isLoading={updateMutation.isPending}
              disabled={!editName.trim()}
            >
              {t('detail.editSave')}
            </Button>
          </div>
        </div>
      </Dialog>

      {enrollment && (
        <TestAuthDialog
          open={testAuthOpen}
          onClose={() => setTestAuthOpen(false)}
          enrollment={enrollment}
        />
      )}
    </AppShell>
  );
}
