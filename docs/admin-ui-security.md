# Admin UI — security (token storage, HTTP headers, deployment)

This document implements the operational guidance from the security hardening plan for the Ezkey Admin UI. It is the canonical reference for **browser security headers**, **local vs QA workflows**, **split UI/API deployments**, **optional HTTPS (mkcert)**, and a **future HttpOnly cookie** model.

**New to this area?** Use **[admin-ui-security-validation.md](admin-ui-security-validation.md)** for a short **where to start** guide, **Path A vs B**, and a **step-by-step checklist** (DevTools, `curl`, Postman) to validate headers and token behavior without reading the full plan.

## Token storage and transport (current)

| Mechanism | Detail |
|-----------|--------|
| **Storage** | Opaque bearer token in **`sessionStorage`** (key `ezkey_admin_auth`), not `localStorage`. |
| **Transport** | `Authorization: Bearer` on API requests (`ezkey-admin-ui/src/lib/api-client.ts`). |
| **Cookies** | No session cookie today; login response returns the token in JSON. |

**Last username preference (optional)**

- If the user enables **remember username** via the **pin toggle** next to the username field on the login screen, the UI stores the **username string** in **`localStorage`** (key `ezkey_admin_username_pref`). This is a **non-secret identifier** only; it does not grant access without completing passwordless authentication. Tokens are **not** stored in `localStorage`.
- If the user leaves the option off, any previously saved username preference is removed when a login attempt successfully starts (same moment the in-app flow moves to device approval).

**Implications**

- **XSS**: Any script running in the page origin can read `sessionStorage`. **CSP** and safe rendering (React, no unsafe HTML) are the primary mitigations. **HttpOnly cookies** (future) reduce token theft via XSS if combined with a correct backend and deployment model.
- **CSRF**: Bearer tokens sent only from application code are not sent automatically on cross-site navigations like classic cookie sessions; CSRF patterns differ if you migrate to cookies.

## Where security headers are applied

### Path A — Daily development (solo)

- **`npm run dev`** in `ezkey-admin-ui` — Vite on `http://localhost:5173`, **hot module reload**.
- **`ezkey-tests/clean-start.sh`** (or equivalent) brings up **backend** services; the UI is **not** required inside Docker for this workflow.
- Vite does **not** emit the same response headers as production Caddy unless you add custom middleware. **This path optimizes velocity**, not header parity.

### Path B — QA / production-like (Docker + Caddy)

- **`ezkey-admin-ui/start.sh`** builds the SPA and serves it with **Caddy** (`docker/Caddyfile` in this module).
- **CSP**, **frame-ancestors** (via CSP), **Referrer-Policy**, **Permissions-Policy**, **X-Content-Type-Options**, **X-Frame-Options** are defined there for HTML and static assets.
- **`/api/*`** is reverse-proxied to the Admin API; minimal headers are applied to API responses (no HTML CSP required for JSON).

**Recommendation**: Validate header and CSP changes using **Path B** (or production/staging), not Path A alone.

### APIs behind Caddy in `clean-start` (default)

From **`ezkey-tests/clean-start.sh`**, **Caddy in front of APIs is enabled by default** (`docker/docker-compose.with-proxy.yml` via `docker/start.sh --with-proxy`). This applies baseline headers on the **API proxy** (`docker/caddy/Caddyfile`) on ports **19080** (Admin), **18080** (Auth), **17080** (Integration).

- Opt out: `./clean-start.sh --no-proxy` if you need a stack without the extra Caddy service.

For **which URL to use when** (direct `8080`/`9080`/… vs Caddy `18080`/`19080`/…), Postman environments, and why demos stay on **in-compose** service ports, see **[LOCAL_STACK_PORTS.md](LOCAL_STACK_PORTS.md)**.

## Content Security Policy (CSP)

The Admin UI **Caddyfile** sets an **enforced** CSP for the SPA, tuned for the Vite **production** build (scripts and styles from `'self'` only).

