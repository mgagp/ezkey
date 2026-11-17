import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Check, Copy, QrCode } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useGetAdminOnboarding } from '@/generated/admin-api/administrator-provisioning/administrator-provisioning';
import type { AdminResponseDto } from '@/generated/admin-api/model';
import { fetchBlobUrl } from '@/lib/api-client';

/** UI-facing shape of the onboarding response (the API returns GetAdminOnboarding200). */
interface AdminOnboardingShape {
  enrollmentProofToken?: string | null;
  enrollmentChallenge?: number | null;
}

/**
 * Bind card on the administrator record (#1313). The record is the single place that reveals an
 * administrator's bind material.
 *
 * Nothing secret loads when the record opens: the card decides to appear from the admin response
 * (see canShowOnboardingCredentials), and the token, challenge and QR are fetched only when the
 * operator clicks the reveal button. Each reveal writes an ADMIN_BIND_MATERIAL_REVEALED audit row
 * on the server.
 *
 * The record page mounts it under a component keyed by adminId, so moving to another administrator
 * resets the reveal.
 */
export function AdminBindCard({
  adminId,
  enrollmentStatus,
}: {
  adminId: number;
  enrollmentStatus: AdminResponseDto['enrollmentStatus'];
}) {
  const { t } = useTranslation(['admins', 'enrollments']);
  const [revealed, setRevealed] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
  const [qrLoading, setQrLoading] = useState(false);
  const [tokenCopied, setTokenCopied] = useState(false);
  const qrExpected = enrollmentStatus === 'CREATED';

  const { data: rawOnboarding, isFetching, isError } = useGetAdminOnboarding<AdminOnboardingShape>(
    adminId,
    // #1266: always ask the server, so a cached token never outlives the state that allowed it.
    { query: { enabled: revealed, staleTime: 0, gcTime: 0 } },
  );
  const onboarding = revealed ? (rawOnboarding as unknown as AdminOnboardingShape | undefined) : undefined;
  const proofToken = onboarding?.enrollmentProofToken ?? null;
  const challenge = onboarding?.enrollmentChallenge ?? null;

  useEffect(() => {
    return () => {
      if (qrCodeUrl) URL.revokeObjectURL(qrCodeUrl);
    };
  }, [qrCodeUrl]);

  const hide = () => {
    setQrCodeUrl(null);
    setRevealed(false);
  };

  const reveal = async () => {
    setRevealed(true);
    if (!qrExpected) return;
    setQrLoading(true);
    try {
      setQrCodeUrl(await fetchBlobUrl(`/api/v1/admins/${adminId}/onboarding/qrcode`));
    } catch {
      // The enrollment moved on (409): the JSON response says what is still available.
    } finally {
      setQrLoading(false);
    }
  };

  const handleCopyToken = async () => {
    if (proofToken == null || proofToken === '') return;
    try {
      await navigator.clipboard.writeText(proofToken);
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 2000);
    } catch {
      /* ignore clipboard API errors */
    }
  };

  let revealLabel = t('bindCard.showCode');
  if (qrExpected) revealLabel = revealed ? t('bindCard.hideQr') : t('bindCard.showQr');
  else if (revealed) revealLabel = t('bindCard.hideCode');

  return (
    <Card data-testid="admin-bind-card">
      <CardHeader>
        <CardTitle>{t('bindCard.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-sm text-fg" data-testid="admin-bind-in-person">{t('bindCard.inPerson')}</p>
        <p className="text-xs text-fg-muted">{t('bindCard.revealAudited')}</p>
        <Button
          variant="secondary"
          size="sm"
          className="gap-1.5"
          isLoading={qrLoading || (revealed && isFetching)}
          onClick={() => (revealed ? hide() : void reveal())}
          data-testid="admin-bind-reveal"
        >
          <QrCode className="size-3.5" />
          {revealLabel}
        </Button>

        {revealed && isError && <Alert variant="error">{t('bindCard.errorLoad')}</Alert>}

        {qrCodeUrl && (
          <div className="border-2 border-fg p-3 w-fit" data-testid="admin-bind-qr">
            <img src={qrCodeUrl} alt={t('bindCard.qrCodeAlt')} className="size-48" />
          </div>
        )}

        {challenge != null && (
          <div className="border-2 border-fg/30 p-3 bg-bg">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted mb-1">
              {t('bindCard.bindingChallenge')}
            </p>
            {proofToken == null && (
              <Alert variant="warning" className="mb-2" data-testid="admin-bind-bound-warning">
                {t('enrollments:detail.boundChallengeWarning')}
              </Alert>
            )}
            <p className="font-mono text-2xl font-black tracking-widest" data-testid="admin-bind-challenge">
              {challenge}
            </p>
          </div>
        )}

        {proofToken != null && (
          <div className="space-y-1.5" data-testid="admin-bind-token">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
              {t('bindCard.proofToken')}
            </p>
            <div className="border-2 border-fg p-3 font-mono text-xs break-all bg-bg leading-relaxed">
              {proofToken}
            </div>
            <Button variant="secondary" size="sm" onClick={() => void handleCopyToken()} className="gap-1.5">
              {tokenCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
              {tokenCopied ? t('onboarding.copied') : t('bindCard.copyToken')}
            </Button>
          </div>
        )}

        {onboarding && proofToken == null && challenge == null && (
          <Alert variant="info">{t('bindCard.noLongerAvailable')}</Alert>
        )}
      </CardContent>
    </Card>
  );
}
