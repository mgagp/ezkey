# Admin UI security hardening — how to validate (developer guide)

This page is a **practical entry point** for checking the measures described in **[admin-ui-security.md](admin-ui-security.md)** and the **archived** **[Admin UI token security plan](../.cursor/plans/archived/2026-03/admin_ui_token_security.plan.md)** (historical). For ongoing policy, prefer `admin-ui-security.md`. This guide tells you **what to open**, **what to look for**, and **when a test counts**.

If you are new to Ezkey: start with **§1**, run one **smoke path** (§2), then use the **checklist** (§3) when you touch headers or auth flow.

---

## 1. Where to start (order of operations)

1. **Bring up APIs** — `ezkey-tests/clean-start.sh` (see **[LOCAL_STACK_PORTS.md](LOCAL_STACK_PORTS.md)** for direct vs Caddy ports).
2. **Pick your surface:**
  - **Fast feature work** — `npm run dev` in `ezkey-admin-ui` (Path A). You are **not** validating production-like HTTP headers here.
  - **Header / CSP / clickjacking parity** — `ezkey-admin-ui/start.sh` and open the URL it prints (Path B, typically `http://localhost:3090`). **This is the right default** when someone asks “are our security headers live?”
3. **Optional** — Call APIs **through Caddy** on the host (`19080` / `18080` / `17080`) when you need to validate **proxy-added headers** or **trusted-proxy / `X-Forwarded-*`** behavior; use the Postman environment `**local (via Caddy proxy)**` (see LOCAL_STACK_PORTS).

You do **not** need to master every row below on day one. Use the checklist when you change Caddyfiles or auth client code.

---

## 2. Two paths — what “passing” means


| Path                      | Command / URL                                | What you validate                                                                                                                                                                    |
| ------------------------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **A — Dev**               | `npm run dev` → e.g. `http://localhost:5173` | App behavior, API integration via Vite proxy, token flow. **Not** the same response headers as production Caddy.                                                                     |
| **B — QA / prod-like UI** | `ezkey-admin-ui/start.sh` → Caddy-served SPA | **CSP**, **frame-ancestors**, **Referrer-Policy**, **Permissions-Policy**, **X-Content-Type-Options**, **X-Frame-Options** on HTML and the configured headers on `/api/*` responses. |


**Rule of thumb:** If the task is “did we ship the right headers?”, test **Path B** (or staging/production). Path A staying “light” on headers is **intentional** for velocity.

---

## 3. Checklist — measure, why it matters, how to test

### Token storage and transport (application code)


| Measure                                           | Why it matters                                                                                                 | How to test                                                                                                                                                                   |
| ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Mode A — token in `sessionStorage`, not `localStorage`** | Default local/QA: short-lived tab scope; opaque bearer stays out of `localStorage`. | Path A or B with cookie **off**: DevTools → **Application** → **Storage** → key `ezkey_admin_auth` under **Session** only (JSON includes `token`). |
| **Mode A — Bearer in `Authorization`**                     | Default API auth contract when cookie mode is off.                                                                      | DevTools → **Network** → authenticated `fetch` → request has `Authorization: Bearer …`.                                                                                |
| **Mode A — no session `Set-Cookie`**                          | Login JSON carries the token; do not expect an Admin session cookie in Mode A.     | Login response: token in JSON body; no `EZKEY_ADMIN_SESSION` (or configured name) session cookie.                                                                                        |
| **Mode B — HttpOnly cookie (split HTTPS)** | Recommended for Cloudflare UI + API VM: secret not in JS; see [`admin-ui-security.md`](admin-ui-security.md) Mode B. | Cookie **on** + UI build flag: login JSON **omits** `token`; `Set-Cookie` HttpOnly on API host; subsequent calls use `credentials: 'include'` (+ CSRF header on unsafe methods). |


### HTTP headers — Admin UI (Path B, Caddy)

Serve the app with `**./start.sh`** in `ezkey-admin-ui`, then for the **main document** (first navigation) or any **HTML** response:


