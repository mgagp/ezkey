import { CircleHelp } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useHelp } from '@/context/use-help';
import type { HelpPatternId, HelpTopicId } from '@/lib/help-topics';

interface HelpInlineButtonProps {
  className?: string;
  topicId?: HelpTopicId;
  patternId?: HelpPatternId;
  ariaLabel?: string;
  title?: string;
}

/**
 * Optional contextual affordance (same route topic as the header help button).
 */
export function HelpInlineButton({
  className,
  topicId,
  patternId,
  ariaLabel,
  title,
}: HelpInlineButtonProps) {
  const { t } = useTranslation('help');
  const { openHelp } = useHelp();
  const defaultLabel = t('drawer.openHelp');

  return (
    <button
      type="button"
      onClick={() => openHelp(topicId || patternId ? { topicId, patternId } : undefined)}
      className={className}
      aria-label={ariaLabel ?? defaultLabel}
      title={title ?? defaultLabel}
    >
      <CircleHelp className="size-4" />
    </button>
  );
}
