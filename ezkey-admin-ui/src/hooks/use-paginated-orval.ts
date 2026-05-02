import { keepPreviousData, useQuery, type Query } from '@tanstack/react-query';
import { useCallback, useState } from 'react';

/**
 * Shape of the JSON body returned by Admin API paginated list endpoints.
 * Orval generates PagedModel*Dto types; the mutator returns this body directly.
 */
export interface PagedBody<T> {
  content?: T[];
  page?: {
    size?: number;
    number?: number;
    totalElements?: number;
    totalPages?: number;
  };
}

/** Alias for compatibility with code that still expects PageResponse (e.g. dashboard). */
export type PageResponse<T> = PagedBody<T>;

/** Controls returned alongside data — pass to <Pagination> and sort controls. */
export interface PaginationControls {
  page: number;
  size: number;
  sort: string;
  totalPages: number;
  totalElements: number;
  isFirst: boolean;
  isLast: boolean;
  goToPage: (page: number) => void;
  firstPage: () => void;
  lastPage: () => void;
  nextPage: () => void;
  prevPage: () => void;
  setPageSize: (size: number) => void;
  setSort: (sort: string) => void;
}

export interface UsePaginatedFromOrvalResult<T> {
  data: T[];
  pagination: PaginationControls;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
}

/**
 * Params passed to the fetch function. Must include page, size, sort (array).
 * Base params (filters) are merged with pagination state.
 */
export type PaginatedParams = {
  page: number;
  size: number;
  sort: string[];
  [key: string]: unknown;
};

/**
 * Wraps an Orval-generated list API function with local pagination state and
 * returns the same contract as the legacy usePaginatedQuery: { data, pagination }
 * for use with <DataTable> and <Pagination>.
 *
 * Pass the generated fetch function (e.g. search, search1, listAdmins, getAuditLogs)
 * which accepts params with page, size, sort (array). The mutator returns the
 * parsed JSON body (PagedModel*Dto) at runtime.
 */
export function usePaginatedFromOrval<T, P extends Record<string, unknown>>(options: {
  queryKey: unknown[];
  baseParams: P;
  fetchPage: (params: P & { page: number; size: number; sort: string[] }) => Promise<PagedBody<T>>;
  defaultSize?: number;
  defaultSort?: string;
  /** When false, the query is not run (e.g. for nested expandable sections). Default true. */
  enabled?: boolean;
  /** When false, do not show previous data while the query key changes (e.g. after focus). Default true. */
  keepPreviousData?: boolean;
  /** Optional polling; when a callback, receives the query so intervals can depend on loaded data. */
  refetchInterval?:
    | number
    | false
    | ((query: Query<PagedBody<T>, Error>) => number | false | undefined);
}): UsePaginatedFromOrvalResult<T> {
  const {
    queryKey,
    baseParams,
    fetchPage,
    defaultSize = 20,
    defaultSort = 'createdAt,DESC',
    enabled = true,
    keepPreviousData: useKeepPreviousData = true,
    refetchInterval,
  } = options;

  const pageResetKey = JSON.stringify(queryKey);
  const [pageState, setPageState] = useState(() => ({
    page: 0,
    pageResetKey,
  }));
  const [size, setSizeState] = useState(defaultSize);
  const [sort, setSortState] = useState(defaultSort);
  const page = pageState.pageResetKey === pageResetKey ? pageState.page : 0;

  const setPageForCurrentKey = useCallback(
    (nextPage: number | ((previousPage: number) => number)) => {
      setPageState((previous) => {
        const currentPage = previous.pageResetKey === pageResetKey ? previous.page : 0;
        const pageValue =
          typeof nextPage === 'function' ? nextPage(currentPage) : nextPage;
        return {
          page: pageValue,
          pageResetKey,
        };
      });
    },
    [pageResetKey],
  );

  const params = {
    ...baseParams,
    page,
    size,
    sort: [sort],
  } as P & { page: number; size: number; sort: string[] };

  const { data: body, isLoading, isError, error, refetch } = useQuery({
    queryKey: [...queryKey, page, size, sort],
    queryFn: () => fetchPage(params),
    placeholderData: useKeepPreviousData ? keepPreviousData : undefined,
    enabled,
    refetchInterval,
  });

  const totalPages = body?.page?.totalPages ?? 0;
  const currentPageNum = body?.page?.number ?? page;

  const goToPage = useCallback((p: number) => setPageForCurrentKey(p), [setPageForCurrentKey]);
  const firstPage = useCallback(() => goToPage(0), [goToPage]);
  const lastPage = useCallback(
    () => goToPage(Math.max(0, totalPages - 1)),
    [goToPage, totalPages],
  );
  const nextPage = useCallback(() => setPageForCurrentKey((prev) => prev + 1), [setPageForCurrentKey]);
  const prevPage = useCallback(() => setPageForCurrentKey((prev) => Math.max(0, prev - 1)), [setPageForCurrentKey]);
  const setPageSize = useCallback((s: number) => {
    setSizeState(s);
    setPageForCurrentKey(0);
  }, [setPageForCurrentKey]);
  const setSort = useCallback((s: string) => {
    setSortState(s);
    setPageForCurrentKey(0);
  }, [setPageForCurrentKey]);

  return {
    data: body?.content ?? [],
    pagination: {
      page,
      size,
      sort,
      totalPages,
      totalElements: body?.page?.totalElements ?? 0,
      isFirst: currentPageNum === 0,
      isLast: totalPages === 0 || currentPageNum >= totalPages - 1,
      goToPage,
      firstPage,
      lastPage,
      nextPage,
      prevPage,
      setPageSize,
      setSort,
    },
    isLoading,
    isError,
    error: error as Error | null,
    refetch,
  };
}
