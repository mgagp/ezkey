# EZKey Admin UI — Agent Notes

For agents working in `ezkey-admin-ui/`.

## Purpose

Web-based SPA for **EZKey administrators** — supports both **Global Admins** and **Tenant Admins**.
The UI adapts dynamically based on the `adminType` returned at login: navigation items and
features are shown or hidden accordingly.
Calls the **Admin API** on port 9080. All tenant scoping is automatic via the bearer token.


## Stack

- Vite 8 + React 19 + TypeScript (strict, `verbatimModuleSyntax`)
- React Router v7 — `createBrowserRouter`, lazy-loaded pages, `ProtectedRoute` guard
- Tailwind CSS v4 — no `tailwind.config.ts`; all theme tokens in `src/index.css` under `@theme {}`
- TanStack Query v5 — data fetching, caching, pagination
- Orval **8.18.0** (exact pin) — OpenAPI → TanStack Query client (`npm run generate:api` → `src/generated/admin-api/`). Treat **8.19+** minors as a new validation ladder (regenerate + build/test/lint). **`orval.config.ts` must not set global `useQuery` or `useMutation`** — Orval 8.10+ applies explicit globals to all HTTP verbs and breaks hook shapes; keep `query: { version: 5 }` only (verb-aware defaults: GET → query, mutations → `useMutation`). Migration history: [`product-docs/global/backlog/TB-2026-05-28-admin-ui-orval-upgrade.md`](../product-docs/global/backlog/TB-2026-05-28-admin-ui-orval-upgrade.md).
- **Install scripts (npm v12 prep):** `package.json` `allowScripts` approves `esbuild@0.28.1` (Vite + Orval transitive dep; native binary postinstall). After dependency changes, run `npm approve-scripts --allow-scripts-pending` if npm warns about uncovered scripts.
- React Hook Form + Zod — forms and validation
- Fetch API — no axios; always use `api.*` from `@/lib/api-client`

## Path Alias

`@/` maps to `src/`. Use it for all cross-directory imports.

## Project Structure

```
src/
  lib/
    api-client.ts       Fetch wrapper: injects Bearer; 401 → redirect to /login only when the request used a session JWT (expired session). Unauthenticated calls (e.g. login with `requireAuth: false`) parse RFC 9457 body instead. getApiErrorMessage()
    api-error-i18n.ts   Maps ProblemDetail.type (https://ezkey.io/problems/...) to i18n keys under `errors` (Option B); getTranslatedApiError()
    auth.ts             sessionStorage session management (AuthSession)
    demo-mode.ts        Dev-only: isDemoMode flag and demo presets for create forms (stripped in production); locale-specific preset copy lives in `locales/*/demo.json` (e.g. Unicorn Farm FR/EN)
    reason-preset-groups.ts  Keys per operation for audited reason quick-picks (`reasonPresets` i18n namespace)
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
    feature/            EnrollmentStatusBadge, AuthAttemptStatusBadge, ReasonQuickPick, ReasonFieldRow (production reason/justification suggestions + shared min-length line; `locales/*/reasonPresets.json` includes phase-2 `encryption_key_rotate` + `audit_chain_justification`), DemoReasonBadges (demo-only)
  pages/                One file per route (see table above)
  routes.tsx            All routes, lazy imports, ProtectedRoute
  App.tsx               Root: QueryClientProvider > AuthProvider > DemoModeProvider > ToastProvider > RouterProvider
```

## Critical Patterns

### Error display (RFC 9457)

The Admin API returns errors as **RFC 9457 Problem Details** (`type`, `title`, `status`, `detail`, `path`). The client parses these and exposes them on `ApiError`.

- **Translated API errors (preferred):** use `getTranslatedApiError(error, t, fallback)` from `@/lib/api-error-i18n`. It maps `type` URIs under `https://ezkey.io/problems/` to the `errors` namespace (Option B). When the API includes RFC 9457 extension **`parameters`** (JSON object of scalars), the client interpolates them into the matching `errors.*` string (see `docs/admin-ui-admin-api-error-inventory.md`). For allowlisted types (e.g. `authentication.*`, static domain/enrollment quick wins — see `docs/admin-ui-admin-api-error-inventory.md`), curated locale strings win when present. For other types (e.g. `admin.invalid-argument`), RFC 9457 **`detail`** wins when non-empty so specific server messages (duplicate name, validation text) are not replaced by generic titles. If there is no `detail`, it uses a translation when the key exists, then `getApiErrorMessage`.
- **Raw English from API only:** use `getApiErrorMessage(error, fallback)` from `@/lib/api-client`.

