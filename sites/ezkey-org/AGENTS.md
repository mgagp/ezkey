# Ezkey.org static site — Agent notes

For agents working in **`sites/ezkey-org/`** (public landing page, mini blog copy, static assets) and for **Cloudflare Pages** operations that serve this content at **ezkey.org**.

For repo-wide rules (Maven, Java, etc.), see **[../../AGENTS.md](../../AGENTS.md)**. For Cloudflare workflow and time horizons, see **[../../docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)** and **[../../docs/cloudflare/README.md](../../docs/cloudflare/README.md)**.

---

## What this folder is

- **Source of truth in git:** `index.html` (English), `fr/index.html` (French); inline SVG logo in each file — static HTML/CSS, no bundler.
- **Public hostname:** **`ezkey.org`** (canonical production URL for this site).
- **Cloudflare:** The live site is deployed via **Cloudflare Pages**. The **only** project name to use in docs and scripts is **`ezkey-org`**; production traffic uses **`ezkey.org`**. Preview URLs from Wrangler may still show a **`*.pages.dev`** subdomain created under an early project name (e.g. `ezkey-teaser`) — that is a **legacy hostname** for the **same** project, not a second app. Details: **[docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)** (section *Why you may still see `ezkey-teaser` in preview URLs*). When an agent reads or updates Cloudflare state, treat **what is configured in Cloudflare** as the operational source of truth for hosting; treat **this folder** as the source of truth for **content and markup** to deploy.

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

## Draft Markdown (`draft-*.md`) vs published HTML

Some French drafts under **`fr/`** (e.g. `draft-ezkey-*.md`) are the **working source** for articles. They may contain **editorial positioning** that must **not** appear in the public HTML.

## Default article workflow

For long-form editorial work on ezkey.org, the default execution order is:

1. **Start with a French draft only** under **`sites/ezkey-org/fr/draft-<english-filename>.md`**.
2. **Do not** create or update the published HTML yet.
3. **Do not** attach the article to **`fr/index.html`** at draft stage.
4. Refine the French draft until the narrative, angle, and exclusions are stable.
5. Only then, if explicitly requested or clearly part of the next approved step, generate the published French HTML and update the French index.

This means the expected first deliverable for an article plan is a **French draft markdown file not linked from the index**.

**Convention (single file, two layers):**

1. **YAML front matter** (optional, at the very top) — **not** copied into article body HTML: `status`, `audience`, or other short metadata for authors and tooling.
2. **HTML comment blocks** — **not** published when generating or hand-syncing HTML. Use **one** multiline HTML comment (everything from `<!-- ezkey-org:exclude-start` through `ezkey-org:exclude-end -->`):

   ```text
   <!-- ezkey-org:exclude-start
   Internal notes only. Not publishable.
   ezkey-org:exclude-end -->
   ```

   Do **not** use an empty `<!-- ezkey-org:exclude-start -->` immediately closed with `-->` and then put body text after it — that text would **not** be inside the comment and would still publish.

Use these blocks for: internal objectives (« schéma mental », niveau de lecteur), working titles (« sans sur-détailler »), captions (« à transposer en illustration »), scratch notes, and duplicate wording kept for context. **Headings and paragraphs outside these blocks** are the **publishable article** (subject to normal editing).

When **regenerating** HTML from Markdown, **omit** YAML front matter and **omit** every multiline comment that starts with `<!-- ezkey-org:exclude-start`. Mermaid or other diagram source in drafts may stay inside an exclude block if the site uses a static SVG/HTML diagram in the `.html` instead.

---

## Related paths

| Path | Role |
| ---- | ---- |
| [README.md](README.md) | Short description of folder contents |
| [index.html](index.html) | Live page source |
| [../../docs/cloudflare/README.md](../../docs/cloudflare/README.md) | Cloudflare docs index |