| Header / policy                           | What it does (short)                                                                                | How to test                                                                                                                                                                                                                                                                                               |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `**Content-Security-Policy` (CSP)**       | Restricts where scripts, styles, and connections may load from; main defense-in-depth vs XSS abuse. | DevTools → **Network** → select the **document** (or `index.html`) → **Headers** → **Response Headers** → confirm `content-security-policy` matches expectations in `ezkey-admin-ui/docker/Caddyfile`. Open the app and click through main screens; **Console** should stay free of CSP violation errors. |
| `**frame-ancestors 'none'`** (inside CSP) | Stops other sites embedding the Admin UI in an iframe (clickjacking).                               | Optional: create a tiny local HTML file with `<iframe src="http://localhost:3090">` — the iframe should be **blocked** or empty (browser-dependent). Or rely on header presence.                                                                                                                          |
| `**X-Frame-Options: DENY`**               | Legacy complement to frame denial.                                                                  | Same document response headers; value `DENY`.                                                                                                                                                                                                                                                             |
| `**Referrer-Policy**`                     | Limits what URL data is sent on navigations away from the app.                                      | Response headers on HTML; value should match Caddyfile (e.g. `strict-origin-when-cross-origin`).                                                                                                                                                                                                          |
| `**Permissions-Policy**`                  | Disables powerful features (camera, mic, …) by default in the browser.                              | Response headers on HTML; presence matches Caddyfile.                                                                                                                                                                                                                                                     |
| `**X-Content-Type-Options: nosniff**`     | Reduces MIME sniffing attacks.                                                                      | Present on HTML and on proxied `/api/*` responses in Path B.                                                                                                                                                                                                                                              |


**CSP tuning:** If you change policies, temporarily enable `**Content-Security-Policy-Report-Only`** in the Caddyfile (see comments there) to see violations in **Console** without blocking, then switch back to enforced CSP.

### HTTP headers — APIs behind Caddy (`clean-start` default)


| Measure                                                                                       | Why it matters                                                                   | How to test                                                                                                                                                                                                                |
| --------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Baseline headers on JSON** (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`) | Consistent hardening on API responses when traffic passes through the dev proxy. | With stack up and **not** `--no-proxy`: `curl -sI http://localhost:19080/api/v1/public/instance-info` (or `18080` as appropriate) and inspect **response** headers. Compare with **direct** `9080` if you want to see the difference. Caddy does not proxy `/api-docs` or Swagger UI; those stay on the direct ports for `scripts/update-specs.sh`. |
| **Trusted proxy / client IP**                                                                 | Apps read `CF-Connecting-IP` / `X-Forwarded-For` when `EZKEY_TRUSTED_PROXIES_CIDRS` is set. | Use the **Caddy** ports from the host; trigger a request that logs client IP and confirm behavior matches docs (see operational / rate-limit docs as needed).                                                              |


### What you cannot fully test on plain HTTP


| Topic                                  | Note                                                                                                      |
| -------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `**Strict-Transport-Security` (HSTS)** | Only meaningful on **HTTPS** responses. The Admin UI Caddyfile keeps HSTS commented until TLS is enabled. |
| `**Secure` cookies**                   | Needs HTTPS. Required for Mode B production cookies; enable when validating cookie mode over TLS.                                           |


**When you need HTTPS locally:** follow **[admin-ui-security.md](admin-ui-security.md)** (mkcert + Caddy `tls`); then enable the HSTS snippet only for HTTPS responses.

---

## 4. Suggested first session (you can do this today)

1. `./clean-start.sh` from `ezkey-tests`.
2. `cd ezkey-admin-ui && ./start.sh` — open the printed URL.
3. DevTools → **Network** — reload — confirm CSP and frame-related headers on the document.
4. Log in — confirm **sessionStorage** + **Bearer** as above.
5. (Optional) `curl -sI http://localhost:19080/api/v1/public/instance-info` — see Caddy headers on the Admin API. Live `/api-docs` and Swagger UI stay on direct `9080` (not Caddy).

That gives you **end-to-end confidence** on the measures most people mean by “security headers for the Admin UI” without reading the whole plan.

---

## 5. Related docs


| Doc                                                                     | Use                                                                     |
| ----------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| [admin-ui-security.md](admin-ui-security.md)                            | Canonical policy: paths, headers, split deploy, mkcert, future cookies. |
| [LOCAL_STACK_PORTS.md](LOCAL_STACK_PORTS.md)                            | Direct vs `17xxx`/`18xxx`/`19xxx` ports and Postman environments.       |
| `[ezkey-admin-ui/docker/Caddyfile](../ezkey-admin-ui/docker/Caddyfile)` | Source of truth for Admin UI headers.                                   |
| `[docker/caddy/Caddyfile](../docker/caddy/Caddyfile)`                   | API proxy headers in `clean-start`.                                     |


