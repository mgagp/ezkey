import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useCallback, useState } from 'react';

/**
 * Structural page response type that matches all PagedModel* DTOs generated
 * from the Admin API OpenAPI spec. Import this when typing paginated API calls.
 */
export interface PageResponse<T> {
  content?: T[];
  page?: {
    size?: number;
    totalElements?: number;
    totalPages?: number;
    number?: number;
  };
}

export interface PaginationState {
  page: number;
  size: number;
  sort: string;
}

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
  nextPage: () => void;
  prevPage: () => void;
  setPageSize: (size: number) => void;
  setSort: (sort: string) => void;
}

export interface UsePaginatedQueryResult<T> {
  data: T[];
  pagination: PaginationControls;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
}

/**
 * Wraps TanStack Query's useQuery with Spring Data pagination state.
 *
 * Usage:
 * ```ts
 * const { data, pagination, isLoading } = usePaginatedQuery({
 *   queryKey: ['integrations'],
 *   queryFn: ({ page, size, sort }) =>
 *     api.get(`/api/v1/integrations?page=${page}&size=${size}&sort=${sort}`),
 * });
 * ```
 */
export function usePaginatedQuery<T>({
  queryKey,
  queryFn,
  defaultSize = 20,
  defaultSort = 'createdAt,DESC',
}: {
  queryKey: unknown[];
  queryFn: (params: PaginationState) => Promise<PageResponse<T>>;
  defaultSize?: number;
  defaultSort?: string;
}): UsePaginatedQueryResult<T> {
  const [paginationState, setPaginationState] = useState<PaginationState>({
    page: 0,
    size: defaultSize,
    sort: defaultSort,
  });

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: [...queryKey, paginationState.page, paginationState.size, paginationState.sort],
    queryFn: () => queryFn(paginationState),
    placeholderData: keepPreviousData,
  });

  const goToPage = useCallback((page: number) => {
    setPaginationState((prev) => ({ ...prev, page }));
  }, []);

  const nextPage = useCallback(() => {
    setPaginationState((prev) => ({ ...prev, page: prev.page + 1 }));
  }, []);

  const prevPage = useCallback(() => {
    setPaginationState((prev) => ({ ...prev, page: Math.max(0, prev.page - 1) }));
  }, []);

  const setPageSize = useCallback((size: number) => {
    setPaginationState((prev) => ({ ...prev, size, page: 0 }));
  }, []);

  const setSort = useCallback((sort: string) => {
    setPaginationState((prev) => ({ ...prev, sort, page: 0 }));
  }, []);

  const totalPages = data?.page?.totalPages ?? 0;
  const currentPageNum = data?.page?.number ?? paginationState.page;

  return {
    data: data?.content ?? [],
    pagination: {
      page: paginationState.page,
      size: paginationState.size,
      sort: paginationState.sort,
      totalPages,
      totalElements: data?.page?.totalElements ?? 0,
      isFirst: currentPageNum === 0,
      isLast: totalPages === 0 || currentPageNum >= totalPages - 1,
      goToPage,
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
