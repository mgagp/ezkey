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
- Orval **8.24.0** (exact pin) — OpenAPI → TanStack Query client (`npm run generate:api` → `src/generated/admin-api/`). Treat later minors as a new validation ladder (regenerate + build/test/lint). **`orval.config.ts` must not set global `useQuery` or `useMutation`** — Orval 8.10+ applies explicit globals to all HTTP verbs and breaks hook shapes; keep `query: { version: 5 }` only (verb-aware defaults: GET → query, mutations → `useMutation`). Migration history: [`product-docs/global/backlog/TB-2026-05-28-admin-ui-orval-upgrade.md`](../product-docs/global/backlog/TB-2026-05-28-admin-ui-orval-upgrade.md).
- **Install scripts (npm v12 prep):** `package.json` `allowScripts` approves `esbuild@0.28.1` (Vite + Orval transitive dep; native binary postinstall). After dependency changes, run `npm approve-scripts --allow-scripts-pending` if npm warns about uncovered scripts.
- React Hook Form + Zod — forms and validation
- Fetch API — no axios; always use `api.*` from `@/lib/api-client`

## Path Alias

`@/` maps to `src/`. Use it for all cross-directory imports.

## Project Structure

```
src/
  lib/
    api-client.ts       Fetch wrapper: injects Bearer/credentials; on 401, session calls (`requireAuth` and no recovery `bearerToken`) clear local auth and `replace('/login')`. Login/public (`requireAuth: false`) and recovery `bearerToken` paths throw without redirect. getApiErrorMessage()
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
    data-table/         DataTable<T>, Pagination, PaginatedTable (top+bottom)
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
raw `useQuery` calls when you truly need a one-off page envelope.

### Dashboard overview

- Prefer `GET /api/v1/dashboard/overview` (Orval/query) for landing stats — do **not** reintroduce
  parallel `size=1` list calls just to read `totalElements`.
- Badge / headline / drilldown semantics:
  [`product-docs/components/admin-ui/dashboard-widget-signal-model.md`](../product-docs/components/admin-ui/dashboard-widget-signal-model.md)
  (`I-2026-0030`).

### Paginated list (standard pattern)

Prefer **`usePaginatedFromOrval`** + **`PaginatedTable`** for operator lists (see
[`docs/LIST_DATA_LOADING_DESIGN.md`](docs/LIST_DATA_LOADING_DESIGN.md)). `PaginatedTable` renders
the same `<Pagination />` **above and below** the table. `Pagination` already uses icon+text,
`aria-label`, and `Tooltip` from `common.pagination.*` — do not reintroduce bottom-only bars or
icon-only controls without labels.

```ts
const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<Integration, …>({
  queryKey: ['integrations', filter],
  // … Orval list fn + params — see LIST_DATA_LOADING_DESIGN.md
});
```

```tsx
<PaginatedTable
  columns={columns}
  data={data}
  isLoading={isLoading}
  currentSort={pagination.sort}
  onSort={pagination.setSort}
  pagination={pagination}
