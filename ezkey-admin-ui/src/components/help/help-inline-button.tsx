import { CircleHelp } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useHelp } from '@/context/help-context';

interface HelpInlineButtonProps {
  className?: string;
}

/**
 * Optional contextual affordance (same route topic as the header help button).
 */
export function HelpInlineButton({ className }: HelpInlineButtonProps) {
  const { t } = useTranslation('help');
  const { openHelp } = useHelp();

  return (
    <button
      type="button"
      onClick={openHelp}
      className={className}
      aria-label={t('drawer.openHelp')}
      title={t('drawer.openHelp')}
    >
      <CircleHelp className="size-4" />
    </button>
  );
}
