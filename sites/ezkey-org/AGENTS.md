# Ezkey.org static site — Agent notes

For agents working in **`sites/ezkey-org/`** (public landing page, mini blog copy, static assets) and for **Cloudflare Pages** operations that serve this content at **ezkey.org**.

For repo-wide rules (Maven, Java, etc.), see **[../../AGENTS.md](../../AGENTS.md)**. For Cloudflare workflow and time horizons, see **[../../docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)** and **[../../docs/cloudflare/README.md](../../docs/cloudflare/README.md)**.

---

## What this folder is

- **Source of truth in git:** `index.html` (English), `fr/index.html` (French); inline SVG logo in each file — static HTML/CSS, no bundler.
- **Public hostname:** **`ezkey.org`** (canonical production URL for this site).
- **Cloudflare:** The live site is deployed via **Cloudflare Pages**. The Pages **project name** in the dashboard/API is **`ezkey-org`**. Custom domains on that project include **`ezkey.org`** and the `*.pages.dev` hostname. When an agent reads or updates Cloudflare state, treat **what is configured in Cloudflare** (project, domains, latest deployment) as the operational source of truth for hosting; treat **this folder** as the source of truth for **content and markup** to deploy.

---

## Voice input and the “easy” / “Ezkey” confusion

Operators may use **Wispr Flow** or other dictation tools. Speech recognition often transcribes the product name as **“easy”**, **“ez key”**, or similar, because it sounds like the English word *easy*.

**Agent guidance:**

- In **copy, titles, and technical references** for this project, the product name is **Ezkey** (see [PRD.md](../../PRD.md)). Do **not** replace it with “easy” or “EZ Key” unless the user explicitly asks for a specific spelling in that instance.
- The **canonical public site domain** is **`ezkey.org`** (lowercase in URLs; “EZKEY” may appear in branding). If a transcript or instruction says “easykey.org” or “easy” in place of the product name, **normalize to Ezkey / ezkey.org** when editing site content or deployment notes, unless the user corrects you.
- If ambiguity remains, prefer **`ezkey.org`** and the on-disk paths under **`sites/ezkey-org/`** over guessed homophones.

---

## Deployment and agent behaviour (high level)

- Prefer the workflow in [ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md): edit content → preview deploy → human validation → production.
- **Preview deploy** from a developer machine: [../../scripts/cloudflare/deploy-ezkey-org-preview.sh](../../scripts/cloudflare/deploy-ezkey-org-preview.sh) (requires `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`). The agent cannot run this without your token.
- Do **not** assume DNS or edge rules are writable via every API token; some operations require specific Cloudflare permissions or dashboard steps.
- See [docs/cloudflare/README.md](../../docs/cloudflare/README.md) for the `scripts/cloudflare/` index.

---

## Related paths

| Path | Role |
|------|------|
| [README.md](README.md) | Short description of folder contents |
| [index.html](index.html) | Live page source |
| [../../docs/cloudflare/README.md](../../docs/cloudflare/README.md) | Cloudflare docs index |
