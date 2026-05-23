# ezkey.org static site — Cloudflare workflow

This document describes the **repository location** of the public static site, the **intended assistant/operator workflow** for content updates and deployment, and **time horizons** for related work. It stays factual: nothing here claims features that are not decided or implemented.

## Current reality

- **Cloudflare Pages project name:** **`ezkey-org`**. **`ezkey.org`** is the production custom domain on that project. Repository folder: [sites/ezkey-org/](../../sites/ezkey-org/).

### Why you may still see `ezkey-teaser` in preview URLs (no functional split)

Early setup used the name **Ezkey Teaser** for the Pages project. The project was later **renamed to `ezkey-org`** (dashboard + API + this repo). That rename **does not imply two different sites**: there is a **single** Pages project and **one** production hostname (**`ezkey.org`**).

What can linger is only the **default `*.pages.dev` subdomain** Cloudflare attached when the project was **first created**. For many accounts, that default subdomain **keeps the original slug** (e.g. `ezkey-teaser.pages.dev`) even after the project **display name** becomes `ezkey-org`. Preview deployments from Wrangler then print URLs like `https://<deployment-id>.ezkey-teaser.pages.dev` — they are **the same project and the same files** as production builds, not a separate “teaser” application.

**How to think about it in this repo:** treat **`ezkey-org`** as the only project name; treat **`ezkey-teaser` in a `*.pages.dev` URL** as a **legacy hostname label** if it still appears. For humans, **`https://ezkey.org`** is the canonical public URL. To see or adjust default Pages hostnames, use the Cloudflare dashboard (**Workers & Pages → ezkey-org → Custom domains** and related settings).

- **Source:** [sites/ezkey-org/](../../sites/ezkey-org/) — includes `sitemap.xml`, `robots.txt`, and **Updates** RSS (`updates.rss`, `fr/updates.rss`); compact landings: `index.html` (EN, `/`), `fr/index.html` (FR, **`/fr/`**); content hubs: `updates.html` / `fr/updates.html`, `articles.html` / `fr/articles.html`, `source-and-evaluation.html` / `fr/source-and-evaluation.html`, `trust.html` / `fr/trust.html`, `changelog.html` / `fr/changelog.html`; API portal pages: `api-docs.html`, `admin-api-reference.html`, `auth-api-reference.html`, `integration-api-reference.html`; logo asset `logo.svg` referenced from pages.
- **Nature:** Fully static HTML/CSS; no bundler or Maven module. Suitable for **Cloudflare Pages** (or equivalent) with **no build command** and publish directory = `sites/ezkey-org` (or the project root if the Pages project points at that folder only).
- **Deployment today:** Manual or ad hoc Cloudflare deployment is acceptable; the important part is that **git** holds the canonical content.
- **Cross-cutting product docs:** Edge IP and rate-limiting context remain in [OPERATIONAL.md](../OPERATIONAL.md). Admin UI split deployment and CSP at the edge are covered in [admin-ui-security.md](../admin-ui-security.md).

## Short term — intended workflow (preview then production)

Goal: support a repeatable dialogue between operator and assistant:

1. **Edit content** — Update the relevant static HTML under [sites/ezkey-org/](../../sites/ezkey-org/) (landing, [`updates.html`](../../sites/ezkey-org/updates.html) / [`fr/updates.html`](../../sites/ezkey-org/fr/updates.html), [`articles.html`](../../sites/ezkey-org/articles.html) index, article pages, etc.) and commit. See [sites/ezkey-org/AGENTS.md](../../sites/ezkey-org/AGENTS.md) for IA and bilingual rules.
2. **Deploy to a test/preview URL** — Publish the same static output to a **non-production** URL (e.g. Cloudflare Pages preview deployment or a dedicated preview hostname).
3. **Human validation** — Operator opens the preview URL, checks layout and copy.
4. **Promote to production** — After approval, deploy the same artifact to the **production** hostname (ezkey.org).

**Guardrails:**

- Treat preview and production as **separate promotion steps**; do not skip human review for public-facing copy unless policy changes.
- Keep costs and complexity aligned with **free tier** where possible; document any paid capability only when actually adopted.

### Deploy a preview (Wrangler CLI)

