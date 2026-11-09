import { useEffect, useState } from 'react';
import {
  PendingAttemptPolicyField,
  type PendingAttemptPolicyValue,
} from '@/components/feature/pending-attempt-policy-field';

type PendingAttemptPolicyEditorProps = {
  id: string;
  serverValue: PendingAttemptPolicyValue;
  disabled?: boolean;
  onSave: (next: PendingAttemptPolicyValue) => Promise<unknown>;
  onSaved: () => void;
  onSaveError: (error: unknown) => void;
};

/**
 * Detail-page control for the waiting-request setting.
 * The chosen value stays visible while the request is in flight. The select is
 * disabled until that request settles, and the previous value returns only if it fails.
 */
export function PendingAttemptPolicyEditor({
  id,
  serverValue,
  disabled = false,
  onSave,
  onSaved,
  onSaveError,
}: PendingAttemptPolicyEditorProps) {
  const [draft, setDraft] = useState<PendingAttemptPolicyValue | null>(null);
  const [saving, setSaving] = useState(false);
  const shown = draft ?? serverValue;

  useEffect(() => {
    if (draft !== null && draft === serverValue) {
      setDraft(null);
    }
  }, [draft, serverValue]);

  return (
    <PendingAttemptPolicyField
      id={id}
      value={shown}
      disabled={disabled || saving}
      onChange={(event) => {
        if (disabled || saving) {
          return;
        }
        const next = event.target.value === 'REFUSE' ? 'REFUSE' : 'REPLACE';
        if (next === shown) {
          return;
        }
        const previous = shown;
        setDraft(next);
        setSaving(true);
        void onSave(next)
          .then(() => {
            onSaved();
          })
          .catch((error: unknown) => {
            setDraft(previous === serverValue ? null : previous);
            onSaveError(error);
          })
          .finally(() => {
            setSaving(false);
          });
      }}
    />
  );
}
