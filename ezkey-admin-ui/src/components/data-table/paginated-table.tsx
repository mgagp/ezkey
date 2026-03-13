import type { ColumnDef } from '@/components/data-table/data-table';
import { DataTable } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';

/**
 * Shape of the pagination object returned by usePaginatedFromOrval.
 * Pass it as the pagination prop so PaginatedTable can render Pagination above and below the table.
 */
export interface PaginationControls {
  page: number;
  totalPages: number;
  totalElements: number;
  isFirst: boolean;
  isLast: boolean;
  firstPage: () => void;
  lastPage: () => void;
  prevPage: () => void;
  nextPage: () => void;
  size?: number;
  setPageSize?: (size: number) => void;
}

interface PaginatedTableProps<T extends object> {
  columns: ColumnDef<T>[];
  data: T[];
  isLoading?: boolean;
  onRowClick?: (row: T) => void;
  emptyMessage?: string;
  keyExtractor?: (row: T, index: number) => string | number;
  currentSort?: string;
  onSort?: (sort: string) => void;
  pagination: PaginationControls;
}

/**
 * Renders pagination above and below a DataTable for consistent UX across list
 * screens. Connect table props and the `pagination` object from
 * usePaginatedFromOrval.
 */
export function PaginatedTable<T extends object>({
  columns,
  data,
  isLoading,
  onRowClick,
  emptyMessage,
  keyExtractor,
  currentSort,
  onSort,
  pagination,
}: PaginatedTableProps<T>) {
  const paginationProps = {
    page: pagination.page,
    totalPages: pagination.totalPages,
    totalElements: pagination.totalElements,
    isFirst: pagination.isFirst,
    isLast: pagination.isLast,
    onFirstPage: pagination.firstPage,
    onLastPage: pagination.lastPage,
    onPrevPage: pagination.prevPage,
    onNextPage: pagination.nextPage,
    pageSize: pagination.size,
    onPageSizeChange: pagination.setPageSize,
  };

  return (
    <div>
      <Pagination {...paginationProps} position="top" />
      <DataTable<T>
        columns={columns}
        data={data}
        isLoading={isLoading}
        onRowClick={onRowClick}
        emptyMessage={emptyMessage}
        keyExtractor={keyExtractor}
        currentSort={currentSort}
        onSort={onSort}
      />
      <Pagination {...paginationProps} position="bottom" />
    </div>
  );
}
