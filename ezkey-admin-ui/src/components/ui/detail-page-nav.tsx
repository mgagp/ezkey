import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Tooltip } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

interface DetailPageNavProps {
  hasPrev: boolean;
  hasNext: boolean;
  onPrev: () => void;
  onNext: () => void;
  /** Last item on current list page while more pages exist in the list. */
  showEndOfPageHint?: boolean;
  className?: string;
}

/**
 * Prev/next row navigation for full-page entity detail views (list context passed via router state).
 */
export function DetailPageNav({
  hasPrev,
  hasNext,
  onPrev,
  onNext,
  showEndOfPageHint = false,
  className,
}: DetailPageNavProps) {
  const { t } = useTranslation('common');

  if (!hasPrev && !hasNext && !showEndOfPageHint) return null;

  return (
    <div className={cn('flex flex-wrap items-center gap-3 mb-4', className)}>
      <div className="flex items-center gap-1">
        <Tooltip content={t('detailNav.prevEntry')}>
          <button
            type="button"
            onClick={onPrev}
            disabled={!hasPrev}
            className="p-1.5 border-2 border-fg bg-surface hover:bg-fg/5 transition-colors disabled:opacity-40 disabled:pointer-events-none"
            aria-label={t('detailNav.prevEntry')}
          >
            <ChevronLeft className="size-4" />
          </button>
        </Tooltip>
        <Tooltip content={t('detailNav.nextEntry')}>
          <button
            type="button"
            onClick={onNext}
            disabled={!hasNext}
            className="p-1.5 border-2 border-fg bg-surface hover:bg-fg/5 transition-colors disabled:opacity-40 disabled:pointer-events-none"
            aria-label={t('detailNav.nextEntry')}
          >
            <ChevronRight className="size-4" />
          </button>
        </Tooltip>
        {(hasPrev || hasNext) && (
          <span className="text-[10px] text-fg-muted font-mono ml-1 hidden sm:inline" aria-hidden>
            {t('detailNav.keyboardHintShort')}
          </span>
        )}
      </div>
      {showEndOfPageHint && (
        <p className="text-xs text-fg-muted italic">
          {t('detailNav.endOfPageMore')}
        </p>
      )}
    </div>
  );
}
