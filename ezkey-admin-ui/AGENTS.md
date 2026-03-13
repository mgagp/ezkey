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
    api-client.ts       Fetch wrapper: injects Bearer, handles 401 → /login, parses RFC 9457 ProblemDetail, getApiErrorMessage()
    auth.ts             sessionStorage session management (AuthSession)
    demo-mode.ts        Dev-only: isDemoMode flag and demo presets for create forms (stripped in production)
    query-client.ts     TanStack QueryClient — staleTime 30s, 1 retry, refetchOnWindowFocus false
    query-keys.ts       Canonical query key prefixes for list/entity caches (invalidateQueries)
    utils.ts            cn(), formatDate(), formatCountdown(), formatChallengeCode(), formatRelativeTime()
  types/
    api.ts              PageResponse<T>, ProblemDetail, request/response DTOs
    models.ts           Domain models: Integration, Enrollment, AuthAttempt, Admin, AuditLog, ApiKey
  context/
    auth-context.tsx    AuthProvider + useAuth; clears QueryClient cache on logout
    demo-mode-context.tsx   DemoModeProvider + useDemoModeSession (session toggle when VITE_DEMO_MODE; stripped in production)
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
  App.tsx               Root: QueryClientProvider > AuthProvider > DemoModeProvider > ToastProvider > RouterProvider
```

## Critical Patterns

### Error display (RFC 9457)

The Admin API returns errors as **RFC 9457 Problem Details** (`type`, `title`, `status`, `detail`, `path`). The client parses these and exposes them on `ApiError`. **Always** use `getApiErrorMessage(error, fallback)` when showing mutation/query errors (Alert or toast) so users see the API’s `detail` or `title` instead of a raw "HTTP 403".

```tsx
import { getApiErrorMessage } from '@/lib/api-client';

