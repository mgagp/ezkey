import { useTranslation } from 'react-i18next';
import type { SelectHTMLAttributes } from 'react';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';

export type PendingAttemptPolicyValue = 'REPLACE' | 'REFUSE';

type PendingAttemptPolicyFieldProps = {
  id: string;
  /** Shown when the setting cannot be changed, such as on the system integration. */
  hint?: string;
} & SelectHTMLAttributes<HTMLSelectElement>;

/** Per-integration pending-attempt policy. Help text stays generic: no connector names. */
export function PendingAttemptPolicyField({
  id,
  hint,
  ...selectProps
}: PendingAttemptPolicyFieldProps) {
  const { t } = useTranslation('integrations');
  const helpId = `${id}-help`;
  const hintId = hint ? `${id}-lock` : undefined;
  const describedBy = hintId ?? helpId;
  return (
    <div>
      <Label htmlFor={id}>{t('pendingAttemptPolicy.label')}</Label>
      <Select id={id} className="mt-1" {...selectProps} aria-describedby={describedBy}>
        <option value="REPLACE">{t('pendingAttemptPolicy.replace')}</option>
        <option value="REFUSE">{t('pendingAttemptPolicy.refuse')}</option>
      </Select>
      {hint ? (
        <p id={hintId} data-testid="pending-attempt-policy-lock" className="text-[10px] text-fg-muted mt-0.5">
          {hint}
        </p>
      ) : (
        <p id={helpId} data-testid="pending-attempt-policy-help" className="text-[10px] text-fg-muted mt-0.5">
          {t('pendingAttemptPolicy.help')}
        </p>
      )}
    </div>
  );
}
