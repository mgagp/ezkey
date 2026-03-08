import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Check, Copy, Eye, EyeOff, Power, PowerOff, QrCode, ShieldOff, Trash2, Zap } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useToast } from '@/context/toast-context';
import { useIntegrations } from '@/hooks/use-integrations';
import { fetchBlobUrl, getApiErrorMessage } from '@/lib/api-client';
import { formatChallengeCode, formatCountdown, formatDate } from '@/lib/utils';
import { useCancel, useCreate2, useGetById2 } from '@/generated/admin-api/auth-attempts/auth-attempts';
import {
  useDeactivate,
  useDelete,
  useGetById,
  useReactivate,
  useRevoke,
} from '@/generated/admin-api/enrollments/enrollments';
import type { AuthAttemptDto, AuthAttemptDtoAuthAttemptStatus, EnrollmentResponseDto } from '@/generated/admin-api/model';

// ── Local types (not yet in OpenAPI spec) ────────────────────────────────────

/** Response from POST /api/v1/auth-attempts — not yet specified in the OpenAPI schema. */
interface AuthAttemptCreateResponse {
  authAttemptId: number;
  authAttemptChallenge?: number;
  timeoutSeconds: number;
  expiresAt: string;
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

function authStatusLabel(s: AuthAttemptDtoAuthAttemptStatus): string {
  switch (s) {
    case 'PENDING': return 'Waiting for device…';
    case 'READ':    return 'Device is reading…';
    case 'ACCEPTED': return 'Accepted';
    case 'REJECTED': return 'Rejected';
    case 'EXPIRED':  return 'Expired';
    case 'INVALID':  return 'Invalid';
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
  type Step = 'configure' | 'live' | 'done';
  type DoneReason = 'accepted' | 'rejected' | 'expired' | 'invalid' | 'cancelled';

  const [step, setStep] = useState<Step>('configure');
  const [challengeRequested, setChallengeRequested] = useState(
    enrollment.authAttemptChallengeRequired ?? false,
  );
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
    setCreatedAttempt(null);
    setIsFinal(false);
    setDoneReason(null);
    setCountdown(0);
    createMutation.reset();
    cancelMutation.reset();
  };

  const handleClose = () => { resetState(); onClose(); };

  return (
    <Dialog open={open} onClose={handleClose} title="Test Authentication" size="md" dismissible={false}>

      {/* ── Step 1: Configure ── */}
      {step === 'configure' && (
        <div className="space-y-4">
          <p className="text-sm text-fg">
            Trigger a live authentication request for enrollment{' '}
            <strong>{enrollment.enrollmentName}</strong>. The end-user will see a pending
            request on their EZKey mobile app.
          </p>

          <label className="flex items-start gap-3 cursor-pointer select-none p-3 border-2 border-fg/20 hover:border-fg/40 transition-colors">
            <input
              type="checkbox"
              className="mt-0.5 size-4 accent-accent"
              checked={challengeRequested}
              onChange={(e) => setChallengeRequested(e.target.checked)}
            />
            <div>
              <p className="text-sm font-bold">Request challenge code</p>
              <p className="text-xs text-fg-muted mt-0.5">
                A 2-digit code is displayed here. The user must confirm the matching code on
                their device.
              </p>
            </div>
          </label>

          {createMutation.isError && (
            <Alert variant="error">
              {getApiErrorMessage(createMutation.error, 'Failed to create auth attempt.')}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={handleClose}>Cancel</Button>
            <Button
              isLoading={createMutation.isPending}
              onClick={() =>
                createMutation.mutate({
                  data: {
                    enrollmentId: enrollment.enrollmentId,
                    challengeRequested,
                  },
                })
              }
              className="gap-1.5"
            >
              <Zap className="size-3.5" />
              Launch Test
            </Button>
          </div>
        </div>
      )}

      {/* ── Step 2: Live ── */}
      {step === 'live' && createdAttempt && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="border-2 border-fg/30 p-3">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                Attempt ID
              </p>
              <p className="font-mono font-black text-xl">{createdAttempt.authAttemptId}</p>
            </div>
            <div className="border-2 border-fg/30 p-3">
              <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                Expires in
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
                Challenge Code
              </p>
              <p className="font-mono text-5xl font-black tracking-[0.5em] text-accent">
                {formatChallengeCode(createdAttempt.authAttemptChallenge)}
              </p>
              <p className="text-xs text-fg-muted mt-2">
                Show this to the user — they must confirm the matching code on their device.
              </p>
            </div>
          )}

          <div className="flex items-center gap-2 py-1">
            <span className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
              Status
            </span>
            {liveStatus ? (
              <Badge variant={authStatusVariant(liveStatus.authAttemptStatus)}>
                {authStatusLabel(liveStatus.authAttemptStatus)}
              </Badge>
            ) : (
              <span className="size-3.5 border-2 border-fg/30 border-t-fg rounded-full animate-spin inline-block" />
            )}
          </div>

          <div className="flex justify-end pt-2">
            <Button
              variant="ghost"
              size="sm"
              isLoading={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate({ id: createdAttempt!.authAttemptId! })}
              className="gap-1 text-error hover:bg-error/10 border border-error/30 hover:border-error"
            >
              Cancel Attempt
            </Button>
          </div>
        </div>
      )}

      {/* ── Step 3: Done ── */}
      {step === 'done' && (
        <div className="space-y-4">
          {doneReason === 'accepted' && (
            <Alert variant="success">
              <strong>Authentication accepted!</strong> The user confirmed the request on their
              device. The enrollment is working correctly.
            </Alert>
          )}
          {doneReason === 'rejected' && (
            <Alert variant="error">
              <strong>Authentication rejected.</strong> The user declined the request on their
              device.
            </Alert>
          )}
          {doneReason === 'expired' && (
            <Alert variant="warning">
              <strong>Timed out.</strong> No response was received before the attempt expired.
            </Alert>
          )}
          {doneReason === 'invalid' && (
            <Alert variant="error">
              <strong>Invalid attempt.</strong> The request was marked invalid — this may
              indicate a device binding issue.
            </Alert>
          )}
          {doneReason === 'cancelled' && (
            <Alert variant="info">
              <strong>Cancelled.</strong> The test authentication was cancelled.
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={resetState}>
              Run Another Test
            </Button>
            <Button onClick={handleClose}>Close</Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function EnrollmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const enrollmentId = Number(id);

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

  const { toast } = useToast();
  const { lookup } = useIntegrations();

  const { data: enrollment, isLoading } = useGetById<EnrollmentResponseDto>(
    isNaN(enrollmentId) ? 0 : enrollmentId,
    { query: { enabled: !isNaN(enrollmentId) } },
  );

  const deleteMutation = useDelete({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        void queryClient.invalidateQueries({ queryKey: ['stats'] });
        toast('Enrollment deleted.');
        navigate('/enrollments');
      },
    },
  });

  const deactivateMutation = useDeactivate({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['enrollment', enrollmentId] });
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast('Enrollment deactivated.');
      },
    },
  });

  const reactivateMutation = useReactivate({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['enrollment', enrollmentId] });
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast('Enrollment reactivated.');
      },
    },
  });

  const revokeMutation = useRevoke({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['enrollment', enrollmentId] });
        void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
        toast('Enrollment permanently revoked.', 'error');
        setRevokeConfirm(false);
        setRevokeReason('');
      },
    },
  });

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
      title={enrollment?.enrollmentName ?? 'Enrollment Detail'}
      breadcrumb={[
        { label: 'Enrollments', path: '/enrollments' },
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
              Test Authentication
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
              <CardHeader><CardTitle>Enrollment Info</CardTitle></CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label="ID"><span className="font-mono">{enrollment.enrollmentId}</span></InfoRow>
                  <InfoRow label="Name"><span className="font-medium">{enrollment.enrollmentName}</span></InfoRow>
                  <InfoRow label="Status"><EnrollmentStatusBadge status={enrollment.enrollmentStatus} /></InfoRow>
                  <InfoRow label="Active">
                    <Badge variant={enrollment.enrollmentActive ? 'success' : 'muted'}>
                      {enrollment.enrollmentActive ? 'Yes' : 'No'}
                    </Badge>
                  </InfoRow>
                  <InfoRow label="Integration">
                    {(enrollment as { isSystemIntegration?: boolean }).isSystemIntegration ? (
                      <span className="font-medium">
                        {(enrollment as { integrationName?: string }).integrationName ??
                          lookup.get(enrollment.integrationId!) ??
                          `#${enrollment.integrationId ?? '?'}`}
                      </span>
                    ) : (
                      <button
                        className="font-mono text-sm text-accent hover:underline"
                        onClick={() => navigate(`/integrations/${enrollment.integrationId}`)}
                      >
                        {(enrollment as { integrationName?: string }).integrationName ??
                          lookup.get(enrollment.integrationId!) ??
                          `#${enrollment.integrationId ?? '?'}`}
                      </button>
                    )}
                  </InfoRow>
                  <InfoRow label="Challenge">
                    <Badge variant={enrollment.authAttemptChallengeRequired ? 'warning' : 'muted'}>
                      {enrollment.authAttemptChallengeRequired ? 'Required' : 'Not required'}
                    </Badge>
                  </InfoRow>
                  {enrollment.contactEmail && (
                    <InfoRow label="Contact Email">{enrollment.contactEmail}</InfoRow>
                  )}
                  {enrollment.userIdentifier && (
                    <InfoRow label="User ID">{enrollment.userIdentifier}</InfoRow>
                  )}
                  {enrollment.verifiedAt && (
                    <InfoRow label="Verified"><span className="text-fg-muted">{formatDate(enrollment.verifiedAt)}</span></InfoRow>
                  )}
                  {enrollment.lastUsedAt && (
                    <InfoRow label="Last Used"><span className="text-fg-muted">{formatDate(enrollment.lastUsedAt)}</span></InfoRow>
                  )}

                </dl>
              </CardContent>
            </Card>

            {/* Token + QR + Danger */}
            <div className="space-y-4">

              {/* Proof Token */}
              <Card>
                <CardHeader><CardTitle>Enrollment Token</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                  <p className="text-xs text-fg-muted">
                    Share this token with the user to set up their EZKey mobile app.
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
                          {tokenVisible ? 'Hide' : 'Reveal'}
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={handleCopyToken}
                          className="gap-1.5"
                        >
                          {tokenCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
                          {tokenCopied ? 'Copied!' : 'Copy'}
                        </Button>
                      </div>
                      {enrollment.enrollmentChallenge != null && (
                        <div className="border-2 border-fg/30 p-3 bg-bg">
                          <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
                            Binding Challenge Code
                          </p>
                          <p className="font-mono text-2xl font-black tracking-widest">
                            {enrollment.enrollmentChallenge}
                          </p>
                        </div>
                      )}
                    </>
                  ) : (
                    <Alert variant="info">Token is not available for this enrollment.</Alert>
                  )}
                </CardContent>
              </Card>

              {/* QR Code */}
              <Card>
                <CardHeader><CardTitle>QR Code</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                  <p className="text-xs text-fg-muted">
                    User can scan this QR code with the EZKey mobile app.
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
                </CardContent>
              </Card>

              {/* Danger Zone */}
              <Card className="border-error">
                <CardHeader><CardTitle>Danger Zone</CardTitle></CardHeader>
                <CardContent className="space-y-4">

                  {/* Deactivate / Reactivate */}
                  {enrollment.enrollmentStatus !== 'REVOKED' && (
                    <div className="space-y-2">
                      {enrollment.enrollmentActive ? (
                        <>
                          <p className="text-xs text-fg-muted">
                            Deactivating temporarily prevents authentication. The enrollment can be reactivated later.
                          </p>
                          <Button
                            variant="destructive"
                            size="sm"
                            className="gap-1.5"
                            isLoading={deactivateMutation.isPending}
                            onClick={() => setLifecycleConfirm('deactivate')}
                          >
                            <PowerOff className="size-3.5" />
                            Deactivate Enrollment
                          </Button>
                        </>
                      ) : (
                        <>
                          <p className="text-xs text-fg-muted">
                            This enrollment is inactive. Reactivating will restore authentication capabilities.
                          </p>
                          <Button
                            variant="primary"
                            size="sm"
                            className="gap-1.5"
                            isLoading={reactivateMutation.isPending}
                            onClick={() => setLifecycleConfirm('reactivate')}
                          >
                            <Power className="size-3.5" />
                            Reactivate Enrollment
                          </Button>
                        </>
                      )}
                      {deactivateMutation.isError && (
                        <Alert variant="error">
                          {getApiErrorMessage(deactivateMutation.error, 'Failed to deactivate.')}
                        </Alert>
                      )}
                      {reactivateMutation.isError && (
                        <Alert variant="error">
                          {getApiErrorMessage(reactivateMutation.error, 'Failed to reactivate.')}
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
                            <strong className="text-error">Irreversible.</strong> Revoking permanently disables this enrollment. A new enrollment must be created for re-enrolment.
                          </p>
                          <Button
                            variant="destructive"
                            size="sm"
                            className="gap-1.5"
                            onClick={() => setRevokeConfirm(true)}
                          >
                            <ShieldOff className="size-3.5" />
                            Revoke Enrollment
                          </Button>
                        </>
                      ) : (
                        <>
                          <p className="text-sm font-bold text-error">
                            This action is PERMANENT. The enrollment will be irreversibly revoked.
                          </p>
                          <div className="space-y-1.5">
                            <Label htmlFor="revoke-reason">Reason (min 10 chars, required for audit)</Label>
                            <Input
                              id="revoke-reason"
                              value={revokeReason}
                              onChange={(e) => setRevokeReason(e.target.value)}
                              placeholder="Security incident — enrollment compromised..."
                            />
                          </div>
                          {revokeMutation.isError && (
                            <Alert variant="error">
                              {getApiErrorMessage(revokeMutation.error, 'Failed to revoke.')}
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
                              Yes, Revoke Permanently
                            </Button>
                            <Button variant="ghost" size="sm" onClick={() => { setRevokeConfirm(false); setRevokeReason(''); }}>Cancel</Button>
                          </div>
                        </>
                      )}
                    </div>
                  )}

                  {enrollment.enrollmentStatus === 'REVOKED' && (
                    <Alert variant="error">This enrollment has been permanently revoked.</Alert>
                  )}

                  {/* Divider */}
                  <div className="border-t-2 border-error/20" />

                  {/* Delete */}
                  {!deleteConfirm ? (
                    <>
                      <p className="text-xs text-fg-muted">
                        Deleting an enrollment permanently removes the device association. The user will no longer be able to authenticate.
                      </p>
                      <Button
                        variant="destructive"
                        size="sm"
                        className="gap-1.5"
                        onClick={() => setDeleteConfirm(true)}
                      >
                        <Trash2 className="size-3.5" />
                        Delete Enrollment
                      </Button>
                    </>
                  ) : (
                    <>
                      <p className="text-sm font-bold text-error">
                        Are you sure? This action cannot be undone.
                      </p>
                      {deleteMutation.isError && (
                        <Alert variant="error">
                          {getApiErrorMessage(deleteMutation.error, 'Failed to delete enrollment.')}
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
                          Yes, Delete
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDeleteConfirm(false)}
                        >
                          Cancel
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
        title={lifecycleConfirm === 'deactivate' ? 'Deactivate Enrollment' : 'Reactivate Enrollment'}
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-sm text-fg">
            {lifecycleConfirm === 'deactivate'
              ? 'Temporarily deactivate this enrollment? The user will not be able to authenticate until reactivated.'
              : 'Reactivate this enrollment? The user will be able to authenticate again.'}
          </p>
          <div className="space-y-1.5">
            <Label htmlFor="lifecycle-reason">
              Reason <span className="text-fg-muted font-normal">(min 10 chars, for audit trail)</span>
            </Label>
            <Input
              id="lifecycle-reason"
              value={lifecycleReason}
              onChange={(e) => setLifecycleReason(e.target.value)}
              placeholder="Justification for this action..."
            />
          </div>
          {(lifecycleConfirm === 'deactivate' ? deactivateMutation.isError : reactivateMutation.isError) && (
            <Alert variant="error">
              {getApiErrorMessage(
                lifecycleConfirm === 'deactivate' ? deactivateMutation.error : reactivateMutation.error,
                lifecycleConfirm === 'deactivate' ? 'Failed to deactivate.' : 'Failed to reactivate.',
              )}
            </Alert>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="ghost"
              onClick={() => { setLifecycleConfirm(null); setLifecycleReason(''); }}
            >
              Cancel
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
                  Deactivate
                </>
              ) : (
                <>
                  <Power className="size-3.5 mr-1.5" />
                  Reactivate
                </>
              )}
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
