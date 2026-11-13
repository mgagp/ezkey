/**
 * Audit-log list query params that live only in the URL (not mirrored in React state).
 *
 * The list page rebuilds search params from filter state. These keys must be copied
 * through from the current URL so a filter change does not drop them.
 *
 * Do not add `source` here. `source` is owned by `contextSource` state on the audit-logs
 * page. Copying it from the current URL fights explicit exits (entity-context clear,
 * date-range clear).
 *
 * Integrity deep-links live on `/integrity` (Global Admin) — not on the everyday trail.
 */
export const AUDIT_LOG_URL_ONLY_PARAMS = [] as const;

/**
 * Copies URL-only audit-log params from `from` onto `to`.
 *
 * @param from current location search params
 * @param to params being built from React filter state
 */
export function copyUrlOnlyAuditLogParams(from: URLSearchParams, to: URLSearchParams): void {
  for (const key of AUDIT_LOG_URL_ONLY_PARAMS) {
    const value = from.get(key);
    if (value) {
      to.set(key, value);
    }
  }
}
