/**
 * React Router location.state shape for prev/next navigation between list rows on entity detail pages.
 */
export interface ListDetailNavState<T extends string | number = number> {
  ids: T[];
  currentIndex: number;
  /** When true, the list had another page of results after the one the user navigated from. */
  listHasMorePages?: boolean;
}

export function buildListDetailNavState<T, TId extends string | number>(
  rows: T[],
  getId: (row: T) => TId,
  currentRow: T,
  listHasMorePages?: boolean,
): ListDetailNavState<TId> | null {
  const ids = rows.map(getId);
  const currentId = getId(currentRow);
  const currentIndex = ids.findIndex((id) => id === currentId);
  if (currentIndex < 0) return null;
  return { ids, currentIndex, listHasMorePages };
}

/** Resolved list context for prev/next on a detail page. */
export type ResolvedListDetailNav<T extends string | number = number> = {
  ids: T[];
  currentIndex: number;
  prevId: T | undefined;
  nextId: T | undefined;
  listHasMorePages?: boolean;
};

/**
 * Validates location.state and returns adjacent IDs for the current entity, or null if navigation is unavailable.
 */
export function resolveListDetailNavigation<T extends string | number>(
  state: unknown,
  currentId: T,
): ResolvedListDetailNav<T> | null {
  if (state == null || typeof state !== 'object') return null;
  const s = state as ListDetailNavState<T>;
  if (!Array.isArray(s.ids) || typeof s.currentIndex !== 'number') return null;
  if (s.currentIndex < 0 || s.currentIndex >= s.ids.length) return null;
  if (s.ids[s.currentIndex] !== currentId) return null;
  return {
    ids: s.ids,
    currentIndex: s.currentIndex,
    prevId: s.currentIndex > 0 ? s.ids[s.currentIndex - 1] : undefined,
    nextId: s.currentIndex < s.ids.length - 1 ? s.ids[s.currentIndex + 1] : undefined,
    listHasMorePages: s.listHasMorePages,
  };
}
