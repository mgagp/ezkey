import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import {
  type ReasonPresetGroupId,
  getReasonPresetOptionKeys,
} from '@/lib/reason-preset-groups';

export interface ReasonQuickPickProps {
  /** Which preset list to load from the `reasonPresets` namespace. */
  presetGroup: ReasonPresetGroupId;
  /** Prefix for stable `id` / `htmlFor` in dialogs that may host multiple fields. */
  idPrefix: string;
  /** Called with the full preset sentence in the current locale when the user picks a non-empty option. */
  onSelectPreset: (text: string) => void;
  className?: string;
}

/**
 * Optional native select that injects localized preset strings into a parent-controlled reason field.
 * Renders nothing when the group has no configured option keys yet.
 */
export function ReasonQuickPick({
  presetGroup,
  idPrefix,
  onSelectPreset,
  className,
}: ReasonQuickPickProps) {
  const { t } = useTranslation('reasonPresets');
  const keys = getReasonPresetOptionKeys(presetGroup);
  const [selectValue, setSelectValue] = useState('');

  if (keys.length === 0) {
    return null;
  }

  const selectId = `${idPrefix}-quick-suggest`;

  return (
    <div className={className ?? 'space-y-1'}>
      <Label htmlFor={selectId} className="text-xs">
        {t('selectLabel')}
      </Label>
      <Select
        id={selectId}
        value={selectValue}
        onChange={(e) => {
          const v = e.target.value;
          setSelectValue('');
          if (v.length === 0) return;
          const text = t(`groups.${presetGroup}.options.${v}`);
          onSelectPreset(text);
        }}
      >
        <option value="">{t('selectPlaceholder')}</option>
        {keys.map((k) => (
          <option key={k} value={k}>
            {t(`groups.${presetGroup}.options.${k}`)}
          </option>
        ))}
      </Select>
    </div>
  );
}
