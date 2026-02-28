import { cn } from '@/lib/utils';
import type { ReactNode } from 'react';

export interface ColumnDef<T> {
  header: string;
  /** Key of T or any string when using render(). */
  key: string;
  render?: (row: T) => ReactNode;
  className?: string;
}

interface DataTableProps<T extends object> {
  columns: ColumnDef<T>[];
  data: T[];
  isLoading?: boolean;
  onRowClick?: (row: T) => void;
  emptyMessage?: string;
  keyExtractor?: (row: T, index: number) => string | number;
}

/**
 * Reusable data table with neo-brutalism styling.
 * Use alongside <Pagination> for paginated lists.
 */
export function DataTable<T extends object>({
  columns,
  data,
  isLoading,
  onRowClick,
  emptyMessage = 'No records found.',
  keyExtractor,
}: DataTableProps<T>) {
  return (
    <div className="w-full overflow-x-auto border-2 border-fg">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="bg-fg text-surface">
            {columns.map((col) => (
              <th
                key={col.key}
                className={cn(
                  'px-3 py-2.5 text-left text-xs font-black uppercase tracking-wider whitespace-nowrap',
                  col.className,
                )}
              >
                {col.header}
              </th>
            ))}
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
                onClick={() => onRowClick?.(row)}
                className={cn(
                  'border-b border-fg/10 bg-surface even:bg-bg',
                  'transition-colors duration-75',
                  onRowClick && 'cursor-pointer hover:bg-accent/5 hover:border-l-2 hover:border-l-accent',
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
