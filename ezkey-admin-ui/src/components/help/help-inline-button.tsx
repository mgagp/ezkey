import { CircleHelp } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useHelp } from '@/context/use-help';
import type { HelpTopicId } from '@/lib/help-topics';

interface HelpInlineButtonProps {
  className?: string;
  topicId?: HelpTopicId;
  ariaLabel?: string;
  title?: string;
}

/**
 * Optional contextual affordance (same route topic as the header help button).
 */
export function HelpInlineButton({
  className,
  topicId,
  ariaLabel,
  title,
}: HelpInlineButtonProps) {
  const { t } = useTranslation('help');
  const { openHelp } = useHelp();
  const defaultLabel = t('drawer.openHelp');

  return (
    <button
      type="button"
      onClick={() => openHelp(topicId ? { topicId } : undefined)}
      className={className}
      aria-label={ariaLabel ?? defaultLabel}
      title={title ?? defaultLabel}
    >
      <CircleHelp className="size-4" />
    </button>
  );
}
