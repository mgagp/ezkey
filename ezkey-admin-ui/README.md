# EZKey Admin UI

Primary web surface for EZKey human administration — supports both **Global Admins** and **Tenant Admins**. It allows admins to manage integrations, enrollments, admins, API keys, and audit logs. Global Admins additionally have access to platform-wide features such as tenant management, operational oversight, and encryption key management.

Calls the **EZKey Admin API** (port 9080). Role scoping is automatic — the bearer token determines access level, and all tenant-scoped resources are filtered server-side. Tenant Admin workflows stay focused on day-to-day tenant administration.

## Documentation

Use the central Admin UI background document for operator-facing product context:

- [`../docs/ADMIN_UI.md`](../docs/ADMIN_UI.md) - purpose, roles, capability map, workflows, and documentation boundaries

Use this module README for frontend setup, local development, and UI-specific implementation notes.

## Stack

- Vite 7 + React 19 + TypeScript (strict)
- React Router v7 — SPA routing
- Tailwind CSS v4 — utility-first styling
- TanStack Query v5 — data fetching and caching
- React Hook Form + Zod — forms and validation

## Getting Started

### Prerequisites

- Node.js 20+
- EZKey Admin API running on port 9080

### Development

```bash
npm install
npm run dev
```

The dev server starts on `http://localhost:5173`. API calls are proxied to `http://localhost:9080` via the Vite proxy config (see `vite.config.ts`).

### Production build

```bash
npm run build
```

Output is in `dist/`. Serve it behind a reverse proxy (nginx, Caddy, etc.) that also handles API routing to the Admin API.

Set `VITE_API_BASE_URL` in `.env.production` to the Admin API base URL if the UI and API are not served from the same origin.

### Cloudflare Pages (split UI / API)

For a static deploy where the Admin API is a separate HTTPS origin (e.g. `https://exp1-admin-api.ezkey.org`):

```bash
npm ci
npm run build:cloudflare
```

This uses [`.env.cloudflare`](.env.cloudflare) (`vite --mode cloudflare`) to embed the API base URL at build time. Output is still `dist/`, including [`public/_redirects`](public/_redirects) for SPA routing on Pages.

Operator runbook (CORS on the API VM, Wrangler deploy, edge CSP): [`../docs/cloudflare/admin-ui-pages.md`](../docs/cloudflare/admin-ui-pages.md).

## Browser Tests

The Admin UI includes a **Playwright** browser suite for representative end-to-end validation against the
real EZKey stack.

### Baseline assumptions

- Start the EZKey backend stack with the standard clean start so Admin API, Auth API, Crypto API, and
  the **Demo Device** are available.
- The clean-start stack pre-seeds the Demo Device, so the browser tests can drive a real passwordless
  login flow through the Admin UI and approve or deny it on the Demo Device.
- The Admin UI runtime is still started separately from clean-start.

### Local developer path

Use the Vite dev server and let Playwright start it automatically:

```bash
./scripts/run-ui-tests.sh
```

Useful variants:

```bash
./scripts/run-ui-tests.sh --headed
./scripts/run-ui-tests.sh --grep @elective
```

Default URLs:

- Admin UI: `http://127.0.0.1:4173`
- Demo Device: `http://127.0.0.1:8083`

Optional Playwright env:

- `EZKEY_DEMO_DEVICE_TEST_ENROLLMENT_ID` — select a specific Demo Device enrollment row (matches `data-enrollment-id` on the home list). Use when multiple enrollments exist and `.first()` would be non-deterministic.

### Docker-only QA path

Use Docker as the only host dependency for the browser runner. This path builds the Admin UI container,
then runs Playwright inside a dedicated Docker image:

```bash
./scripts/run-ui-tests-docker.sh
```

Useful variants:

```bash
./scripts/run-ui-tests-docker.sh --headed
./scripts/run-ui-tests-docker.sh --grep @elective
./scripts/run-ui-tests-docker.sh --skip-ui-start
```

Default URLs for the Docker runner:

- Admin UI: `http://host.docker.internal:3080`
- Demo Device: `http://host.docker.internal:8083`

### Artifacts

The browser suite writes concise and rich outputs for follow-up and debugging:

- Text summary: `test-results/browser/summary.txt`
- Run metadata: `test-results/browser/run-info.txt`
- Failure artifacts: `test-results/browser/artifacts/`
- HTML report: `playwright-report/`

### Scope

The initial suite stays intentionally small:

- unauthenticated shell checks
- real passwordless login through the Demo Device
- authenticated shell smoke
- one representative post-login workflow
- one elective rejection path

Fresh enrollment automation, broad CRUD coverage, and heavy instrumentation remain out of scope for the
first phase.

## Authentication

The app uses EZKey's passwordless authentication flow:

1. User submits username → Admin API returns an `authAttemptId` and optionally a challenge code
2. User approves the request on their EZKey mobile app (optionally entering the 2-digit challenge code)
3. The app polls `passwordless-wait` until the device responds
4. On success, the bearer token is stored in `sessionStorage` and the user is redirected to the dashboard

The token is validated on every read (expiration check). Any 401 response from the API automatically clears the session and redirects to login.

For the broader operator-facing explanation of how the Admin UI fits into Ezkey, when to use the UI versus the API, and which workflows matter most, see [`../docs/ADMIN_UI.md`](../docs/ADMIN_UI.md).

## Project Structure

See `AGENTS.md` for detailed conventions, patterns, and design rules used throughout the codebase.

## Security

- **Token storage**: The bearer token is stored in `sessionStorage` (never `localStorage`). It is tab-isolated and cleared when the tab closes. The app uses React JSX throughout (no `dangerouslySetInnerHTML` for app content).
- **Operational detail**: **`npm run dev`** (Vite) does not apply the same browser headers as production. **CSP and security headers** are enforced in the **Docker + Caddy** path (`./start.sh` — see `docker/Caddyfile`).

Canonical documentation (workflows, CSP, split UI/API, mkcert, future HttpOnly cookies, `clean-start` + Caddy defaults):

- [`docs/admin-ui-security.md`](../docs/admin-ui-security.md)
- [`../docs/ADMIN_UI.md`](../docs/ADMIN_UI.md)
