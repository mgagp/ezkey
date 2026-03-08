import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  isFirst: boolean;
  isLast: boolean;
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
  onPrevPage,
  onNextPage,
  pageSize,
  onPageSizeChange,
}: PaginationProps) {
  if (totalElements === 0 && totalPages === 0) return null;

  return (
    <div className="flex items-center justify-between border-2 border-t-0 border-fg px-3 py-2 bg-bg flex-wrap gap-2">
      <p className="text-xs text-fg-muted font-medium">
        Page <strong className="text-fg">{page + 1}</strong> of{' '}
        <strong className="text-fg">{Math.max(totalPages, 1)}</strong>
        <span className="text-fg-muted"> · {totalElements} total</span>
      </p>

      <div className="flex items-center gap-2">
        {/* Page size selector — opt-in */}
        {pageSize !== undefined && onPageSizeChange && (
          <select
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="border-2 border-fg bg-surface text-fg text-xs font-bold px-2 py-1 cursor-pointer focus:outline-none focus:ring-1 focus:ring-accent"
          >
            {PAGE_SIZE_OPTIONS.map((n) => (
              <option key={n} value={n}>
                {n} / page
              </option>
            ))}
          </select>
        )}

        <Button variant="secondary" size="sm" onClick={onPrevPage} disabled={isFirst}>
          <ChevronLeft className="size-3.5" />
          Prev
        </Button>
        <Button variant="secondary" size="sm" onClick={onNextPage} disabled={isLast}>
          Next
          <ChevronRight className="size-3.5" />
        </Button>
      </div>
    </div>
  );
}