/>
```

Use raw `<DataTable />` + `<Pagination />` only for special layouts (e.g. dual controls already
composed by hand). Default new list screens to `PaginatedTable`.

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

For any screen that uses a **paginated** list API (e.g. `usePaginatedFromOrval` + `listTenants`, `search` integrations, etc.), **sort must be sent to the backend**. The API accepts a `sort` parameter and returns the current page of the **globally** ordered result set. Do **not** implement client-side-only sort (e.g. sorting only the current page in memory): that would reorder just the visible segment and mislead users who assume "sort by name" applies to the full list. Wire `currentSort` and `onSort` from the pagination hook into **`PaginatedTable`** (or `DataTable` if composing manually) so that clicking a sortable column triggers a new request with the updated `sort` param. If the backend does not support sort for a given list, do not add a `sortKey` to that column.

Clicking a new column sorts DESC by default; clicking the same column toggles ASC ↔ DESC.
The active column shows `↑` (ASC) or `↓` (DESC); inactive sortable columns show `⇅`.

### List quick security actions (trailing column)

Some paginated lists include a **right-edge actions column** for incident-relevant controls (API
key **Revoke**, admin **Deactivate**). Do not remove these for column-count hygiene when the canon
says they apply. Enrollment list inline deactivate is planned; integration **Retire** and tenant
deactivate stay detail-only.

Authoritative eligibility matrix:
[`product-docs/global/admin-ui-list-quick-security-actions.md`](../product-docs/global/admin-ui-list-quick-security-actions.md).
**Icon + tooltip** when space is tight and intent is unambiguous (revoke); **labeled button** when
wording disambiguates reversible suspend (deactivate).

### List refresh after mutations

After a successful create/edit/delete that affects a list, **invalidate that list's query key** so the list refetches and stays in sync. Use the **same key** as the list query so `invalidateQueries` targets the right cache.

- **usePaginatedFromOrval / useQuery with custom key** (standard for operator lists, including tenants): use the same prefix you pass as `queryKey` (e.g. `['tenants']`, `['integrations']`, `['enrollments']`). You can use `queryKeys` from `@/lib/query-keys` for consistency.
- **Raw Orval-generated list hooks** (rare; path-based keys such as `['/api/v1/…']`): invalidate with the **generated query key factory** from the same module. Invalidating a short custom prefix like `['tenants']` will not match Orval's path key. Operator lists should use `usePaginatedFromOrval` instead — see `docs/LIST_DATA_LOADING_DESIGN.md`.
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

The login form also offers an optional **pin toggle** beside the username field (tooltip + `aria-pressed`) for remembering the username on this device — see `last-username-pref.ts`, `login.tsx`, and [`docs/admin-ui-security.md`](../docs/admin-ui-security.md) § Last username preference.

### Recovery funnel (login page)

Recovery codes are a **secondary** path on `/login` (`LoginRecoverySection`), not a full admin session. Flow: `POST /api/v1/admin/auth/recover` → temporary `ezkey_recovery_*` token → `POST /api/v1/admin/enrollments/reset` → bind new device → normal passwordless login. Canon: [`docs/ADMIN_UI_RECOVERY.md`](../docs/ADMIN_UI_RECOVERY.md). Do not treat the recovery token as a console bearer.

### Instance branding (public instance-info)

Login, sidebar, and header About use unauthenticated `GET /api/v1/public/instance-info`
(`usePublicInstanceInfo`) — `instanceName`, `instanceDescription`, `aboutUrl`,
`authApiPublicBaseUrl` from `ezkey.organization.*` / `ezkey.qr.auth-base-url`. Bootstrap applies
organization name/description to system tenant/integration display names (flags identify system
rows, not the literal `"Ezkey System"`). Client-generated recovery QR must keep
`VITE_QR_AUTH_BASE_URL` aligned with server `ezkey.qr.auth-base-url` — see
[`docs/ADMIN_UI_RECOVERY.md`](../docs/ADMIN_UI_RECOVERY.md).

### Role-based feature gating

```ts
const { session } = useAuth();
const isGlobalAdmin = session?.adminType === 'GLOBAL_ADMIN';
// Example: show Deactivate button only for Global Admin
{isGlobalAdmin && r.active && <Button ...>Deactivate</Button>}
```

### Create administrator (tenant assignment)

- **Global Admin → Tenant Admin:** the Create Admin dialog must require a **tenant** selector and
  send `tenantId` on `POST /api/v1/admins/tenant`. Exclude the **system tenant**
  (`isSystemTenant`). If no eligible tenants exist, show create-tenant guidance and disable submit.
- **Tenant Admin → peer:** do **not** show a tenant selector; omit `tenantId` so the API uses the
  session tenant.
- **Deep link:** tenant detail can open `/admins?tenantId=…&createTenantAdmin=1` to prefill.
- **MFA enrollment id:** list/get/update admin responses include `enrollmentId` (passwordless
  identity FK — not a secret). Admin detail links via `EnrollmentFkLink` to `/enrollments/{id}`;
  `GET` enrollment still goes through access control.

### Test Authentication (enrollment-detail pattern)

Trigger a live auth attempt for a VERIFIED enrollment to confirm device binding:
1. `POST /api/v1/auth-attempts` — `{ enrollmentId, challengeRequested }`
2. Poll `GET /api/v1/auth-attempts/{id}` every 3s with `refetchInterval`
3. Polling stops on final status: `ACCEPTED | REJECTED | EXPIRED | INVALID`
4. Cancel via `POST /api/v1/auth-attempts/{id}/cancel`

### Enrollment detail / metadata vs lifecycle

Attribute inventory (what GET shows, what PATCH may edit, what stays workflow-bound):
[`docs/ENROLLMENT_ADMIN_UI_ATTRIBUTE_MATRIX.md`](../docs/ENROLLMENT_ADMIN_UI_ATTRIBUTE_MATRIX.md).
Non-admin device replacement is **revoke + create** a new enrollment — no dedicated rebind API
([`docs/ENROLLMENT_WORKFLOW_GAPS.md`](../docs/ENROLLMENT_WORKFLOW_GAPS.md)). Do not expand PATCH into
crypto/lifecycle fields.

### Logout

`logout()` in `AuthProvider` calls `queryClient.clear()` before clearing the session —
prevents stale data from a previous user appearing on the next login.

### Adding a new screen

1. Create `src/pages/my-screen.tsx` → `export default function MyScreenPage()`
2. Wrap content in `<AppShell title="Screen Name">`
3. Add a lazy route in `routes.tsx`
4. For list views: `usePaginatedFromOrval` + `PaginatedTable` (see § Paginated list)

### Tier A list conventions (ID column + cross-links)

Canonical matrix: [`product-docs/global/admin-ui-paginated-screens-matrix.md`](../product-docs/global/admin-ui-paginated-screens-matrix.md).

- **ID first:** bounded Tier A paginated lists show the entity primary key as the **first column** (`font-mono text-xs`, server `sortKey` when supported).
- **Labels alongside ID:** join-enriched names (tenant, integration, username) live in their business columns; they do not replace the ID column.
- **Administrator links:** there is **no** `/admins/:id` route. Detail opens on `/admins` with `?adminId=`. Use `adminListDetailHref(id)` from `@/lib/list-detail-navigation` for every cross-screen link to an admin (audit logs, enrollment detail, tenant embedded list, etc.).

### Prev/Next detail navigation

When opening detail from a list (investigation / sequential review), reuse the shared pattern — do not invent ad-hoc arrow handlers:

- **Modals:** `useDetailNavigation` (←/→ when focus is not in an editable field) + header chevrons (`DetailDialogHeaderNav` where used). Scope is the **current page** of results.
- **Full-page detail:** pass list context via `buildListDetailNavState` / `list-detail-navigation`; consume with `useListDetailPageNavigation` + `DetailPageNav`.
- **Audit detail stability** (fixed field heights, FK button reserve, `lg-wide`): [`docs/admin-ui/DETAIL_DIALOG_STABLE_LAYOUT.md`](../docs/admin-ui/DETAIL_DIALOG_STABLE_LAYOUT.md).

### Dialog (modal) — dismissible

The shared `Dialog` component (`@/components/ui/dialog`) accepts `dismissible` (default `true`). When `dismissible={false}`, backdrop click and Escape do **not** close the dialog; only the close button (X) and explicit actions (Cancel, Submit, Done) do. Optional `size`: `sm` | `md` | `lg` | `lg-wide` (the last is ~26.5% wider than `lg`, for content-heavy read-only modals such as audit log detail).

- **Use `dismissible={false}`** for any dialog that contains a **form** (create/edit) or **critical state** (e.g. secret shown once, live test in progress). This prevents accidental data loss when the user clicks outside.
- **Leave default** (`dismissible` unspecified) for read-only detail dialogs and simple confirmations (yes/no, no form fields).

### Button placement (list toolbar and dialogs)

- **List toolbar:** The primary action (Create, New, Rotate Key, etc.) is always on the **right**. Put filters, search, and Refresh on the left; use `ml-auto` on the primary action button (or a right group with `justify-between`) so it stays right-aligned.
- **Dialogs:** Cancel (or secondary) on the left, primary Submit/Create on the right. Use `flex justify-end gap-2` (or `justify-end pt-2`) for dialog footers.

### Date range filters (list / integrity)

When a screen filters by time via Admin API `createdAfter` / `createdBefore` (or the same ISO pair for integrity windows), use shared **`DateRangeFilter`** (`@/components/ui/date-range-filter`) and **`date-range-presets`** (`@/lib/date-range-presets`). Resolve named presets (Today, Yesterday, Last 7/30 days rolling, etc.) in the UI only — do not invent backend period keywords. See existing call sites on Auth Attempts, Audit Logs, Encryption Keys, and **Re-encryption batches** (same Encryption Keys page; server filters + `usePaginatedFromOrval` — ops contract in [`docs/REENCRYPTION_OPERATIONS.md`](../docs/REENCRYPTION_OPERATIONS.md) §7).

## Docker Deployment

Use **start.sh** to build and run the Admin UI in Docker. Keep this path **standalone**
(`docker-compose.admin-ui.yml` + `docker/Dockerfile`) — do **not** fold the Node/Vite build into
the main Java `docker/docker-compose.yml` / daily `clean-start` (would slow every backend cycle).

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

- Agent validation ladder (build → API → Playwright): [`docs/testing/AGENT_UI_VALIDATION.md`](../../docs/testing/AGENT_UI_VALIDATION.md)
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
- **Same operating model as Java `java-doctor-curated`:** curator shortlist → small lot → interactive HITL → campaign note → hygiene branch + PR. Sibling lane: root `AGENTS.md` § Java doctor-curated.
- **Trace (hygiene, not program):** do **not** create `I-*` / `TB-*` / `TSP-*` for a routine doctor-curated cleanup. Prefer a **dedicated branch + PR** (optional GitHub issue only if board visibility helps). Aligns with root `AGENTS.md` lightweight hygiene workflow and `product-docs/methodology/README.md` § *Three rules worth keeping*.
- **Campaign decision notes (HITL):** `product-docs/global/hygiene/react-doctor/` (template + dated pass instances). Do **not** invent `I-*` / `TB-*` / GitHub issues per finding.
- Default commands from `ezkey-admin-ui/`:
  - `npm run doctor:curated`
- Output lives under `logs/react-doctor/`:
  - `react-doctor.raw.json` — full raw tool output
  - `react-doctor.curated.json` — filtered summary for follow-up analysis
  - `react-doctor.curated.md` — human-readable shortlist
  - `react-doctor.stderr.log` — CLI warnings / stderr
- Current low-signal suppressions in the curated pass (`SUPPRESSED_RULES` in `scripts/doctor-curated.mjs`):
  - `unused-file`
  - `design-no-em-dash-in-jsx-text`
  - `only-export-components`
  - `no-impure-state-updater`
- Working rule for humans: treat the curated report as a **triage aid**. The goal is to identify a few high-signal improvements with strong signal-to-effort ratio and stop before diminishing returns.
- **Triage balance — high signal first, modest low-signal allotment:** prioritize findings with strong signal-to-effort ratio (correctness, a11y on shared surfaces, real operator-visible bugs). After that high-signal set is chosen (or confirmed empty / deferred for design reasons), **reserve a small continuous-improvement slot** for cheap, low-risk P2/P3 items (e.g. Intl hoist/cache, lazy `useState` init, single-pass list transforms, dead exports in touched files). Absolute value may be low, but skipping them forever means the backlog never climbs. Keep that slot **modest** (typically 1–3 items, local diffs, no design migrations). Do **not** expand it into `prefer-useReducer`, giant-component splits, or native `<dialog>` rewrites unless the operator widens scope.

### HITL contract (mandatory for cold agents)

When the operator asks for a **`doctor-curated`** improvement pass:

1. Run the script; read `logs/react-doctor/react-doctor.curated.md`.
2. Propose a **small prioritized lot** (usually 3–6 items, including any modest low-signal allotment), not a zero-warning campaign. A short numbered **overview** of the lot is fine (rule + location hint only). State clearly what is **in** vs **out** of the pass (challenge design-decision items such as wholesale `<dialog>` migrations).
3. **Before any code change — interactive HITL loop (mandatory):** do **not** replace the dialogue with one dense options matrix that asks for a bulk reply (`1A, 2B, 3B…`). After the overview, **iterate explicitly, one finding at a time**. Nest the **maintainer continuous-learning briefing** inside each item turn (the maintainer is **not** a UI specialist): what the rule is complaining about, where it shows up in this codebase, why it matters in Ezkey Admin UI (or why leave it), options, open question — then **wait** for the operator’s Go / No-Go / suppress / skip / clarifying questions on **that** item before presenting the next. Keep each turn short (a few sentences). Prefer concrete Admin UI surfaces (DataTable, Dialog, audit logs, pagination) over generic framework theory. A compact decision table may appear later in the **campaign note** after decisions are made — not as the primary briefing.
4. **Fuzzy signal rule:** if the finding cannot be tied clearly to source (opaque tooling noise, unreachable-file heuristics without a clear locus), **skip** — do not invent a problem. Prefer suppress-with-reason only when the pattern is understood and intentionally accepted.
5. **If it ain't broken, don't fix it:** when diagnosis is **clear** but the flagged code is an intentional or harmless local pattern, **leave the code** and suppress with reason in `SUPPRESSED_RULES`. Clarity of the finding does **not** oblige a rewrite.
6. Record decisions in a dated campaign note under `product-docs/global/hygiene/react-doctor/` (copy `TEMPLATE.md`). New machine-facing suppressions go in `SUPPRESSED_RULES` inside `scripts/doctor-curated.mjs`.
7. Only then implement accepted fixes on a **dedicated hygiene branch + PR**. **PR body carries the briefing:** reuse the same short per-item learning text (Summary + “Why these fixes”); **link the campaign note**. Do not invent a second long write-up. Keep the PR pragmatic: what changed, why it matters in Admin UI, what was deferred, and a minimal test plan. Modest low-signal allotment after high-signal items is allowed when the operator agrees.

Unless the operator explicitly asks to skip the briefing/HITL, do not jump straight to implementation.

### Cursor IDE browser (MCP) spot checks

Agents using the embedded browser tools should mirror the **same sequencing as Playwright**, not invent a parallel protocol. Full recipe + pitfalls:
[`docs/testing/AGENT_UI_VALIDATION.md`](../docs/testing/AGENT_UI_VALIDATION.md) § *Step 4 — MCP browser*.

- After `browser_navigate`, take **`browser_snapshot` with `interactive: true`** when you need clickable element refs; a non-interactive snapshot can look nearly empty on first paint. Re-snapshot after Demo Device route changes — the a11y tree can lag the screenshot.
- **Hostname:** use **`http://localhost:<port>`** for Admin UI and Demo Device in the Cursor browser. On Windows, Vite often listens on **IPv6 `::1` only**; `http://127.0.0.1:5173` then returns `ERR_CONNECTION_REFUSED` even when the app is up. Confirm `npm run dev` is running and note the printed port if not 5173.
- **Passwordless login + Demo Device** (mirror `e2e/support/auth-flow.ts`):
  1. Admin UI `/login` → username **`admin.docker`** → submit → waiting state (`login-waiting-state`).
  2. New tab → `http://localhost:8083/phone/ezkey` (if `about:blank`, lock tab and navigate again).
  3. Open the **`admin.docker`** enrollment link → auth page; reload until **Approve** / **Deny**.
  4. **Approve** → confirm success → click `[data-testid="demo-device-back-to-enrollments"]` (primary **Back to Enrollments**; not the side-exit).
  5. Admin UI should land on `/dashboard`. **Logout** when the smoke is complete.
