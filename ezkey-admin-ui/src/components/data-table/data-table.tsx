import { cn } from '@/lib/utils';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import type { KeyboardEvent, ReactNode } from 'react';
import { Tooltip } from '@/components/ui/tooltip';

export interface ColumnDef<T> {
  header: string;
  /** Optional tooltip text for the column header (domain-specific terms). */
  headerTooltip?: string;
  /** Key of T or any string when using render(). */
  key: string;
  render?: (row: T) => ReactNode;
  className?: string;
  /**
   * Backend field name for server-side sorting (e.g. 'createdAt', 'id').
   * Omit for non-sortable columns.
   */
  sortKey?: string;
}

interface DataTableProps<T extends object> {
  columns: ColumnDef<T>[];
  data: T[];
  isLoading?: boolean;
  onRowClick?: (row: T) => void;
  rowClassName?: (row: T, index: number) => string | undefined;
  emptyMessage?: string;
  keyExtractor?: (row: T, index: number) => string | number;
  /**
   * Current sort string from usePaginatedFromOrval (e.g. 'createdAt,DESC').
   * Required to show active sort indicator.
   */
  currentSort?: string;
  /**
   * Called with the new sort string when a sortable column header is clicked.
   * Connect to `pagination.setSort` from usePaginatedFromOrval.
   */
  onSort?: (sort: string) => void;
}

/** Parse 'field,DIR' → { field, dir }. Returns empty strings if missing. */
function parseSortString(sort: string): { field: string; dir: string } {
  const [field = '', dir = ''] = sort.split(',');
  return { field, dir: dir.toUpperCase() };
}

/** Activate clickable table controls with Enter or Space (keyboard parity for onClick). */
function activateOnEnterOrSpace(event: KeyboardEvent, action: () => void): void {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    action();
  }
}

/**
 * Reusable data table with neo-brutalism styling and optional server-side sorting.
 * Use alongside <Pagination> for paginated lists.
 *
 * Sorting: add `sortKey` to column definitions and pass `currentSort` + `onSort`.
 */
export function DataTable<T extends object>({
  columns,
  data,
  isLoading,
  onRowClick,
  rowClassName,
  emptyMessage = 'No records found.',
  keyExtractor,
  currentSort = '',
  onSort,
}: DataTableProps<T>) {
  const { field: activeField, dir: activeDir } = parseSortString(currentSort);

  const handleSort = (sortKey: string) => {
    if (!onSort) return;
    // Same column: toggle direction. New column: default DESC.
    if (sortKey === activeField) {
      onSort(`${sortKey},${activeDir === 'ASC' ? 'DESC' : 'ASC'}`);
    } else {
      onSort(`${sortKey},DESC`);
    }
  };

  return (
    <div className="w-full overflow-x-auto border-2 border-fg">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="bg-fg text-surface">
            {columns.map((col) => {
              const isSortable = !!col.sortKey && !!onSort;
              const isActive = isSortable && col.sortKey === activeField;
              const sortKey = col.sortKey;

              return (
                <th
                  key={col.key}
                  onClick={isSortable && sortKey ? () => handleSort(sortKey) : undefined}
                  onKeyDown={
                    isSortable && sortKey
                      ? (event) => activateOnEnterOrSpace(event, () => handleSort(sortKey))
                      : undefined
                  }
                  tabIndex={isSortable ? 0 : undefined}
                  aria-sort={
                    isActive ? (activeDir === 'ASC' ? 'ascending' : 'descending') : undefined
                  }
                  className={cn(
                    'px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider whitespace-nowrap',
                    isSortable && 'cursor-pointer select-none hover:bg-white/10 transition-colors',
                    col.className,
                  )}
                >
                  <span className="inline-flex items-center gap-1.5">
                    {col.headerTooltip ? (
                      <Tooltip content={col.headerTooltip}>
                        <span className="inline-flex items-center gap-1.5 cursor-help" tabIndex={0}>
                          {col.header}
                          {isSortable && (
                            isActive ? (
                              activeDir === 'ASC'
                                ? <ArrowUp className="size-3 opacity-90" />
                                : <ArrowDown className="size-3 opacity-90" />
                            ) : (
                              <ArrowUpDown className="size-3 opacity-40" />
                            )
                          )}
                        </span>
                      </Tooltip>
                    ) : (
                      <>
                        {col.header}
                        {isSortable && (
                          isActive ? (
                            activeDir === 'ASC'
                              ? <ArrowUp className="size-3 opacity-90" />
                              : <ArrowDown className="size-3 opacity-90" />
                          ) : (
                            <ArrowUpDown className="size-3 opacity-40" />
                          )
                        )}
                      </>
                    )}
                  </span>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={columns.length} className="px-3 py-10 text-center text-fg-muted">
                <span className="inline-block size-5 border-2 border-fg border-t-transparent rounded-full animate-spin" />
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td
                colSpan={columns.length}
                className="px-3 py-10 text-center text-fg-muted text-sm italic"
              >
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((row, index) => (
              <tr
                key={keyExtractor ? keyExtractor(row, index) : index}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                onKeyDown={
                  onRowClick
                    ? (event) => activateOnEnterOrSpace(event, () => onRowClick(row))
                    : undefined
                }
                tabIndex={onRowClick ? 0 : undefined}
                className={cn(
                  'border-b border-fg/10 bg-surface even:bg-bg',
                  'transition-colors duration-75',
                  onRowClick && 'cursor-pointer hover:bg-accent/5 hover:border-l-2 hover:border-l-accent',
                  rowClassName?.(row, index),
                )}
              >
                {columns.map((col) => (
                  <td key={col.key} className={cn('px-3 py-2 text-sm text-fg', col.className)}>
                    {col.render
                      ? col.render(row)
                      : (row[col.key as keyof T] as ReactNode)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
