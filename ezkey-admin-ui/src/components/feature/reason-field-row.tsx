import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ReasonQuickPick } from '@/components/feature/reason-quick-pick';
import type { ReasonPresetGroupId } from '@/lib/reason-preset-groups';

export interface ReasonFieldRowProps {
  /** When set, renders production quick-pick presets above the reason field. */
  presetGroup?: ReasonPresetGroupId;
  /** Prefix for quick-pick control ids; required when `presetGroup` is set. */
  idPrefix: string;
  inputId: string;
  value: string;
  onChange: (value: string) => void;
  label: ReactNode;
  placeholder?: string;
  maxLength?: number;
  /** True when trimmed length is between 1 and 9 (inclusive) and the field must not submit in that state. */
  showMinLengthError: boolean;
  /**
   * Inline error copy: optional (mentions clearing), required short reason, or justification wording (audit chain seal/gap).
   * @default 'optional'
   */
  minLengthErrorTone?: 'optional' | 'required' | 'justification';
  /** e.g. `DemoReasonBadges` — rendered after the input, before the inline error. */
  childrenAfterInput?: ReactNode;
  className?: string;
}

/**
 * Shared layout for audited reason fields: optional {@link ReasonQuickPick}, label, input,
 * optional demo helpers, and a uniform inline error when the value is non-empty but under 10 characters.
 */
export function ReasonFieldRow({
  presetGroup,
  idPrefix,
  inputId,
  value,
  onChange,
  label,
  placeholder,
  maxLength = 500,
  showMinLengthError,
  minLengthErrorTone = 'optional',
  childrenAfterInput,
  className,
}: ReasonFieldRowProps) {
  const { t } = useTranslation('reasonPresets');
  const minErrorKey =
    minLengthErrorTone === 'optional'
      ? 'minLengthErrorOptional'
      : minLengthErrorTone === 'justification'
        ? 'minLengthErrorJustification'
        : 'minLengthErrorRequired';
  return (
    <div className={className ?? 'space-y-1.5'}>
      {presetGroup && (
        <ReasonQuickPick presetGroup={presetGroup} idPrefix={idPrefix} onSelectPreset={onChange} />
      )}
      <Label htmlFor={inputId}>{label}</Label>
      <Input
        id={inputId}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        maxLength={maxLength}
      />
      {childrenAfterInput}
      {showMinLengthError && (
        <p className="text-xs text-error" role="alert">
          {t(minErrorKey)}
        </p>
      )}
    </div>
  );
}
