import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Tooltip } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

interface DetailDialogHeaderNavProps {
  hasPrev: boolean;
  hasNext: boolean;
  onPrev: () => void;
  onNext: () => void;
  /** When true, show muted hint for ← → (e.g. both directions available or at least one). */
  showKeyboardHint?: boolean;
  className?: string;
}

/**
 * Prev/next icon buttons for detail modal headers (paired with Dialog `headerActions`).
 */
export function DetailDialogHeaderNav({
  hasPrev,
  hasNext,
  onPrev,
  onNext,
  showKeyboardHint = true,
  className,
}: DetailDialogHeaderNavProps) {
  const { t } = useTranslation('common');

  return (
    <div className={cn('flex items-center gap-1 shrink-0', className)}>
      <Tooltip content={t('detailNav.prevEntry')}>
        <button
          type="button"
          onClick={onPrev}
          disabled={!hasPrev}
          className="p-1 hover:bg-fg/10 transition-colors disabled:opacity-40 disabled:pointer-events-none"
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
          className="p-1 hover:bg-fg/10 transition-colors disabled:opacity-40 disabled:pointer-events-none"
          aria-label={t('detailNav.nextEntry')}
        >
          <ChevronRight className="size-4" />
        </button>
      </Tooltip>
      {showKeyboardHint && (hasPrev || hasNext) && (
        <span className="text-[10px] text-fg-muted font-mono ml-0.5 hidden sm:inline" aria-hidden>
          {t('detailNav.keyboardHintShort')}
        </span>
      )}
    </div>
  );
}
