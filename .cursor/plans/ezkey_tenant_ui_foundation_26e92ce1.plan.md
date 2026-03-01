---
name: Ezkey Tenant UI Foundation
overview: "Bootstrap the ezkey-tenant-ui project: a React SPA for tenant administrators, with phased delivery starting from project scaffolding, visual identity, login flow, and mock dashboard with navigation."
todos:
  - id: scaffold
    content: Scaffold Vite + React + TypeScript project with package.json, tsconfig, vite.config
    status: completed
  - id: tailwind-shadcn
    content: Install and configure Tailwind CSS v4 + shadcn/ui with neo-brutalism theme tokens
    status: in_progress
  - id: agents-md
    content: Create AGENTS.md for ezkey-tenant-ui with project conventions and patterns
    status: pending
  - id: api-client
    content: Implement lib/api-client.ts (fetch wrapper with auth) and lib/auth.ts (token management)
    status: pending
  - id: auth-flow
    content: Build login page with passwordless flow (username -> challenge code -> polling -> token)
    status: pending
  - id: app-shell
    content: Build app shell layout (sidebar navigation, header with tenant/user info, content area)
    status: pending
  - id: pagination
    content: Implement reusable usePaginatedQuery hook and DataTable + Pagination components
    status: pending
  - id: mock-dashboard
    content: Create dashboard page with mock widget cards (integrations, enrollments, auth attempts, activity)
    status: pending
  - id: routing
    content: Configure React Router with protected routes, login redirect, and placeholder pages for all nav items
    status: pending
isProject: false
---

# Ezkey Tenant UI -- Foundation Plan

## Context

The TUI (`ezkey-cli-python`) serves Global Admins well (flat, keyboard-driven, one screen per controller). But Tenant Admins are business-oriented users who need a traditional web UI. The `ezkey-tenant-ui` fills this gap: a web-based SPA scoped to tenant administration, calling the existing Admin API (`localhost:9080`).

The backend already handles tenant scoping automatically -- a Tenant Admin's bearer token restricts visibility to their own tenant's resources. The UI just needs to authenticate, store the token, and call the API.

## Stack (confirmed)

- **Vite** -- build tool
- **React 19 + TypeScript** -- UI framework
- **React Router v7** -- routing
- **Tailwind CSS v4** -- styling
- **shadcn/ui** -- component library (copy-to-repo pattern)
- **TanStack Query v5** -- data fetching, caching, pagination
- **React Hook Form + Zod** -- forms and validation
- **Fetch API** -- HTTP client (no axios needed)

## Visual Identity: Subtle Neo-Brutalism

The neo-brutalism approach works well for an admin tool that needs personality without distraction:
- Thick borders (2-3px) on cards and primary actions, with slight box-shadow offsets
- High contrast text, minimal color palette (monochrome base + 1 accent color)
- No rounded corners on primary containers (sharp edges), but `rounded-sm` on buttons/inputs
- Flat backgrounds, no gradients
- One or two "candy" elements: a colored accent bar on the sidebar, bold headings in a slightly playful font weight
- Overall: clean, professional, with just enough edge to feel modern and distinct

## Project Location

New directory: `ezkey-tenant-ui/` at the workspace root (sibling to `ezkey-admin-api`, `ezkey-core`, etc.). This is a standalone Node.js project, not a Maven module -- it has its own `package.json` and runs independently.

## Phased Delivery

### Phase 1 -- Foundation (this plan)

Deliverables:
1. Project scaffolding (Vite + React + TS)
2. Tailwind CSS + shadcn/ui setup
3. AGENTS.md for the project
4. Visual identity (theme, layout shell)
5. Auth module (login screen + token management)
6. App shell (sidebar nav, header, layout)
7. Mock dashboard with placeholder widgets
8. Reusable data table + pagination primitives

### Phase 2 -- Dashboard + First Screens (next iteration)

- Live dashboard widgets (stats from API)
- Integrations list + detail
- Enrollments list + detail + create

### Phase 3+ -- Remaining Screens (iterative)

- Admin management (list, create tenant admin, onboarding)
- Auth attempts (list, detail -- read-only)
- Audit logs (list, filters -- read-only)
- API keys (list, create, revoke)

---

## Phase 1 Detail

### 1. Project Structure