{ mutation.isError && (
  <Alert variant="error">{getApiErrorMessage(mutation.error, 'Operation failed.')}</Alert>
)}
```

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
  onFirstPage={pagination.firstPage}
  onLastPage={pagination.lastPage}
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

### Paginated lists: sort is always server-side

For any screen that uses a **paginated** list API (e.g. `usePaginatedFromOrval` + `listTenants`, `search` integrations, etc.), **sort must be sent to the backend**. The API accepts a `sort` parameter and returns the current page of the **globally** ordered result set. Do **not** implement client-side-only sort (e.g. sorting only the current page in memory): that would reorder just the visible segment and mislead users who assume "sort by name" applies to the full list. Wire `currentSort` and `onSort` from the pagination hook to `DataTable` so that clicking a sortable column triggers a new request with the updated `sort` param. If the backend does not support sort for a given list, do not add a `sortKey` to that column.

Clicking a new column sorts DESC by default; clicking the same column toggles ASC ↔ DESC.
The active column shows `↑` (ASC) or `↓` (DESC); inactive sortable columns show `⇅`.

### List refresh after mutations

After a successful create/edit/delete that affects a list, **invalidate that list's query key** so the list refetches and stays in sync. Use the **same key** as the list query so `invalidateQueries` targets the right cache.

- **Orval-generated list hooks** (e.g. tenants): use the **generated query key factory** in invalidations (e.g. `getListTenantsQueryKey()` from `@/generated/admin-api/tenants/tenants`). Orval uses path-based keys (e.g. `['/api/v1/tenants']`); invalidating `['tenants']` does not match and the list will not refresh.
- **usePaginatedFromOrval / useQuery with custom key**: use the same prefix you pass as `queryKey` (e.g. `['integrations']`, `['enrollments']`). You can use `queryKeys` from `@/lib/query-keys` for consistency.
- **Optional**: `await queryClient.invalidateQueries(...)` in mutation `onSuccess` so the mutation stays pending until the list refetch completes; the dialog can then close with the list already updated.

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

### Dialog (modal) — dismissible

The shared `Dialog` component (`@/components/ui/dialog`) accepts `dismissible` (default `true`). When `dismissible={false}`, backdrop click and Escape do **not** close the dialog; only the close button (X) and explicit actions (Cancel, Submit, Done) do.

- **Use `dismissible={false}`** for any dialog that contains a **form** (create/edit) or **critical state** (e.g. secret shown once, live test in progress). This prevents accidental data loss when the user clicks outside.
- **Leave default** (`dismissible` unspecified) for read-only detail dialogs and simple confirmations (yes/no, no form fields).

### Button placement (list toolbar and dialogs)

- **List toolbar:** The primary action (Create, New, Rotate Key, etc.) is always on the **right**. Put filters, search, and Refresh on the left; use `ml-auto` on the primary action button (or a right group with `justify-between`) so it stays right-aligned.
- **Dialogs:** Cancel (or secondary) on the left, primary Submit/Create on the right. Use `flex justify-end gap-2` (or `justify-end pt-2`) for dialog footers.

## Docker Deployment

Use **start.sh** to build and run the Admin UI in Docker:

```bash
./start.sh                    # Test/QA build (demo mode on), http://localhost:3080
./start.sh -production        # Production build (no demo), same port
./start.sh -p 3090            # Custom host port (e.g. avoid conflict with Grafana on 3000)
./start.sh -d                 # Detached
./start.sh --no-cache         # Force full rebuild
```

- **Build modes:** Default is **Test/QA** (`BUILD_MODE=test`): demo mode enabled (Ctrl+click on sidebar, Fill demo in create forms). Use `-production` for a production build: optimized, no demo code (tree-shaken).
- **Port:** Default host port is **3080** (avoids common conflicts: 3000 Grafana, 5173 Vite dev, 8080/9090/9100). Override with `-p PORT` or `--port PORT`.
- Served by **Caddy** on port 8080 inside the container; host port is configurable.
- `VITE_API_BASE_URL` is **empty** in production/test envs → all `/api/*` calls are relative.
- Caddy reverse-proxies `/api/*` → `host.docker.internal:9080` (Admin API on the Docker host).
- SPA fallback to `index.html` for all non-API routes (React Router client-side routing).

**Verification (production build, no demo leakage):**

- **Automated:** After a local `npm run build`, run `./scripts/assert-no-demo-in-build.sh` to grep `dist/` for demo-only strings; use in CI for the production build path.
- **Visual:** With `./start.sh -production`, open the UI and confirm: no "Demo" badge in header, no "Fill demo" in create dialogs, no Ctrl+click behavior on the sidebar brand.

## Developer/Demo mode

- **Dev-only:** When `VITE_DEMO_MODE=true` (e.g. in `.env.development`), the UI can show "Fill demo" controls in create dialogs (tenant, integration, enrollment, admin) and allow Ctrl+click on the sidebar brand to toggle a session-level demo indicator.
- **Production stripping:** All demo-mode code and preset data are **removed** from production builds. In `vite.config.ts`, production builds use `define: { 'import.meta.env.VITE_DEMO_MODE': '"false"' }`, so any branch guarded by `isDemoMode` (or `import.meta.env.VITE_DEMO_MODE === 'true'`) is dead code and tree-shaken. Do not rely on runtime checks for demo features; use the compile-time flag so production bundles never contain demo logic or strings.
- **Presets:** `src/lib/demo-mode.ts` exports `isDemoMode` and typed preset arrays; they are only referenced from components that render when `isDemoMode && sessionDemoOn`, so they are eliminated in production.

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

- **i18n**: All user-facing text must use translation keys via `useTranslation()` and `t('key')` (or `t('namespace:key')`). Supported locales: `en`, `fr`. Add new strings to `src/locales/en/*.json` and `src/locales/fr/*.json`. Language selector is in the header (EN | FR); preference is stored in `localStorage` under `ezkey-admin-ui-lang`.
- Strict TypeScript — no `any`, no non-null assertions (`!`) in app code
- No axios — always `api.*` from `@/lib/api-client`
- Token in `sessionStorage` only (never `localStorage`)
- `nonBlocking: true` always on login requests
- Challenge codes always zero-padded to 2 digits via `formatChallengeCode()`
- Every authenticated page wrapped in `<AppShell title="...">`
