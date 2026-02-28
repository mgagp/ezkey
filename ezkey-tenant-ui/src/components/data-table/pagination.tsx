import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  isFirst: boolean;
  isLast: boolean;
  onPrevPage: () => void;
  onNextPage: () => void;
}

/**
 * Pagination controls for use below a <DataTable>.
 * Connect directly to the `pagination` object from usePaginatedQuery.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  isFirst,
  isLast,
  onPrevPage,
  onNextPage,
}: PaginationProps) {
  if (totalElements === 0 && totalPages === 0) return null;

  return (
    <div className="flex items-center justify-between border-2 border-t-0 border-fg px-3 py-2 bg-bg">
      <p className="text-xs text-fg-muted font-medium">
        Page <strong className="text-fg">{page + 1}</strong> of{' '}
        <strong className="text-fg">{Math.max(totalPages, 1)}</strong>
        <span className="text-fg-muted"> · {totalElements} total</span>
      </p>
      <div className="flex gap-2">
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