```
ezkey-tenant-ui/
  AGENTS.md
  index.html
  package.json
  tsconfig.json
  vite.config.ts
  tailwind.config.ts
  postcss.config.js
  .env.development          # VITE_API_BASE_URL=http://localhost:9080
  .env.production
  src/
    main.tsx                 # Entry point
    App.tsx                  # Router + providers
    vite-env.d.ts
    lib/
      api-client.ts          # Fetch wrapper with auth interceptor
      auth.ts                # Token storage (sessionStorage), login/logout
      query-client.ts        # TanStack Query client config
      utils.ts               # cn() helper, date formatting
    hooks/
      use-auth.ts            # Auth context hook
      use-paginated-query.ts # Reusable paginated TanStack Query hook
    types/
      api.ts                 # API response types (Page<T>, error shapes)
      models.ts              # Domain models (Integration, Enrollment, etc.)
    components/
      ui/                    # shadcn/ui components (Button, Card, Input, etc.)
      layout/
        app-shell.tsx        # Sidebar + header + main content area
        sidebar.tsx          # Navigation menu
        header.tsx           # Top bar (tenant name, user, logout)
      data-table/
        data-table.tsx       # Reusable table with column definitions
        pagination.tsx       # Page controls (prev/next, page size, total)
    pages/
      login.tsx              # Login page
      dashboard.tsx          # Dashboard with widget grid
      not-found.tsx          # 404
    routes.tsx               # Route definitions
```

### 2. Auth Flow

The login flow mirrors the TUI's passwordless authentication:

```
User enters username
  -> POST /api/v1/admin/auth/login
  -> Backend creates auth attempt on system integration
  -> UI shows challenge code (6-digit number)
  -> UI polls POST /api/v1/admin/auth/passwordless-wait
  -> User approves on mobile device
  -> API returns bearer token
  -> Token stored in sessionStorage
  -> Redirect to /dashboard
```

Key implementation points:
- Token stored in `sessionStorage` (cleared on tab close -- security by default)
- All API calls via a `fetchApi()` wrapper that injects `Authorization: Bearer <token>`
- On 401 response, redirect to `/login`
- Auth state managed via React context (`useAuth` hook)

### 3. API Client (`lib/api-client.ts`)

A thin `fetchApi()` wrapper around native `fetch`:
- Automatically prepends `VITE_API_BASE_URL`
- Injects `Authorization: Bearer <token>` header from session
- Parses JSON responses
- On 401: clears token, redirects to login
- Returns typed responses using generics

### 4. Reusable Pagination Hook (`hooks/use-paginated-query.ts`)

The Admin API uses Spring Data's `Page<T>` structure consistently across all list endpoints:

```typescript
interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;       // current page (0-based)
  size: number;
  first: boolean;
  last: boolean;
}
```

The `usePaginatedQuery` hook wraps TanStack Query's `useQuery` with:
- Page state management (page, size, sort)
- Query key includes pagination params (auto-refetch on change)
- Returns `{ data, pagination, isLoading, error }` where `pagination` has `goToPage()`, `nextPage()`, `prevPage()`, `setPageSize()`

### 5. Reusable Data Table (`components/data-table/`)

A `<DataTable>` component that:
- Accepts column definitions (header, accessor, render)
- Accepts `PageResponse<T>` data
- Renders the table body
- Composes with `<Pagination>` component below
- Supports row click handler (for master/detail navigation)
- Styled with neo-brutalism: thick top border, no zebra stripes, bold headers

### 6. App Shell Layout

```
+--[ Sidebar ]--+--[ Header: Tenant name | User | Logout ]--+
|               |                                             |
| Dashboard     |  [ Main Content Area ]                     |
| Integrations  |                                             |
| Enrollments   |                                             |
| Auth Attempts |                                             |
| Audit Logs    |                                             |
| Admins        |                                             |
| API Keys      |                                             |
|               |                                             |
+---------------+---------------------------------------------+
```

- Sidebar: fixed width, dark background, accent bar on active item
- Header: tenant organization name, admin username, session indicator, logout
- Content: padded area with page title + content
- Neo-brutalism sidebar: thick left accent bar, bold nav labels, sharp edges

### 7. Mock Dashboard

A grid of 4-6 cards with static/mock data to validate the visual identity:

- **Integrations**: count active / total
- **Enrollments**: count by status (verified, bound, created)
- **Auth Attempts (24h)**: total, accepted, rejected, pending
- **Recent Activity**: 5 most recent audit log entries (placeholder)

Each card styled with neo-brutalism: thick border, slight offset shadow, bold title.

### 8. AGENTS.md

A concise reference for AI agents working in this module, covering:
- Project purpose and audience (Tenant Admin web UI)
- Stack summary
- Project structure conventions
- API base URL and auth pattern
- Naming conventions (components PascalCase, hooks use-*, pages lowercase)
- Visual identity guidelines (neo-brutalism rules)
- Key patterns (usePaginatedQuery, fetchApi, DataTable)
