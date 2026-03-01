import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Check, Copy, Eye, EyeOff, QrCode, Trash2, Zap } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { useIntegrations } from '@/hooks/use-integrations';
import { ApiError, api, fetchBlobUrl } from '@/lib/api-client';
import { formatChallengeCode, formatCountdown, formatDate } from '@/lib/utils';
import type { AuthAttemptCreateRequest, AuthAttemptCreateResponse } from '@/types/api';
import type { AuthAttempt, AuthAttemptStatus, Enrollment } from '@/types/models';

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

const FINAL_STATUSES: AuthAttemptStatus[] = ['ACCEPTED', 'REJECTED', 'EXPIRED', 'INVALID'];

function authStatusVariant(s: AuthAttemptStatus): 'success' | 'error' | 'warning' | 'muted' {
  if (s === 'ACCEPTED') return 'success';
  if (s === 'REJECTED' || s === 'INVALID') return 'error';
  if (s === 'READ') return 'warning';
  return 'muted';
}

function authStatusLabel(s: AuthAttemptStatus): string {
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
  enrollment: Enrollment;
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
  const { data: liveStatus } = useQuery({
    queryKey: ['test-auth', createdAttempt?.authAttemptId],
    queryFn: () =>
      api.get<AuthAttempt>(`/api/v1/auth-attempts/${createdAttempt!.authAttemptId}`),
    enabled: step === 'live' && createdAttempt !== null,
    refetchInterval: isFinal ? false : 3_000,
  });

  // Transition to done when status becomes final
  useEffect(() => {
    if (!liveStatus || isFinal) return;
    if (FINAL_STATUSES.includes(liveStatus.authAttemptStatus)) {
      setIsFinal(true);
      setDoneReason(liveStatus.authAttemptStatus.toLowerCase() as DoneReason);
      setStep('done');
    }
  }, [liveStatus, isFinal]);

  const createMutation = useMutation({
    mutationFn: (req: AuthAttemptCreateRequest) =>
      api.post<AuthAttemptCreateResponse>('/api/v1/auth-attempts', req),
    onSuccess: (data) => {
      setCreatedAttempt(data);
      setCountdown(data.timeoutSeconds);
      setStep('live');
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () =>
      api.post<AuthAttempt>(`/api/v1/auth-attempts/${createdAttempt!.authAttemptId}/cancel`, {}),
    onSuccess: () => {
      setIsFinal(true);
      setDoneReason('cancelled');
      setStep('done');
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
    <Dialog open={open} onClose={handleClose} title="Test Authentication" size="md">

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
              {createMutation.error instanceof ApiError
                ? createMutation.error.message
                : 'Failed to create auth attempt.'}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={handleClose}>Cancel</Button>
            <Button
              isLoading={createMutation.isPending}
              onClick={() =>
                createMutation.mutate({
                  enrollmentId: enrollment.enrollmentId,
                  challengeRequested,
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
              onClick={() => cancelMutation.mutate()}
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
  const [testAuthOpen, setTestAuthOpen] = useState(false);

  const { lookup } = useIntegrations();

  const { data: enrollment, isLoading } = useQuery({
    queryKey: ['enrollment', enrollmentId],
    queryFn: () => api.get<Enrollment>(`/api/v1/enrollments/${enrollmentId}`),
    enabled: !isNaN(enrollmentId),
  });

  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/api/v1/enrollments/${enrollmentId}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
      void queryClient.invalidateQueries({ queryKey: ['stats'] });
      navigate('/enrollments');
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
    <AppShell title={enrollment?.enrollmentName ?? 'Enrollment Detail'}>
      <div className="space-y-6">

        {/* Back + Test Auth */}
        <div className="flex items-center justify-between">
          <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="gap-1.5 -ml-2">
            <ArrowLeft className="size-3.5" />
            Back
          </Button>
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
                    <button
                      className="font-mono text-sm text-accent hover:underline"
                      onClick={() => navigate(`/integrations/${enrollment.integrationId}`)}
                    >
                      {lookup.get(enrollment.integrationId) ?? `#${enrollment.integrationId}`}
                    </button>
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
                  {enrollment.createdAt && (
                    <InfoRow label="Created"><span className="text-fg-muted">{formatDate(enrollment.createdAt)}</span></InfoRow>
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
                <CardContent className="space-y-3">
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
                          {deleteMutation.error instanceof ApiError
                            ? deleteMutation.error.message
                            : 'Failed to delete enrollment.'}
                        </Alert>
                      )}
                      <div className="flex gap-2">
                        <Button
                          variant="destructive"
                          size="sm"
                          isLoading={deleteMutation.isPending}
                          onClick={() => deleteMutation.mutate()}
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
