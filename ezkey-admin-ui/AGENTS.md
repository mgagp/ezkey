# EZKey Admin UI — Agent Notes

For agents working in `ezkey-admin-ui/`.

## Purpose

Web-based SPA for **EZKey administrators** — supports both **Global Admins** and **Tenant Admins**.
The UI adapts dynamically based on the `adminType` returned at login: navigation items and
features are shown or hidden accordingly.
Calls the **Admin API** on port 9080. All tenant scoping is automatic via the bearer token.


## Stack

- Vite 7 + React 19 + TypeScript (strict, `verbatimModuleSyntax`)
- React Router v7 — `createBrowserRouter`, lazy-loaded pages, `ProtectedRoute` guard
- Tailwind CSS v4 — no `tailwind.config.ts`; all theme tokens in `src/index.css` under `@theme {}`
- TanStack Query v5 — data fetching, caching, pagination
- React Hook Form + Zod — forms and validation
- Fetch API — no axios; always use `api.*` from `@/lib/api-client`

## Path Alias

`@/` maps to `src/`. Use it for all cross-directory imports.

## Project Structure

```
src/
  lib/
    api-client.ts       Fetch wrapper: injects Bearer, handles 401 → /login, parses ProblemDetail
    auth.ts             sessionStorage session management (AuthSession)
    query-client.ts     TanStack QueryClient — staleTime 30s, 1 retry, refetchOnWindowFocus false
    utils.ts            cn(), formatDate(), formatCountdown(), formatChallengeCode(), formatRelativeTime()
  types/
    api.ts              PageResponse<T>, ProblemDetail, request/response DTOs
    models.ts           Domain models: Integration, Enrollment, AuthAttempt, Admin, AuditLog, ApiKey
  context/
    auth-context.tsx    AuthProvider + useAuth; clears QueryClient cache on logout
  hooks/
    use-paginated-query.ts   usePaginatedQuery — Spring Data pagination (see Pattern B below)
    use-debounce.ts          useDebounce — search input debouncing
    use-integrations.ts      useIntegrations — cached integration list + lookup map
  components/
    ui/                 Button, Input, Label, Card, Badge, Alert, Select, Dialog, Textarea
    layout/             AppShell, Sidebar, Header
    data-table/         DataTable<T>, Pagination
    feature/            EnrollmentStatusBadge, AuthAttemptStatusBadge
  pages/                One file per route (see table above)
  routes.tsx            All routes, lazy imports, ProtectedRoute
  App.tsx               Root: QueryClientProvider > AuthProvider > RouterProvider
```

## Critical Patterns

### PageResponse<T> — Pattern B (nested `page` object)

The Admin API returns pagination metadata **nested** inside a `page` sub-object:

```json
{
  "content": [...],
  "page": { "size": 20, "totalElements": 42, "totalPages": 3, "number": 0 }
}
```

`usePaginatedQuery` handles this internally. **Never** access `data.totalElements` directly —
always go through `pagination.totalElements` from the hook, or `data?.page?.totalElements` in
raw `useQuery` calls (e.g. dashboard stats).

### Paginated list (standard pattern)

```ts
const { data, pagination, isLoading, refetch } = usePaginatedQuery<Integration>({
  queryKey: ['integrations', filter],
  queryFn: ({ page, size, sort }) =>
    api.get<PageResponse<Integration>>(`/api/v1/integrations?page=${page}&size=${size}&sort=${sort}`),
});
```

```tsx
<DataTable
  columns={columns}
  data={data}
  isLoading={isLoading}
  currentSort={pagination.sort}        // enables active sort indicator
  onSort={pagination.setSort}          // wires column header clicks
/>
<Pagination
  page={pagination.page}
  totalPages={pagination.totalPages}
  totalElements={pagination.totalElements}
  isFirst={pagination.isFirst}
  isLast={pagination.isLast}
  onPrevPage={pagination.prevPage}
  onNextPage={pagination.nextPage}
  pageSize={pagination.size}           // shows size selector (10/20/50/100)
  onPageSizeChange={pagination.setPageSize}
/>
```

### Sortable columns

Add `sortKey` to any `ColumnDef` entry to make that column's header clickable.
`sortKey` must match the backend field name accepted by `?sort=`.
Columns without `sortKey` are rendered as static headers.

