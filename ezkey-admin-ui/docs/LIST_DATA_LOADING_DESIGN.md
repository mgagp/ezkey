# Admin UI — List data loading: raw vs wrapped

Conceptual overview of how Admin UI list screens load data from Orval-generated Admin API clients.

---

## 1. What does "raw" vs "wrapped" mean?

### Raw Orval hook

- Call the **hook** Orval generates from the OpenAPI spec (e.g. a generated `useList…()`).
- Orval chooses the TanStack Query cache key from the **URL path** (e.g. `['/api/v1/tenants']`).
- **One line** to fetch, but **we do not own the cache key**.

### Wrapped (our layer)

- Do **not** use the Orval-generated list hook for the screen.
- Use **`usePaginatedFromOrval`**:
  - TanStack Query with a **query key we choose** (e.g. `['tenants', filters]`, `['integrations', filters]`).
  - Orval-generated **function** (e.g. `listTenants(params)`, `search(params)`) for the HTTP call.
- **We own** the cache key and pagination state; Orval only provides the typed fetch.

**Raw** = Orval hook as-is (Orval owns the key). **Wrapped** = our hook + our key; Orval for the HTTP call only.

---

## 2. Current standard (all Tier A operator lists)

Operator list screens that hit **server-paginated** Admin API endpoints use the **wrapped** pattern, including **Tenants**:

| List | API | UI pattern |
|------|-----|------------|
| Tenants | `GET /api/v1/tenants` — `page`, `size`, `sort`, optional `tenantName` / `active` | `usePaginatedFromOrval` + `queryKey: ['tenants', …]` |
| Integrations, Enrollments, Admins, Audit logs, … | Same envelope (`content` + `page`) | `usePaginatedFromOrval` + list-specific keys |

Contract for tenants list: [`docs/ENDPOINT.md`](../../docs/ENDPOINT.md) § List tenants. Pagination mechanics: [`docs/PAGINATION_GUIDELINES.md`](../../docs/PAGINATION_GUIDELINES.md). Screen matrix: [`product-docs/global/admin-ui-paginated-screens-matrix.md`](../../product-docs/global/admin-ui-paginated-screens-matrix.md).

**Invalidation:** use the **same key prefix** as the list query (e.g. `['tenants']`). See `AGENTS.md` § List refresh after mutations.

---

## 3. Historical note (why this doc once singled out Tenants)

Before server-side pagination on `GET /api/v1/tenants`, the API returned a **full array**. The UI used a **raw** Orval hook and paginated/filtered in the browser — the only list that did so.

That asymmetry is **gone**: backend pagination shipped, and `tenants.tsx` uses `usePaginatedFromOrval` like the other operator lists. Do not reintroduce client-only pagination/sort for this screen.

---

## 4. When would "raw" still appear?

Raw Orval hooks remain appropriate for **non-paginated** reads (detail-by-id, small fixed payloads) where a generated hook is enough. For any **paginated operator list**, prefer wrapped + server `sort` (never sort only the current page in memory).