When adding a new problem `type` from the backend, add matching keys under `src/locales/en/errors.json` and `src/locales/fr/errors.json` (nested object matching the dotted key path).

```tsx
import { getTranslatedApiError } from '@/lib/api-error-i18n';

{ mutation.isError && (
  <Alert variant="error">{getTranslatedApiError(mutation.error, t, t('myNamespace:operationFailed'))}</Alert>
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

### Detail refresh after mutations

After a mutation that changes the current entity on a **detail** page (e.g. deactivate enrollment, update API key), invalidate using the **same query key** as the detail query so the view refetches and reflects the new state. For Orval-generated detail hooks (`useGetById`, `useGetApiKey`, `useGetTenant`), use the generated **query key factory** from the same module (e.g. `getGetByIdQueryKey(id)`, `getGetApiKeyQueryKey(keyId)`). Custom keys like `['enrollment', id]` do not match Orval's path-based keys (`['/api/v1/enrollments/123']`) and the detail will not refresh. See `src/lib/query-keys.ts` for the rule.

### Auth flow (login page)

1. `POST /api/v1/admin/auth/login` — always `nonBlocking: true`
   - Returns `authAttemptId`, `expiresAt`, optional `challengeCode` (2-digit integer)
2. Countdown driven by `expiresAt` via `setInterval`
3. Challenge displayed zero-padded: `formatChallengeCode(code)` → `"07"`
4. One long-running `POST /api/v1/admin/auth/passwordless-wait` — resolves when device responds
5. `AbortController` + `finalStatusRef` guard against race conditions on success/expiry

The login form also offers an optional **pin toggle** beside the username field (tooltip + `aria-pressed`) for remembering the username on this device — see `last-username-pref.ts` and `login.tsx`.

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

The shared `Dialog` component (`@/components/ui/dialog`) accepts `dismissible` (default `true`). When `dismissible={false}`, backdrop click and Escape do **not** close the dialog; only the close button (X) and explicit actions (Cancel, Submit, Done) do. Optional `size`: `sm` | `md` | `lg` | `lg-wide` (the last is ~26.5% wider than `lg`, for content-heavy read-only modals such as audit log detail).

- **Use `dismissible={false}`** for any dialog that contains a **form** (create/edit) or **critical state** (e.g. secret shown once, live test in progress). This prevents accidental data loss when the user clicks outside.
- **Leave default** (`dismissible` unspecified) for read-only detail dialogs and simple confirmations (yes/no, no form fields).

### Button placement (list toolbar and dialogs)

- **List toolbar:** The primary action (Create, New, Rotate Key, etc.) is always on the **right**. Put filters, search, and Refresh on the left; use `ml-auto` on the primary action button (or a right group with `justify-between`) so it stays right-aligned.
- **Dialogs:** Cancel (or secondary) on the left, primary Submit/Create on the right. Use `flex justify-end gap-2` (or `justify-end pt-2`) for dialog footers.

## Docker Deployment

Use **start.sh** to build and run the Admin UI in Docker:

```bash
./start.sh                    # Test/QA build (demo mode on), http://localhost:3090
./start.sh -production        # Production build (no demo), same port
./start.sh -p 3080            # Custom host port override
./start.sh -d                 # Detached
./start.sh --no-cache         # Force full rebuild
```

- **Build modes:** Default is **Test/QA** (`BUILD_MODE=test`): demo mode enabled (Ctrl+click on sidebar, Fill demo in create forms). Use `-production` for a production build: optimized, no demo code (tree-shaken).
- **Port:** Default host port is **3090** (avoids 3000 Grafana, **3080** standalone Demo Device repo, 5173 Vite dev, 8080/9090/9100). Override with `-p PORT` or `--port PORT`.
- Served by **Caddy** on port 8080 inside the container; host port is configurable.
- `VITE_API_BASE_URL` is **empty** in production/test envs → all `/api/*` calls are relative.
- Caddy reverse-proxies `/api/*` → `host.docker.internal:9080` (Admin API on the Docker host).
- SPA fallback to `index.html` for all non-API routes (React Router client-side routing).

**Verification (production build, no demo leakage):**

- **Automated:** After a local `npm run build` or `npm run build:cloudflare`, run `./scripts/assert-no-demo-in-build.sh` (or `npm run build:cloudflare:verify`) to grep `dist/` for strings unique to `demo-mode.ts` preset data; use in CI for production or Cloudflare build paths.
- **Visual:** With `./start.sh -production`, open the UI and confirm: no "Demo" badge in header, no "Fill demo" in create dialogs, no Ctrl+click behavior on the sidebar brand.

## Browser UI Tests

- The Admin UI now has a **Playwright** browser suite under `e2e/`.
- The primary validation model is **device-backed end to end**:
  - start the EZKey backend stack with `clean-start`
  - use the pre-seeded **Demo Device** on `http://localhost:8083`
  - run the Admin UI either with `npm run dev` or `./start.sh`
- Preferred commands:
  - Local dev path: `./scripts/run-ui-tests.sh` (runs `npm run test:browser:install` for Chromium first)
  - Docker-only QA path: `./scripts/run-ui-tests-docker.sh`
  - One-off browser download after clone or `@playwright/test` bump: `npm run test:browser:install`
- Default scope is intentionally narrow:
  - public login shell
  - real passwordless login approved on Demo Device
  - authenticated shell smoke
  - one representative post-login workflow
  - elective rejection path
- Use browser tests when a change materially affects:
  - Admin UI login behavior
  - authenticated shell/navigation behavior
  - Admin UI ↔ Demo Device interaction
- Do **not** default to running browser tests for every minor UI text or layout tweak.
- Keep instrumentation **secondary**. The default path should continue to exercise the real Docker stack and real Demo Device behavior.

## React Doctor curated pass

- Keyword for humans and agents: **`doctor-curated`**.
- Purpose: a **punctual lint/polish pass** for Admin UI React code. This is intentionally **not** a CI gate and **not** a zero-warning exercise.
- Default commands from `ezkey-admin-ui/`:
  - `npm run doctor:curated`
  - `scripts\doctor-curated.cmd` on Windows
- Output lives under `logs/react-doctor/`:
  - `react-doctor.raw.json` — full raw tool output
  - `react-doctor.curated.json` — filtered summary for follow-up analysis
  - `react-doctor.curated.md` — human-readable shortlist
  - `react-doctor.stderr.log` — CLI warnings / stderr
- Current low-signal suppressions in the curated pass:
  - `unused-file`
  - `design-no-em-dash-in-jsx-text`
  - `only-export-components`
- Working rule for agents: when the user asks for a **`doctor-curated`** pass, run the script first, read the curated Markdown or JSON, then propose or implement a **small, prioritized** set of fixes. Prefer P1 first, then a narrow slice of P2. Do not turn the pass into a broad refactor campaign.
- Working rule for humans: treat the curated report as a **triage aid**. The goal is to identify a few high-signal improvements with strong signal-to-effort ratio and stop before diminishing returns.

### Cursor IDE browser (MCP) spot checks

Agents using the embedded browser tools should mirror the **same sequencing as Playwright**, not invent a parallel protocol:

- After `browser_navigate`, take **`browser_snapshot` with `interactive: true`** when you need clickable element refs; a non-interactive snapshot can look nearly empty on first paint.
- **Passwordless login + Demo Device**: drive `POST` login from the Admin UI, wait until the UI shows the waiting state, then on the Demo Device open **`/phone/ezkey/enrollments/{id}/auth`** (same pattern as `e2e/support/auth-flow.ts`: repeated navigations / reloads until the pending shell appears). Relying only on a single “Check for Authentication Requests” click may not refresh the accessibility tree the way a full navigation does in every runtime.
- URLs and ports match the Playwright harness: Admin UI (`EZKEY_ADMIN_UI_URL`, often dev `http://127.0.0.1:5173` or preview `http://127.0.0.1:4173`), Demo Device (`EZKEY_DEMO_DEVICE_URL`, default `http://127.0.0.1:8083`), bootstrap admin **`admin.docker`** (`EZKEY_ADMIN_UI_TEST_USERNAME`).
- When MCP proves flaky, treat **`./scripts/run-ui-tests.sh`** (or `PLAYWRIGHT_SKIP_WEBSERVER=1` against an already-running dev server) as the **ground-truth** device-backed check.

### Autonomy and recommendations

- For UI changes with meaningful workflow risk, do not stop at "browser tests exist" — explicitly judge whether:
  - the current Playwright coverage is already sufficient,
  - the existing suite should be run,
  - or a focused browser test should be added or adjusted.
- Typical triggers for that recommendation:
  - critical actions such as revoke, deactivate, approve, reset, or other security-sensitive mutations
  - changed confirmation/cancel behavior
  - changed success/error/retry states
  - changed route transitions, guards, or role-based visibility
  - changed Admin UI ↔ Demo Device interactions
- Keep recommendations proportionate:
  - low-risk text or layout changes usually do **not** justify browser test work
  - workflow or risk-bearing changes often **do**
- When proposing a new UI test, prefer extending the existing representative suite over adding broad or duplicate coverage.
- In the Ezkey spirit, prefer a **small, high-value recommendation** over a large generic test wish-list.

### Manual exploratory summaries

- When a UI change deserves human validation, it is often useful to provide a **brief manual exploratory test summary** alongside the automated recommendation.
- Default assumptions for that summary:
  - clean-start baseline
  - empty database
  - default Docker parameters
  - Admin UI started either with `npm run dev` or `./start.sh`
- When it helps the reviewer move quickly, use **demo-mode themes and presets** as the concrete examples for form input and workflow setup.
- Good summaries are short and operator-friendly:
  - a few representative steps
  - expected visible results
  - only the most relevant paths for the current change
- This is especially valuable for:
  - QA handoff
  - interactive functional review after a plan is implemented
  - preserving a compact historical trace of how a workflow was meant to be exercised

## Security (headers, workflows, deployment)

- **Daily dev:** `npm run dev` — Vite HMR; **not** the same HTTP header surface as Caddy.
- **QA / prod-like:** `./start.sh` — **Caddy** (`docker/Caddyfile`) enforces **CSP** and other headers on the built SPA.
- **Full write-up** (two-path model, `clean-start` + Caddy default, split UI/API, mkcert, future HttpOnly cookies): [`docs/admin-ui-security.md`](../docs/admin-ui-security.md)
- **How to validate** (DevTools, `curl`, first-session checklist): [`docs/admin-ui-security-validation.md`](../docs/admin-ui-security-validation.md)

## Developer/Demo mode

- **Dev-only:** When `VITE_DEMO_MODE=true` (e.g. in `.env.development`), the UI can show "Fill demo" controls in create dialogs (tenant, integration, enrollment, admin) and allow Ctrl+click on the sidebar brand to toggle a session-level demo indicator.
- **Production stripping:** All demo-mode code and preset data are **removed** from production builds. In `vite.config.ts`, production builds use `define: { 'import.meta.env.VITE_DEMO_MODE': '"false"' }`, so any branch guarded by `isDemoMode` (or `import.meta.env.VITE_DEMO_MODE === 'true'`) is dead code and tree-shaken. Do not rely on runtime checks for demo features; use the compile-time flag so production bundles never contain demo logic or strings.
- **Presets:** `src/lib/demo-mode.ts` exports `isDemoMode` and typed preset arrays; they are only referenced from components that render when `isDemoMode && sessionDemoOn`, so they are eliminated in production.

## Contextual help

- **Shell:** `HelpProvider` wraps all routes (`routes.tsx`); the panel is a right-hand drawer (`HelpDrawer`). **`?`** opens help when focus is not in an input, textarea, select, or contenteditable.
- **Topics:** `HelpTopicId` and `resolveHelpTopicId()` live in `src/lib/help-topics.ts`. Copy is in the **`help`** i18n namespace (`src/locales/en/help.json`, `src/locales/fr/help.json`) under `topics.<id>.*` — **strict FR+EN parity** for every key you add.
- **UI:** Header includes a help icon; optional `HelpInlineButton` on specific pages (e.g. integrations list). Use `useHelp()` for `openHelp` / `closeHelp`.
- **Demo-only paragraphs:** optional `demoExtra` per topic; shown only when demo mode is on and the string is non-empty.

## Visual Identity: Neo-Brutalism (subtle)

- **Logo:** Primary fill `#3076DF` (EZKey blue). Source: repo root `logo.svg`. Admin UI copies and variants are generated by `scripts/prepare-admin-ui-brand-assets.py`: `public/logo.svg` (full colour), `public/favicon.svg` (32×32), `public/logo-sidebar.svg` (40×40, fill `#d6e6ff` for dark sidebar). Do not edit the generated SVGs by hand; re-run the script after changing the root logo.
- Background: `bg-bg` (#d6e6ff — light tint of EZKey blue #3076df)
- Surface (cards, inputs): `bg-surface` (#ffffff)
- Foreground: `text-fg` / `border-fg` (#1a1a1a)
- Accent: `text-accent` / `bg-accent` (#e85d04 burnt orange)
- Sidebar: `bg-sidebar-bg` (#12275c deep navy), `text-sidebar-fg` (#d6e6ff)
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
- Token in `sessionStorage` only (never `localStorage`). Optional **remember username** on the login page may store the username string in `localStorage` (`ezkey_admin_username_pref`); see `docs/admin-ui-security.md`.
- `nonBlocking: true` always on login requests
- Challenge codes always zero-padded to 2 digits via `formatChallengeCode()`
- Every authenticated page wrapped in `<AppShell title="...">`
