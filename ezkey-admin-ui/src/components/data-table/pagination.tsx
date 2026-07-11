import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Tooltip } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  isFirst: boolean;
  isLast: boolean;
  onFirstPage: () => void;
  onLastPage: () => void;
  onPrevPage: () => void;
  onNextPage: () => void;
  /**
   * Current page size. When provided alongside onPageSizeChange, renders a
   * page size selector (10 / 20 / 50 / 100).
   */
  pageSize?: number;
  /**
   * Called with the new page size. Connect to `pagination.setPageSize` from
   * usePaginatedFromOrval. If omitted, the size selector is hidden.
   */
  onPageSizeChange?: (size: number) => void;
  /**
   * When "top", removes bottom border so the bar connects to the table below.
   * When "bottom" (default), removes top border so the bar connects to the table above.
   */
  position?: 'top' | 'bottom';
}

/**
 * Pagination controls for use below a <DataTable>.
 * Connect directly to the `pagination` object from usePaginatedFromOrval.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  isFirst,
  isLast,
  onFirstPage,
  onLastPage,
  onPrevPage,
  onNextPage,
  pageSize,
  onPageSizeChange,
  position = 'bottom',
}: PaginationProps) {
  const { t } = useTranslation('common');

  if (totalElements === 0 && totalPages === 0) return null;

  const borderClass =
    position === 'top'
      ? 'border-2 border-b-0 border-fg'
      : 'border-2 border-t-0 border-fg';

  return (
    <div className={cn('flex items-center justify-between px-3 py-2 bg-bg flex-wrap gap-2', borderClass)}>
      <p className="text-xs text-fg-muted font-medium">
        {t('pagination.page')} <strong className="text-fg">{page + 1}</strong> {t('pagination.of')}{' '}
        <strong className="text-fg">{Math.max(totalPages, 1)}</strong>
        <span className="text-fg-muted"> · {totalElements} {t('pagination.total')}</span>
      </p>

      <div className="flex items-center gap-2">
        {/* Page size selector — opt-in */}
        {pageSize !== undefined && onPageSizeChange && (
          <select
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            aria-label={t('pagination.pageSize')}
            className="border-2 border-fg bg-surface text-fg text-xs font-bold px-2 py-1 cursor-pointer focus:outline-none focus:ring-1 focus:ring-accent"
          >
            {PAGE_SIZE_OPTIONS.map((n) => (
              <option key={n} value={n}>
                {t('pagination.perPage', { count: n })}
              </option>
            ))}
          </select>
        )}

        <Tooltip content={t('pagination.first')}>
          <Button
            variant="secondary"
            size="sm"
            onClick={onFirstPage}
            disabled={isFirst}
            aria-label={t('pagination.first')}
          >
            <ChevronsLeft className="size-3.5" />
            {t('pagination.first')}
          </Button>
        </Tooltip>
        <Tooltip content={t('pagination.prev')}>
          <Button
            variant="secondary"
            size="sm"
            onClick={onPrevPage}
            disabled={isFirst}
            aria-label={t('pagination.prev')}
          >
            <ChevronLeft className="size-3.5" />
            {t('pagination.prev')}
          </Button>
        </Tooltip>
        <Tooltip content={t('pagination.next')}>
          <Button
            variant="secondary"
            size="sm"
            onClick={onNextPage}
            disabled={isLast}
            aria-label={t('pagination.next')}
          >
            {t('pagination.next')}
            <ChevronRight className="size-3.5" />
          </Button>
        </Tooltip>
        <Tooltip content={t('pagination.last')}>
          <Button
            variant="secondary"
            size="sm"
            onClick={onLastPage}
            disabled={isLast}
            aria-label={t('pagination.last')}
          >
            {t('pagination.last')}
            <ChevronsRight className="size-3.5" />
          </Button>
        </Tooltip>
      </div>
    </div>
  );
}
