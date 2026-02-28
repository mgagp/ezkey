# EZKey Tenant UI — Agent Notes

For agents working in `ezkey-tenant-ui/`.

## Purpose

Web-based SPA for **Tenant Administrators** of the EZKey MFA system.
Calls the **Admin API** on port 9080. Tenant scoping is automatic — the bearer token
restricts all API responses to the authenticated tenant's resources.

## Stack

- Vite 7 + React 19 + TypeScript (strict, `verbatimModuleSyntax`)
- React Router v7 — SPA routing with `createBrowserRouter`
- Tailwind CSS v4 — no `tailwind.config.ts`; theme tokens live in `src/index.css` under `@theme {}`
- TanStack Query v5 — data fetching, caching, pagination
- React Hook Form + Zod — forms and validation
- Fetch API — no axios; always use `api.*` from `@/lib/api-client`

## Path Alias

`@/` maps to `src/`. Use it for all cross-directory imports.

## Project Structure

```
src/
  lib/
    api-client.ts       Fetch wrapper: injects Bearer token, handles 401 → /login redirect
    auth.ts             sessionStorage token management (AuthSession)
    query-client.ts     TanStack QueryClient (30s stale, 1 retry)
    utils.ts            cn(), formatDate(), formatCountdown(), formatChallengeCode()
  types/
    api.ts              PageResponse<T>, ProblemDetail, AdminLoginResponse, PasswordlessWaitRequest
    models.ts           Domain models: Integration, Enrollment, Admin, AuditLog, ApiKey
  context/
    auth-context.tsx    AuthProvider + useAuth hook
  hooks/
    use-paginated-query.ts  usePaginatedQuery — wraps useQuery with Spring Data page/size/sort state
  components/
    ui/                 Button, Input, Label, Card, Badge, Alert (neo-brutalism styled)
    layout/             AppShell, Sidebar, Header
    data-table/         DataTable<T>, Pagination
  pages/                login, dashboard (+ future: integrations, enrollments, etc.)
  routes.tsx            All routes; ProtectedRoute guard; PlaceholderPage for Phase 2 screens
  App.tsx               Root: QueryClientProvider > AuthProvider > RouterProvider
```

## Key Patterns

### API calls

Always use `api.get<T>()`, `api.post<T>()`, `api.put<T>()`, `api.delete<T>()`.
On 401, the client auto-clears the session and redirects to `/login`.

### Paginated lists

```ts
const { data, pagination, isLoading } = usePaginatedQuery({
  queryKey: ['integrations'],
  queryFn: ({ page, size, sort }) =>
    api.get<PageResponse<Integration>>(
      `/api/v1/integrations?page=${page}&size=${size}&sort=${sort}`
    ),
});
// Render:
<DataTable columns={...} data={data} isLoading={isLoading} />
<Pagination
  page={pagination.page}
  totalPages={pagination.totalPages}
  totalElements={pagination.totalElements}
  isFirst={pagination.isFirst}
  isLast={pagination.isLast}
  onPrevPage={pagination.prevPage}
  onNextPage={pagination.nextPage}
/>
```

### Auth flow (Login page pattern)

1. `POST /api/v1/admin/auth/login` — always with `nonBlocking: true`
   - Response includes `authAttemptId`, `expiresAt`, optionally `challengeCode` (2-digit integer)
2. Start countdown timer using `expiresAt` (update every second via `setInterval`)
3. Display challenge code as zero-padded string: `String(code).padStart(2, '0')`
4. One long-running `POST /api/v1/admin/auth/passwordless-wait` — blocks until device responds
   - On success: call `login({ token, username, adminType, expiresAt })` → navigate to /dashboard
   - On rejected: show rejection state
5. Use `AbortController` + `finalStatusRef` to prevent race conditions between countdown and wait

### Adding a new screen

1. Create `src/pages/my-screen.tsx` with `export default function MyScreenPage()`
2. Use `<AppShell title="Screen Name">` as the wrapper
3. Replace the `<PlaceholderPage>` in `routes.tsx` with a lazy import
4. Use `usePaginatedQuery` + `DataTable` + `Pagination` for list views

## Visual Identity: Neo-Brutalism (subtle)

Design rules — do not deviate:

- Background: `bg-bg` (#d6e6ff — light tint of EZKey logo blue #3076df)
- Surface (cards, inputs): `bg-surface` (#ffffff)
- Foreground (text, borders): `text-fg` / `border-fg` (#1a1a1a near-black)
- Accent: `bg-accent` / `text-accent` (#e85d04 burnt orange)
- Sidebar: `bg-sidebar-bg` (#12275c deep navy, same blue family) with `text-sidebar-fg`
- Borders: **always** `border-2 border-fg` on cards, inputs, tables
- Shadows: `shadow-brutal` (3px 3px 0 #1a1a1a) and `shadow-brutal-lg` (5px 5px)
- Border radius: `0` on containers, `rounded-sm` (2px) on inputs/buttons max
- Hover state: shadow grows + slight `-translate-y-px`
- No gradients, no heavy animations, no rounded cards

## Naming Conventions

- Components: PascalCase (`AppShell`, `DataTable`)
- Hooks: `use-*` filename, `use*` export (`use-paginated-query.ts` → `usePaginatedQuery`)
- Pages: kebab-case file, default PascalCase export (`login.tsx` → `export default function LoginPage`)
- Types/interfaces: PascalCase
- API field names: camelCase matching backend DTOs exactly

## Non-Negotiables

- All content in English
- Strict TypeScript — no `any`, no non-null assertions (`!`) in app code
- No axios — always use `api.*` from `@/lib/api-client`
- Token always in `sessionStorage` (never `localStorage`)
- `nonBlocking: true` always included in login requests
- Challenge code always displayed zero-padded to 2 digits
- Every page wrapped in `<AppShell title="...">` (except login/404)
