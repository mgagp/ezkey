# Admin UI — Cloudflare Pages and split UI/API

This runbook supports deploying the Vite/React Admin UI from [`ezkey-admin-ui/`](../../ezkey-admin-ui/) to **Cloudflare Pages** while the **Admin API** runs elsewhere (e.g. `https://exp1-admin-api.ezkey.org` on AWS Lightsail).

Cross-links: [admin-ui-security.md](../admin-ui-security.md) (CSP, token model), [ezkey-admin-api/CONFIGURATION.md](../../ezkey-admin-api/CONFIGURATION.md) (CORS properties).

## 0. Pages projects — one instance, one project (Ezkey convention)

**What you are choosing:** how Cloudflare Pages maps to **which Admin API** the built SPA calls. `VITE_API_BASE_URL` is fixed at **build time**, so the wrong project or wrong variables means the UI talks to the **wrong** API.

**Recommended (default for Ezkey):** **one Pages project per Admin UI deployment** — e.g. a project dedicated to `exp1` (custom domain `exp1-admin-ui.ezkey.org`) and, when you add it, a **separate** project for `demo1`. Each project has its own production branch, custom domain, Transform Rules / CSP for **that** API origin, and **Environment variables** in the Pages dashboard:

- `VITE_API_BASE_URL` → `https://<same-instance>-admin-api.ezkey.org`
- `VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE` → `true` when that API uses the HttpOnly session cookie
- Optional only if the API defaults are changed: `VITE_ADMIN_AUTH_CSRF_COOKIE_NAME` and `VITE_ADMIN_AUTH_CSRF_HEADER_NAME`
- Optional `VITE_GIT_SHA` → short git SHA for sidebar **Public alpha · \<sha\>** chrome (see [`VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../VERSIONING_AND_DEPLOY_TRACEABILITY.md)); Vite/deploy scripts resolve from git when unset

This keeps mental load low: open the project → you see exactly one API target.

**Alternative:** a **single** Pages project with multiple branches and branch-specific variables (or previews). Fewer projects in the dashboard, but easier to misconfigure merges or variables — use only if you explicitly want that operational model.

**Operator source of truth:** this document (§2–3 for build and deploy, §5 for `connect-src`), repo root [`.env.example`](../../.env.example) for script-based deploys, and the **Pages project → Settings → Environment variables** for Git-connected builds.

## 1. Runtime CORS on the Admin API (Lightsail / VM)

When the SPA and API are on **different origins**, the browser requires CORS. The Admin API exposes `ezkey.admin.cors.*` (see CONFIGURATION.md).

**After you know the Admin UI origin** (Cloudflare Pages preview URL, custom domain, or both):

1. Set **`EZKEY_ADMIN_CORS_ALLOWED_ORIGINS`** to a **comma-separated** list of exact origins (scheme + host + port, **no path**), for example:
   - `https://exp1-admin-ui.pages.dev`
   - `https://<preview-id>.<project>.pages.dev`
   - `https://exp1-admin-ui.ezkey.org` (custom domain; see DNS example below)
2. Restart the Admin API container (or process) on the instance so the new env is applied.
3. For **rotating preview URLs**, either add each preview origin when testing, use a **stable preview hostname**, or restrict previews to environments where you can update env quickly.

If `allowed-origins` is empty, the API does **not** send CORS headers (same as pre–split-ui behavior).

### HttpOnly session cookie (recommended for custom-domain UI)

For **`https://<instance>-admin-ui.ezkey.org`** calling **`https://<instance>-admin-api.ezkey.org`**, you can use the **browser session cookie** so the opaque token is not exposed to JavaScript:

1. On the **Admin API** VM: set **`EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_ENABLED=true`**, **`EZKEY_ADMIN_CORS_ALLOW_CREDENTIALS=true`**, and **`EZKEY_ADMIN_CORS_ALLOWED_ORIGINS`** to the **exact** UI origin (e.g. `https://exp1-admin-ui.ezkey.org` or `https://demo1-admin-ui.ezkey.org`). Restart the API. The default cookie policy is host-only, `Secure`, `HttpOnly` for the session cookie, and `SameSite=Strict`.
2. On the **Admin UI** build: set **`VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE=true`** (see [`.env.cloudflare`](../../ezkey-admin-ui/.env.cloudflare); for Git-connected Pages, add the variable in the project settings). Rebuild and deploy.
3. **CSP** `connect-src` must still include that instance’s API origin (see §5).

In cookie mode, the UI restores state after refresh with `GET /api/v1/admin/auth/me` and sends `X-CSRF-TOKEN` on unsafe requests. Ensure custom edge/CORS rules do not strip that header.

The same steps apply to **demo1**, **exp1**, or any other prefix — only origins and URLs change.

**Rollback:** turn off the API cookie flag and deploy a UI build without `VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE`.

## 2. Build the static bundle locally

From `ezkey-admin-ui/`:

```bash
npm ci
npm run build:cloudflare
```

This runs Orval codegen and a **production** Vite build. Set `VITE_API_BASE_URL` explicitly (env, root `.env`, or uncomment an example in [`.env.cloudflare`](../../ezkey-admin-ui/.env.cloudflare)) — there is no product default hostname.

Optional check (no demo strings in `dist/`):

```bash
./scripts/assert-no-demo-in-build.sh dist
```

Publish **`dist/`** contents to Pages (not the whole monorepo).

## 3. Deploy to Cloudflare Pages

**Prerequisites:** `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` (see [README.md](README.md)). If you already use a gitignored **`.env`** at the **repository root** for the marketing site deploy script, the same file is **sourced automatically** by `deploy-admin-ui-preview.sh` — you do not need to export the variables again unless you prefer to.

Add **`VITE_API_BASE_URL`** in the repo-root `.env` (or export it) before `--build`. Deploy scripts **require** it and exit ≠ 0 if missing — there is no product default hostname. Template: **[`.env.example`](../../.env.example)** at the repo root (commented EXP1 / community examples). Committed Vite mode file [`ezkey-admin-ui/.env.cloudflare`](../../ezkey-admin-ui/.env.cloudflare) also uses commented examples only.

Create a **dedicated** Pages project (do not reuse `ezkey-org`). Example project name: `ezkey-admin-ui` or `exp1-admin-ui`.

**Preview (Wrangler):**

```bash
./scripts/cloudflare/deploy-admin-ui-preview.sh
```

Build and deploy in one step (requires `VITE_API_BASE_URL` in root `.env` or the environment):

```bash
./scripts/cloudflare/deploy-admin-ui-preview.sh --build
```

(Or `export CLOUDFLARE_API_TOKEN=...` and `CLOUDFLARE_ACCOUNT_ID=...` in the shell instead of `.env`.)

Override project name: `CLOUDFLARE_PAGES_PROJECT=ezkey-admin-ui ./scripts/cloudflare/deploy-admin-ui-preview.sh`

**Production:** from the repo root, after `dist/` is built:

```bash
./scripts/cloudflare/deploy-admin-ui-production.sh
```

Optional `--build` runs `npm run build:cloudflare` first. Override branch with `CLOUDFLARE_PAGES_PRODUCTION_BRANCH` if your Pages project uses something other than `main`. You can also deploy from the dashboard; keep the production branch name aligned with Cloudflare project settings.

**Git-connected builds:** root directory `ezkey-admin-ui`, build command `npm ci && npm run build:cloudflare`, add `VITE_API_BASE_URL` in the Pages project env if you prefer not to rely on committed `.env.cloudflare`.

### Custom domain DNS (example — `ezkey.org` zone)

The **marketing** apex uses its own Pages project and CNAME (e.g. `ezkey.org` → `ezkey-teaser.pages.dev` — see [ezkey-org-site.md](ezkey-org-site.md)). That is **not** the Admin UI.

For **`https://exp1-admin-ui.ezkey.org`** pointing at the **`ezkey-admin-ui`** Pages project, a typical record is:

| Type | Name | Target | Proxy |
|------|------|--------|--------|
| CNAME | `exp1-admin-ui` | `ezkey-admin-ui.pages.dev` | Proxied |

Also add **`exp1-admin-ui.ezkey.org`** under **Workers & Pages → ezkey-admin-ui → Custom domains** so TLS and routing are correct (Cloudflare may create or align the DNS record).

## 4. TLS version (minimum 1.3) — Admin UI edge

Ezkey is **opinionated** about TLS: public APIs on Lightsail use **TLS 1.3 only** (see [`experimental-hybrid/DEPLOYMENT_PLAYBOOK.md`](../../experimental-hybrid/DEPLOYMENT_PLAYBOOK.md)). The Admin UI is served by **Cloudflare**, not Caddy — set the same expectation at the edge.

In the Cloudflare dashboard for the **zone** that serves your Pages hostname(s) and custom Admin UI domain:

1. **SSL/TLS** → **Edge Certificates** (or **SSL/TLS** → **Overview** → **Advanced**, depending on UI version).
2. Set **Minimum TLS Version** to **1.3**.

That controls the **browser ↔ Cloudflare** leg. Modern browsers and mobile stacks support TLS 1.3; legacy clients that cannot negotiate 1.3 will fail to load the site — an accepted trade-off for this project.

## 5. Edge security headers (Transform Rules)

Pages does not apply [`ezkey-admin-ui/docker/Caddyfile`](../../ezkey-admin-ui/docker/Caddyfile) automatically. Mirror the **baseline** for HTML responses using **Cloudflare Transform Rules** (or equivalent) on the Admin UI hostname.

**Suggested headers** (align with Caddy; adjust if product policy changes):

| Header | Example value |
|--------|----------------|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=(), payment=(), usb=()` |
| `Content-Security-Policy` | See below |

**Content-Security-Policy** for split UI/API (extend `connect-src` with the public Admin API origin):

```http
default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' https://exp1-admin-api.ezkey.org; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
```

**HSTS** should be set only on **HTTPS** responses at the edge (Cloudflare SSL mode Full/Strict). Use your account policy for `max-age` and `includeSubDomains`.

Scope rules to **HTML** or the SPA shell if you add Workers or APIs on the same hostname later.

## 6. Related scripts

| Script | Purpose |
|--------|---------|
| [deploy-admin-ui-preview.sh](../../scripts/cloudflare/deploy-admin-ui-preview.sh) | Upload `ezkey-admin-ui/dist` to Pages as a preview branch |
| [deploy-admin-ui-production.sh](../../scripts/cloudflare/deploy-admin-ui-production.sh) | Upload `dist/` to the Pages **production** branch (e.g. `main`) |
