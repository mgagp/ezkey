import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Check, Copy, Eye, EyeOff } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';

/**
 * Enrollment code (#1494): the exact JSON the enrollment QR code holds, composed by the Admin API
 * (`QrCodePayloadService`), for an authenticator that cannot scan.
 *
 * The parent renders it only where the proof token is shown, so it inherits the token's visibility
 * (CREATED only, audited reveal for an administrator). A null code with a token means the instance
 * has no public Auth API address: there is nothing honest to copy, and the block says why.
 */
export function EnrollmentCodeBlock({
  code,
  testId = 'enrollment-code',
  showLabel = true,
}: {
  code: string | null | undefined;
  testId?: string;
  /** False when the surrounding card already titles the block. */
  showLabel?: boolean;
}) {
  const { t } = useTranslation('common');
  const [visible, setVisible] = useState(false);
  const [copied, setCopied] = useState(false);

  if (code == null || code === '') {
    return (
      <Alert variant="info" data-testid={`${testId}-unavailable`}>
        {t('enrollmentCode.unavailable')}
      </Alert>
    );
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore clipboard API errors
    }
  };

  return (
    <div className="space-y-1.5" data-testid={testId}>
      {showLabel && (
        <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">
          {t('enrollmentCode.label')}
        </p>
      )}
      <p className="text-xs text-fg-muted leading-snug">{t('enrollmentCode.hint')}</p>
      <div
        className="border-2 border-fg p-2 font-mono text-[11px] break-all bg-bg leading-relaxed max-h-32 overflow-y-auto"
        data-testid={`${testId}-value`}
      >
        {visible ? code : '•'.repeat(20) + ' …'}
      </div>
      <div className="flex gap-2 flex-wrap">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => setVisible(!visible)}
          className="gap-1.5"
          data-testid={`${testId}-reveal`}
        >
          {visible ? <EyeOff className="size-3.5" /> : <Eye className="size-3.5" />}
          {visible ? t('enrollmentCode.hide') : t('enrollmentCode.show')}
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => void handleCopy()}
          className="gap-1.5"
          data-testid={`${testId}-copy`}
        >
          {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
          {copied ? t('enrollmentCode.copied') : t('enrollmentCode.copy')}
        </Button>
      </div>
    </div>
  );
}
