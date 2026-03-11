import {
  DATE_RANGE_PRESET_OPTIONS,
  getPresetDateRange,
} from '@/lib/date-range-presets';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';

export interface DateRangeValue {
  from: string;
  to: string;
}

interface DateRangeFilterProps {
  value: DateRangeValue;
  onChange: (value: DateRangeValue) => void;
  /** Optional class for the wrapper. */
  className?: string;
  /** Preset dropdown width (default w-44). */
  presetWidth?: string;
  /** Show "Clear" link when range is set (default true). */
  showClear?: boolean;
  /** Label for the empty preset option (value ''). Default "Full range". Use e.g. "Select a range" when range is required. */
  emptyOptionLabel?: string;
}

/**
 * Controlled date range filter: preset dropdown plus From/To date inputs.
 * When a preset is selected, from/to are computed via getPresetDateRange.
 * When the user edits From/To manually, the preset selection is cleared.
 */
export function DateRangeFilter({
  value,
  onChange,
  className = '',
  presetWidth = 'w-44',
  showClear = true,
  emptyOptionLabel = 'Full range',
}: DateRangeFilterProps) {
  const hasRange = Boolean(value.from || value.to);

  const handlePresetChange = (presetValue: string) => {
    const range = getPresetDateRange(presetValue);
    if (range) {
      onChange({ from: range.from, to: range.to });
    } else {
      onChange({ from: '', to: '' });
    }
  };

  const handleFromChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...value, from: e.target.value });
  };

  const handleToChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...value, to: e.target.value });
  };

  const handleClear = () => {
    onChange({ from: '', to: '' });
  };

  const currentPresetId = (() => {
    if (!value.from && !value.to) return '';
    for (const opt of DATE_RANGE_PRESET_OPTIONS) {
      if (!opt.value) continue;
      const range = getPresetDateRange(opt.value);
      if (range && range.from === value.from && range.to === value.to) return opt.value;
    }
    return '';
  })();

  return (
    <div className={`flex flex-wrap items-center gap-3 ${className}`}>
      <div className={presetWidth}>
        <Select
          value={currentPresetId}
          onChange={(e) => handlePresetChange(e.target.value)}
          aria-label="Date range preset"
        >
          {DATE_RANGE_PRESET_OPTIONS.map((opt) => (
            <option key={opt.value || 'full'} value={opt.value}>
              {opt.value === '' ? emptyOptionLabel : opt.label}
            </option>
          ))}
        </Select>
      </div>
      <div className="flex items-center gap-2">
        <Label className="text-xs shrink-0">From</Label>
        <Input
          type="date"
          value={value.from}
          onChange={handleFromChange}
          className="w-36"
          aria-label="From date"
        />
      </div>
      <div className="flex items-center gap-2">
        <Label className="text-xs shrink-0">To</Label>
        <Input
          type="date"
          value={value.to}
          onChange={handleToChange}
          className="w-36"
          aria-label="To date"
        />
      </div>
      {showClear && hasRange && (
        <button
          type="button"
          className="text-[10px] text-accent underline hover:text-accent/80 font-medium"
          onClick={handleClear}
        >
          Clear
        </button>
      )}
    </div>
  );
}
