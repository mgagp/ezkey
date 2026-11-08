import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Mail } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ApiError } from '@/lib/api-client';
import {
  qrEmailLocale,
  qrEmailNoticeForOutcome,
  qrEmailNoticeForRefusal,
  type QrEmailNotice,
} from '@/lib/qr-email-delivery';
import { useCapability, useSend } from '@/generated/admin-api/enrollments/enrollments';
import type { QrDeliveryCapabilityDto, QrDeliveryResponseDto } from '@/generated/admin-api/model';

/**
 * "Send by email" for an enrollment QR (#1206 step 5). Shown only when a delivery connector is
 * usable; the QR stays on screen whatever happens. The challenge is never sent.
 */
export function QrEmailDelivery({
  enrollmentId,
  contactEmail,
  onEditContactEmail,
}: {
  enrollmentId: number;
  contactEmail: string | null | undefined;
  onEditContactEmail: () => void;
}) {
  const { t, i18n } = useTranslation('enrollments');
  const locale = qrEmailLocale(i18n.language);
  const [notice, setNotice] = useState<{ notice: QrEmailNotice; recipient?: string; code?: string } | null>(null);

  const { data: rawCapability } = useCapability({ query: { staleTime: 10_000, retry: false } });
  const capability = rawCapability as unknown as QrDeliveryCapabilityDto | undefined;

  const sendMutation = useSend({
    mutation: {
      onSuccess: (raw) => {
        const result = raw as unknown as QrDeliveryResponseDto;
        setNotice({
          notice: qrEmailNoticeForOutcome(result.outcome),
          recipient: result.recipient,
          code: result.code,
        });
      },
      onError: (err) => {
        const code =
          err instanceof ApiError
            ? ((err.problemDetail as { code?: string } | null)?.code ?? undefined)
            : undefined;
        setNotice({ notice: qrEmailNoticeForRefusal(code), code });
      },
    },
  });

  if (!capability?.available) return null;
  const offersLocale = capability.locales?.includes(locale) ?? false;

  return (
    <div className="space-y-2 border-t-2 border-fg/10 pt-3" data-testid="enrollment-email-delivery">
      <div className="flex items-center gap-2">
        <p className="text-sm font-bold">{t('detail.emailDelivery.title')}</p>
        {capability.testMode && (
          <Badge variant="warning" data-testid="enrollment-email-test-mode">
            {t('detail.emailDelivery.testMode')}
          </Badge>
        )}
      </div>
      {contactEmail ? (
        <>
          <p className="text-xs text-fg-muted">
            {t('detail.emailDelivery.languageNote', {
              language: t(`detail.emailDelivery.language.${locale}`),
            })}
          </p>
          <Button
            variant="secondary"
            size="sm"
            className="gap-1.5"
            data-testid="enrollment-email-send"
            isLoading={sendMutation.isPending}
            disabled={!offersLocale}
            onClick={() => {
              setNotice(null);
              sendMutation.mutate({ id: enrollmentId, data: { locale } });
            }}
          >
            <Mail className="size-3.5" />
            {t('detail.emailDelivery.send', { email: contactEmail })}
          </Button>
        </>
      ) : (
        <div className="space-y-2">
          <p className="text-xs text-fg-muted" data-testid="enrollment-email-missing">
            {t('detail.emailDelivery.noContactEmail')}
          </p>
          <Button variant="secondary" size="sm" onClick={onEditContactEmail}>
            {t('detail.editContactEmailOnly')}
          </Button>
        </div>
      )}
      {notice && (
        <Alert variant={notice.notice.variant} data-testid={`enrollment-email-${notice.notice.variant}`}>
          {t(`detail.emailDelivery.${notice.notice.key}`, {
            email: notice.recipient ?? contactEmail ?? '',
            code: notice.code ?? '',
          })}
        </Alert>
      )}
    </div>
  );
}
