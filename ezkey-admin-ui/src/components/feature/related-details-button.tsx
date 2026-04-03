import { useId } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { HelpInlineButton } from '@/components/help/help-inline-button';
import { Button } from '@/components/ui/button';
import { Tooltip } from '@/components/ui/tooltip';
import { getRelatedDetailsUiState } from '@/lib/related-details-ui';
import { cn } from '@/lib/utils';

interface RelatedDetailsButtonProps {
  onClick: () => void;
  isExpanded: boolean;
  isLoading: boolean;
  className?: string;
}

/**
 * Shared action for on-demand FK detail expansion. Keeps copy, status, tooltip,
 * and pattern-level help consistent across all detail panels.
 */
export function RelatedDetailsButton({
  onClick,
  isExpanded,
  isLoading,
  className,
}: RelatedDetailsButtonProps) {
  const { t } = useTranslation(['common', 'help']);
  const descriptionId = useId();
  const uiState = getRelatedDetailsUiState({ isExpanded, isLoading });
  const label = t(uiState.labelKey, { ns: 'common' });
  const description = t(uiState.tooltipKey, { ns: 'common' });

  const helpTitle = t('patterns.fkRelatedDetails.title', { ns: 'help' });
  const helpAriaLabel = t('help.ariaLabel', { ns: 'common', title: helpTitle });

  return (
    <div className={cn('flex items-center gap-2', className)}>
      <Tooltip content={description}>
        <div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={onClick}
            disabled={uiState.disableButton}
            aria-describedby={descriptionId}
            title={description}
            className={cn(uiState.showCheckIcon && 'gap-1.5')}
          >
            {uiState.showCheckIcon && <CheckCircle2 className="size-3.5" />}
            {label}
          </Button>
        </div>
      </Tooltip>
      <span id={descriptionId} className="sr-only">
        {description}
      </span>
      <HelpInlineButton
        patternId="fkRelatedDetails"
        className="p-1.5 hover:bg-fg/10 shrink-0 text-fg-muted hover:text-fg"
        ariaLabel={helpAriaLabel}
        title={helpAriaLabel}
      />
    </div>
  );
}