- Prefer `data-testid` over locale-specific labels (`Login with EZKey` / `Connexion avec EZKey`).
- URLs: Admin UI (`EZKEY_ADMIN_UI_URL`, often `http://localhost:5173` or preview `http://localhost:4173`), Demo Device (`EZKEY_DEMO_DEVICE_URL`, default `http://localhost:8083`), bootstrap admin **`admin.docker`** (`EZKEY_ADMIN_UI_TEST_USERNAME`).
- Rare empty pending while Admin UI countdown still runs: Cancel → restart login → reload Demo Device auth immediately. Do not treat infrequent races as a standing bug-hunt.
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
- **Full write-up** (two-path model, `clean-start` + Caddy default, split UI/API, mkcert, Mode A Bearer vs Mode B HttpOnly cookie): [`docs/admin-ui-security.md`](../docs/admin-ui-security.md)
- **Mode B (cookie builds):** after refresh, restore via `GET /api/v1/admin/auth/me` (`auth-context`); opaque token stays HttpOnly — never put it in JS storage. `fetchApi` sends `credentials: 'include'` and `X-CSRF-TOKEN` on unsafe methods; Bearer/recovery paths remain. Do not weaken that split.
- **How to validate** (DevTools, `curl`, first-session checklist): [`docs/admin-ui-security-validation.md`](../docs/admin-ui-security-validation.md)

