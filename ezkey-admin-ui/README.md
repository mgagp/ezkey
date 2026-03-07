# EZKey Admin UI

Web-based SPA for EZKey administrators — supports both **Global Admins** and **Tenant Admins**. Allows admins to manage integrations, enrollments, admins, API keys, and audit logs. Global Admins additionally have access to platform-wide features such as encryption key management.

Calls the **EZKey Admin API** (port 9080). Role scoping is automatic — the bearer token determines access level, and all tenant-scoped resources are filtered server-side.

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

## Security Notes

### Token storage

The bearer token is stored in `sessionStorage` (never `localStorage`). This means:

- The token is tab-isolated and cleared automatically when the tab is closed
- Bearer tokens in `Authorization` headers are immune to CSRF attacks
- The app uses React JSX rendering throughout (no `dangerouslySetInnerHTML`), which neutralizes most XSS injection vectors

### Content Security Policy (CSP) — production recommendation

Before deploying to production, configure a strict CSP on the reverse proxy serving the app. This is the most impactful defense against XSS-based token theft, as it prevents injected scripts from exfiltrating data even if a supply-chain compromise occurs.

Recommended CSP for the nginx/Caddy/reverse proxy config:

```
Content-Security-Policy:
  default-src 'self';
  script-src 'self';
  connect-src 'self' https://<admin-api-domain>;
  img-src 'self' data: blob:;
  style-src 'self' 'unsafe-inline';
  frame-ancestors 'none';
```

Key directives:
- `script-src 'self'` — blocks inline scripts and third-party script injection
- `connect-src 'self' <api>` — prevents token exfiltration to attacker-controlled domains
- `frame-ancestors 'none'` — protects against clickjacking

> In development, CSP is not enforced. The meta-tag approach can be used for local testing if needed.
