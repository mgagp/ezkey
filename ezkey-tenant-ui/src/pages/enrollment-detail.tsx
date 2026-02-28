import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Check, Copy, Eye, EyeOff, QrCode, Trash2 } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useIntegrations } from '@/hooks/use-integrations';
import { ApiError, api, fetchBlobUrl } from '@/lib/api-client';
import { formatDate } from '@/lib/utils';
import type { Enrollment } from '@/types/models';

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

        {/* Back */}
        <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="gap-1.5 -ml-2">
          <ArrowLeft className="size-3.5" />
          Back
        </Button>

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
    </AppShell>
  );
}
