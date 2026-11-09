import { useTranslation } from 'react-i18next';
import type { SelectHTMLAttributes } from 'react';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';

export type PendingAttemptPolicyValue = 'REPLACE' | 'REFUSE';

type PendingAttemptPolicyFieldProps = {
  id: string;
} & SelectHTMLAttributes<HTMLSelectElement>;

/** Per-integration pending-attempt policy. Help text stays generic: no connector names. */
export function PendingAttemptPolicyField({ id, ...selectProps }: PendingAttemptPolicyFieldProps) {
  const { t } = useTranslation('integrations');
  return (
    <div>
      <Label htmlFor={id}>{t('pendingAttemptPolicy.label')}</Label>
      <Select id={id} className="mt-1" {...selectProps}>
        <option value="REPLACE">{t('pendingAttemptPolicy.replace')}</option>
        <option value="REFUSE">{t('pendingAttemptPolicy.refuse')}</option>
      </Select>
      <p data-testid="pending-attempt-policy-help" className="text-[10px] text-fg-muted mt-0.5">
        {t('pendingAttemptPolicy.help')}
      </p>
    </div>
  );
}
