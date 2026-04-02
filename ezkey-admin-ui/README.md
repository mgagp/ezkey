# EZKey Admin UI

Primary web surface for EZKey human administration — supports both **Global Admins** and **Tenant Admins**. It allows admins to manage integrations, enrollments, admins, API keys, and audit logs. Global Admins additionally have access to platform-wide features such as tenant management, operational oversight, and encryption key management.

Calls the **EZKey Admin API** (port 9080). Role scoping is automatic — the bearer token determines access level, and all tenant-scoped resources are filtered server-side. Tenant Admin workflows stay focused on day-to-day tenant administration.

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

## Authentication

The app uses EZKey's passwordless authentication flow:

1. User submits username → Admin API returns an `authAttemptId` and optionally a challenge code
2. User approves the request on their EZKey mobile app (optionally entering the 2-digit challenge code)
3. The app polls `passwordless-wait` until the device responds
4. On success, the bearer token is stored in `sessionStorage` and the user is redirected to the dashboard

The token is validated on every read (expiration check). Any 401 response from the API automatically clears the session and redirects to login.

## Project Structure

See `AGENTS.md` for detailed conventions, patterns, and design rules used throughout the codebase.

## Security

- **Token storage**: The bearer token is stored in `sessionStorage` (never `localStorage`). It is tab-isolated and cleared when the tab closes. The app uses React JSX throughout (no `dangerouslySetInnerHTML` for app content).
- **Operational detail**: **`npm run dev`** (Vite) does not apply the same browser headers as production. **CSP and security headers** are enforced in the **Docker + Caddy** path (`./start.sh` — see `docker/Caddyfile`).

Canonical documentation (workflows, CSP, split UI/API, mkcert, future HttpOnly cookies, `clean-start` + Caddy defaults):

- [`docs/admin-ui-security.md`](../docs/admin-ui-security.md)