- **`connect-src 'self'`** is correct when the browser talks to the **same origin** and the app uses relative `/api/v1/...` URLs (Caddy proxies to the Admin API).
- If the UI and API are on **different origins** (e.g. UI on Cloudflare, API on AWS), extend **`connect-src`** to include the **API origin** (and any WebSocket origin if used later). Mirror the same policy on your edge (Cloudflare **Content-Security-Policy** transform or Workers).

**Report-only**: To debug violations without blocking, you can temporarily enable **`Content-Security-Policy-Report-Only`** with the same policy (see comments in `ezkey-admin-ui/docker/Caddyfile`). Use a reporting endpoint when you are ready to collect reports in production.

## Split deployment (UI and API on different hosts)

| Topic | Action |
|-------|--------|
| **CORS** | Admin API must allow the **browser origin** of the Admin UI (`Access-Control-Allow-Origin`, credentials if using cookies later). |
| **CSP `connect-src`** | Include the API base origin (scheme + host + port). |
| **Cookies (future)** | `SameSite`, `Secure`, and registrable domain rules depend on whether UI and API share a **site**; a **BFF** on one origin often simplifies this. |

## Iframe policy

The Admin UI is **not** intended to be embedded in iframes. Headers: **`frame-ancestors 'none'`** (CSP) and **`X-Frame-Options: DENY`**.

## HTTPS, HSTS, and mkcert

- **HTTP** (e.g. `http://localhost:3080` for Path B) is enough to test **most** headers; **HSTS** must only be sent on **HTTPS** responses.
- **`Strict-Transport-Security`** is commented in `ezkey-admin-ui/docker/Caddyfile` until TLS is terminated there.
- **mkcert** gives **locally trusted** certificates without browser warning spam. Typical flow: `mkcert -install`, then `mkcert localhost 127.0.0.1 ::1`, mount PEM files into the container or host Caddy, add a `tls` block, then enable the **`@https` HSTS** snippet in the Caddyfile.

For **Path A** with HTTPS **and** HMR, you can run **Caddy on the host** in front of Vite (`reverse_proxy` to `http://127.0.0.1:5173`) and configure Vite **`server.hmr`** for `wss` — optional and higher setup cost; most teams rely on Path B or staging for HTTPS parity.

## Future: HttpOnly session cookie (design sketch)

If you move from **Bearer in JSON + sessionStorage** to **HttpOnly** session cookies:

1. **Issue** the session via **`Set-Cookie`** from a **BFF** or Admin API response over **HTTPS** (`Secure`, `HttpOnly`, `SameSite` appropriate to your layout).
2. **Browser requests**: use **`fetch(..., { credentials: 'include' })`** and ensure **CORS** allows credentials and reflects the UI origin.
3. **Logout**: implement **server-side** session invalidation and clear the cookie (`Set-Cookie` with `Max-Age=0` or similar).
4. **CSRF**: with cookies, add **SameSite** where possible; for state-changing requests consider **anti-CSRF tokens** or **double-submit cookie** patterns as required by your threat model.

This is a **cross-cutting** change (API, BFF, CORS, UI client). The current bearer model avoids classic cross-site cookie CSRF but remains XSS-sensitive via `sessionStorage`.

## Mirroring headers at the edge (Cloudflare / AWS)

For production, replicate the **same baseline** as `ezkey-admin-ui/docker/Caddyfile` (CSP, frame denial, referrer, permissions) on **Cloudflare** (Transform Rules, Page Rules, or Workers) or on **ALB / CloudFront** response headers. Keep **one** documented source of truth (this file + Caddyfile) and align the edge when policies change.

## Related files

| File | Role |
|------|------|
| [`admin-ui-security-validation.md`](admin-ui-security-validation.md) | **How to test** headers, token storage, and proxy paths (developer checklist) |
| [`ezkey-admin-ui/docker/Caddyfile`](../ezkey-admin-ui/docker/Caddyfile) | Admin UI static SPA + `/api/*` proxy + CSP and security headers |
| [`docker/caddy/Caddyfile`](../docker/caddy/Caddyfile) | API-only Caddy in `docker-compose.with-proxy.yml` |
| [`ezkey-tests/clean-start.sh`](../ezkey-tests/clean-start.sh) | Default stack includes Caddy proxy (`--no-proxy` to disable) |