## Developer/Demo mode

- **Dev-only:** When `VITE_DEMO_MODE=true` (e.g. in `.env.development`), the UI can show "Fill demo" controls in create dialogs (tenant, integration, enrollment, admin) and allow Ctrl+click on the sidebar brand to toggle a session-level demo indicator.
- **Production stripping:** All demo-mode code and preset data are **removed** from production builds. In `vite.config.ts`, production builds use `define: { 'import.meta.env.VITE_DEMO_MODE': '"false"' }`, so any branch guarded by `isDemoMode` (or `import.meta.env.VITE_DEMO_MODE === 'true'`) is dead code and tree-shaken. Do not rely on runtime checks for demo features; use the compile-time flag so production bundles never contain demo logic or strings.
- **Presets:** `src/lib/demo-mode.ts` exports `isDemoMode` and typed preset arrays; they are only referenced from components that render when `isDemoMode && sessionDemoOn`, so they are eliminated in production.
- **Form placeholder theme:** Create-form example values (i18n `*Placeholder` keys) use the light **Garage du coin** thematic — tenant/org examples and `garageducoin.ca`; admins/enrollments use **Marie Dupont**. Prefer names and brand hints only (no long “luxury garage” copy). Do not reintroduce Acme-style placeholders; match existing `locales/*/tenants|admins|enrollments.json` and `demo-mode.ts`.

## Contextual help

- **Shell:** `HelpProvider` wraps all routes (`routes.tsx`); the panel is a right-hand drawer (`HelpDrawer`). **`?`** opens help when focus is not in an input, textarea, select, or contenteditable.
- **Topics:** `HelpTopicId` and `resolveHelpTopicId()` live in `src/lib/help-topics.ts`. Copy is in the **`help`** i18n namespace (`src/locales/en/help.json`, `src/locales/fr/help.json`) under `topics.<id>.*` — **strict FR+EN parity** for every key you add.
- **UI:** Header includes a help icon; optional `HelpInlineButton` on specific pages (e.g. integrations list). Use `useHelp()` for `openHelp` / `closeHelp`.
- **Demo-only paragraphs:** optional `demoExtra` per topic; shown only when demo mode is on and the string is non-empty.
- **Three help surfaces:** hover `Tooltip` (`components/ui/tooltip.tsx`) for one-sentence domain terms; click `ContextHelp` for short section concepts; `HelpDrawer` for topic-scale guidance. Explain Ezkey vocabulary (statuses, HMAC, key tiers) — not standard UI labels or labeled buttons. Tooltip/ContextHelp strings live in locale namespaces (`*help*` / `help.*` keys), same FR+EN parity rule.

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
