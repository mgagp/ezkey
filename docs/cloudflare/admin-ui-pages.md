# Admin UI — Cloudflare Pages and split UI/API

This runbook supports deploying the Vite/React Admin UI from [`ezkey-admin-ui/`](../../ezkey-admin-ui/) to **Cloudflare Pages** while the **Admin API** runs elsewhere (e.g. `https://exp1-admin-api.ezkey.org` on AWS Lightsail).

Cross-links: [admin-ui-security.md](../admin-ui-security.md) (CSP, token model), [ezkey-admin-api/CONFIGURATION.md](../../ezkey-admin-api/CONFIGURATION.md) (CORS properties).

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

## 2. Build the static bundle locally

From `ezkey-admin-ui/`:

```bash
npm ci
npm run build:cloudflare
```

This runs Orval codegen and a **production** Vite build with `VITE_API_BASE_URL` from [`.env.cloudflare`](../../ezkey-admin-ui/.env.cloudflare) (exp1 Admin API URL).

Optional check (no demo strings in `dist/`):

```bash
./scripts/assert-no-demo-in-build.sh dist
```

Publish **`dist/`** contents to Pages (not the whole monorepo).

## 3. Deploy to Cloudflare Pages

**Prerequisites:** `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` (see [README.md](README.md)). If you already use a gitignored **`.env`** at the **repository root** for the marketing site deploy script, the same file is **sourced automatically** by `deploy-admin-ui-preview.sh` — you do not need to export the variables again unless you prefer to.

Add **`VITE_API_BASE_URL`** there if the public Admin API is not `https://exp1-admin-api.ezkey.org`. If you omit it, the deploy script still defaults to that URL when you use **`--build`**. Template: **[`.env.example`](../../.env.example)** at the repo root.

Create a **dedicated** Pages project (do not reuse `ezkey-org`). Example project name: `ezkey-admin-ui` or `exp1-admin-ui`.

**Preview (Wrangler):**

```bash
./scripts/cloudflare/deploy-admin-ui-preview.sh
```

Build and deploy in one step (uses `VITE_API_BASE_URL` from root `.env`, or `https://exp1-admin-api.ezkey.org` by default):

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