The supported way to upload a **prebuilt** static folder is **[Wrangler](https://developers.cloudflare.com/pages/how-to/use-direct-upload-with-continuous-integration/)** (`npx wrangler`). The agent environment does not have your API token; run these commands **on your machine** after exporting credentials.

**Required environment variables**

| Variable | Purpose |
|----------|---------|
| `CLOUDFLARE_API_TOKEN` | API token with **Account → Cloudflare Pages → Edit** (or a template that includes Pages deploy). [Create token](https://developers.cloudflare.com/fundamentals/api/get-started/create-token/). |
| `CLOUDFLARE_ACCOUNT_ID` | Account ID (dashboard → account home). |

**Preview deployment** (branch ≠ production branch, e.g. `main`)

From the **repository root**, using Git Bash on Windows or any Unix shell:

```bash
export CLOUDFLARE_API_TOKEN="your_token_here"
export CLOUDFLARE_ACCOUNT_ID="your_account_id_here"
./scripts/cloudflare/deploy-ezkey-org-preview.sh
```

The script deploys `sites/ezkey-org/` to the Pages project **`ezkey-org`** (override with `CLOUDFLARE_PAGES_PROJECT`) and passes `--branch` with a unique preview branch name. Wrangler prints a **deployment URL** such as `https://<short-id>.<pages-subdomain>.pages.dev`. The `<pages-subdomain>` may still be the **legacy** name from project creation (e.g. `ezkey-teaser`); that is **normal** and does not mean a different project — see [Current reality](#current-reality) above.

**PowerShell (Windows)**

```powershell
$env:CLOUDFLARE_API_TOKEN = "your_token_here"
$env:CLOUDFLARE_ACCOUNT_ID = "your_account_id_here"
cd C:\github\ezkey
npx wrangler pages deploy sites/ezkey-org --project-name=ezkey-org --branch=preview-manual-1
```

**Production deployment** (after you approve a preview) uses the same Wrangler direct upload, targeting the project’s **production branch** (usually `main`):

```bash
export CLOUDFLARE_API_TOKEN="your_token_here"
export CLOUDFLARE_ACCOUNT_ID="your_account_id_here"
./scripts/cloudflare/deploy-ezkey-org-production.sh
```

The script is **[scripts/cloudflare/deploy-ezkey-org-production.sh](../../scripts/cloudflare/deploy-ezkey-org-production.sh)**. Override the branch with `CLOUDFLARE_PAGES_PRODUCTION_BRANCH` if your Pages project uses something other than `main`. The live public URL is **`https://ezkey.org`** (custom domain on the `ezkey-org` project), not the `*.pages.dev` link Wrangler may print.

## Medium term — repository and assistant conventions

When Cloudflare usage grows, document (in this folder or short linked notes):

- **Where plans live** — e.g. `docs/plan/` for product/engineering plans; `docs/cloudflare/` for edge and hosting runbooks.
- **What the assistant may change automatically** — e.g. file edits in `sites/ezkey-org/` with review; **not** destructive DNS changes without explicit approval.
- **What always needs human validation** — production deploy, DNS cutover, WAF or security rule changes affecting traffic.

**Possible topics** (only expand when decisions are real):

- DNS for public API hostnames (e.g. `api.ezkey.org`) pointing to origins **outside** Cloudflare (e.g. AWS).
- Aligning **edge response headers** (CSP, etc.) with [admin-ui-security.md](../admin-ui-security.md) when Admin UI or static assets are served from Cloudflare.
- **Rate limiting** and **WAF**-style controls at the edge as a complement to application-level limits described in [OPERATIONAL.md](../OPERATIONAL.md) and [ENDPOINT.md](../ENDPOINT.md).

## Longer term — possibilities (not commitments)

These are **directional** only; validate against product roadmap and budget before treating as roadmap items:

- Host **Admin UI** static build on Cloudflare while **Admin API / Auth API** run on another cloud.
- **JSON schema validation** or other request filtering at the edge (only where it matches threat model and cost).
- Deeper **WAF** posture — may intersect paid tiers; document facts when evaluated.

## Related

- [README.md](README.md) — Index for this Cloudflare docs area.
- [sites/ezkey-org/README.md](../../sites/ezkey-org/README.md) — Short pointer from the site folder back here.
