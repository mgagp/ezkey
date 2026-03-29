import { useCallback, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useDetailNavigation } from '@/hooks/use-detail-navigation';
import {
  resolveListDetailNavigation,
  type ListDetailNavState,
  type ResolvedListDetailNav,
} from '@/lib/list-detail-navigation';

interface Options<T extends string | number> {
  currentId: T;
  /** Route prefix without the id segment, e.g. `/enrollments` → `/enrollments/${id}`. */
  pathPrefix: string;
}

/**
 * Prev/next across list rows for a full-page detail route when the list passes {@link ListDetailNavState} in location.state.
 */
export function useListDetailPageNavigation<T extends string | number>({
  currentId,
  pathPrefix,
}: Options<T>): {
  nav: ResolvedListDetailNav<T> | null;
  goPrev: () => void;
  goNext: () => void;
  showEndOfPageHint: boolean;
} {
  const location = useLocation();
  const navigate = useNavigate();

  const nav = useMemo(
    () => resolveListDetailNavigation<T>(location.state, currentId),
    [location.state, currentId],
  );

  const goPrev = useCallback(() => {
    if (nav?.prevId === undefined) return;
    navigate(`${pathPrefix}/${String(nav.prevId)}`, {
      state: {
        ids: nav.ids,
        currentIndex: nav.currentIndex - 1,
        listHasMorePages: nav.listHasMorePages,
      } as ListDetailNavState<T>,
    });
  }, [nav, navigate, pathPrefix]);

  const goNext = useCallback(() => {
    if (nav?.nextId === undefined) return;
    navigate(`${pathPrefix}/${String(nav.nextId)}`, {
      state: {
        ids: nav.ids,
        currentIndex: nav.currentIndex + 1,
        listHasMorePages: nav.listHasMorePages,
      } as ListDetailNavState<T>,
    });
  }, [nav, navigate, pathPrefix]);

  const hasPrev = nav?.prevId !== undefined;
  const hasNext = nav?.nextId !== undefined;
  const showEndOfPageHint = Boolean(
    nav && nav.nextId === undefined && nav.listHasMorePages === true,
  );

  useDetailNavigation(nav !== null, {
    enabled: nav !== null,
    hasPrev,
    hasNext,
    onPrev: goPrev,
    onNext: goNext,
  });

  return { nav, goPrev, goNext, showEndOfPageHint };
}
