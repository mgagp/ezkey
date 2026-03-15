/**
 * Demo-only: quick-select badges for reason fields (min 10 chars).
 * Renders only when VITE_DEMO_MODE and session demo are on; stripped in production.
 */

import { useTranslation } from 'react-i18next';
import { isDemoMode, reasonDemoPresets } from '@/lib/demo-mode';
import { useDemoModeSession } from '@/context/demo-mode-context';
import { Button } from '@/components/ui/button';

interface DemoReasonBadgesProps {
  onSelect: (value: string) => void;
}

/**
 * Renders a row of French and English reason presets as clickable badges for fast demo input.
 * Shown only in demo mode when session demo is on.
 */
export function DemoReasonBadges({ onSelect }: DemoReasonBadgesProps) {
  const { t } = useTranslation('layout');
  const { sessionDemoOn } = useDemoModeSession();

  if (!isDemoMode || !sessionDemoOn) {
    return null;
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5 pt-1">
      <span className="text-xs font-bold text-fg-muted uppercase tracking-wider mr-1">
        {t('demoQuickReason')}
      </span>
      {reasonDemoPresets.flatMap((preset) => [
        <Button
          key={`${preset.id}-fr`}
          type="button"
          variant="secondary"
          size="sm"
          className="text-xs h-7"
          onClick={() => onSelect(preset.fr)}
        >
          {preset.fr}
        </Button>,
        <Button
          key={`${preset.id}-en`}
          type="button"
          variant="secondary"
          size="sm"
          className="text-xs h-7"
          onClick={() => onSelect(preset.en)}
        >
          {preset.en}
        </Button>,
      ])}
    </div>
  );
}