```ts
const columns: ColumnDef<Integration>[] = [
  { header: 'ID',      key: 'id',        sortKey: 'id'        },
  { header: 'Status',  key: 'active',    sortKey: 'active'    },
  { header: 'Created', key: 'createdAt', sortKey: 'createdAt' },
  { header: 'Name',    key: 'name'       /* no sortKey → not sortable */ },
];
```

Clicking a new column sorts DESC by default; clicking the same column toggles ASC ↔ DESC.
The active column shows `↑` (ASC) or `↓` (DESC); inactive sortable columns show `⇅`.

### Auth flow (login page)

1. `POST /api/v1/admin/auth/login` — always `nonBlocking: true`
   - Returns `authAttemptId`, `expiresAt`, optional `challengeCode` (2-digit integer)
2. Countdown driven by `expiresAt` via `setInterval`
3. Challenge displayed zero-padded: `formatChallengeCode(code)` → `"07"`
4. One long-running `POST /api/v1/admin/auth/passwordless-wait` — resolves when device responds
5. `AbortController` + `finalStatusRef` guard against race conditions on success/expiry

### Role-based feature gating

```ts
const { session } = useAuth();
const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
// Example: show Deactivate button only for Global Admin
{isGlobalAdmin && r.active && <Button ...>Deactivate</Button>}
```

### Test Authentication (enrollment-detail pattern)

Trigger a live auth attempt for a VERIFIED enrollment to confirm device binding:
1. `POST /api/v1/auth-attempts` — `{ enrollmentId, challengeRequested }`
2. Poll `GET /api/v1/auth-attempts/{id}` every 3s with `refetchInterval`
3. Polling stops on final status: `ACCEPTED | REJECTED | EXPIRED | INVALID`
4. Cancel via `POST /api/v1/auth-attempts/{id}/cancel`

### Logout

`logout()` in `AuthProvider` calls `queryClient.clear()` before clearing the session —
prevents stale data from a previous user appearing on the next login.

### Adding a new screen

1. Create `src/pages/my-screen.tsx` → `export default function MyScreenPage()`
2. Wrap content in `<AppShell title="Screen Name">`
3. Add a lazy route in `routes.tsx`
4. Use `usePaginatedQuery` + `DataTable` + `Pagination` for list views

## Docker Deployment

```
docker compose -f docker-compose.admin-ui.yml up --build
```

- Served by **Caddy** on port 8080 inside the container, exposed as **3000** on the host
- `VITE_API_BASE_URL` is **empty** in `.env.production` → all `/api/*` calls are relative
- Caddy reverse-proxies `/api/*` → `host.docker.internal:9080` (Admin API on the Docker host)
- SPA fallback to `index.html` for all non-API routes (React Router client-side routing)

## Visual Identity: Neo-Brutalism (subtle)

- Background: `bg-bg` (#d6e6ff — light tint of EZKey blue #3076df)
- Surface (cards, inputs): `bg-surface` (#ffffff)
- Foreground: `text-fg` / `border-fg` (#1a1a1a)
- Accent: `text-accent` / `bg-accent` (#e85d04 burnt orange)
- Sidebar: `bg-sidebar-bg` (#12275c deep navy)
- Borders: **always** `border-2 border-fg` on cards, inputs, tables
- Shadows: `shadow-brutal` (3px 3px 0 #1a1a1a), `shadow-brutal-lg` (5px 5px)
- Border radius: `0` on containers, `rounded-sm` max on inputs/buttons
- No gradients, no heavy animations, no rounded cards

## Naming Conventions

- Components: PascalCase (`AppShell`, `DataTable`)
- Hooks: `use-*` file → `use*` export (`use-paginated-query.ts` → `usePaginatedQuery`)
- Pages: kebab-case file, PascalCase default export (`login.tsx` → `LoginPage`)
- API field names: camelCase matching backend DTOs exactly

## Non-Negotiables

- All content in English
- Strict TypeScript — no `any`, no non-null assertions (`!`) in app code
- No axios — always `api.*` from `@/lib/api-client`
- Token in `sessionStorage` only (never `localStorage`)
- `nonBlocking: true` always on login requests
- Challenge codes always zero-padded to 2 digits via `formatChallengeCode()`
- Every authenticated page wrapped in `<AppShell title="...">`
