# Ezkey.org static site — Agent notes

For agents working in **`sites/ezkey-org/`** (public landing page, mini blog copy, static assets) and for **Cloudflare Pages** operations that serve this content at **ezkey.org**.

For repo-wide rules (Maven, Java, etc.), see **[../../AGENTS.md](../../AGENTS.md)**. For Cloudflare workflow and time horizons, see **[../../docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)** and **[../../docs/cloudflare/README.md](../../docs/cloudflare/README.md)**.

---

## What this folder is

- **Source of truth in git:** static HTML/CSS under this directory — no bundler. **English** default locale; **French** under `fr/`.
- **Landing pages (compact):** [`index.html`](index.html) (en), [`fr/index.html`](fr/index.html) (fr) — hero, pillars, primary navigation, evaluation CTAs (run locally, docs, trust), and secondary links into product writing. They are **not** the archive for updates or articles.
- **Trust / diligence (short):** [`trust.html`](trust.html) / [`fr/trust.html`](fr/trust.html) — plain-language trust model, disclosure posture, security reporting (`security@ezkey.org`), and **on-site** pointers (no public GitHub URLs while the repository stays private).
- **Source & evaluation hub:** [`source-and-evaluation.html`](source-and-evaluation.html) / [`fr/source-and-evaluation.html`](fr/source-and-evaluation.html) — explains that Ezkey is **planned** as MIT open source but the **main repository is private** until the scheduled public opening; anchors `#documentation` and `#run-locally` are the nav targets for **Docs** / **Run locally**.
- **Product updates (reverse chronological, dated):** [`updates.html`](updates.html) / [`fr/updates.html`](fr/updates.html) — short build-status notes; optional `data-update-type` on each `<li class="update-entry">` for future filtering. This is the **product-facing timeline**. It cross-links to the changelog for future versioned notes.
- **Articles & notes index:** [`articles.html`](articles.html) / [`fr/articles.html`](fr/articles.html) — includes a **Guides & walkthroughs** lane (evaluator-oriented steps) above the **Essays & deep dives** catalog; article bodies remain standalone pages at the site root or under `fr/`.
- **Changelog placeholder (technical release notes later):** [`changelog.html`](changelog.html) / [`fr/changelog.html`](fr/changelog.html) — reserved for versioned technical notes. **Not** listed on the primary navigation of the landing page or content hubs so an empty section does not dilute IA integrity; the changelog page itself repeats full nav **plus** a contextual “Changelog” / “Notes de version” item with `aria-current="page"`. Discoverable by URL and from copy on **Updates**.
- **Discovery & sharing (static):** [`sitemap.xml`](sitemap.xml) and [`robots.txt`](robots.txt) at the site root; [`updates.rss`](updates.rss) / [`fr/updates.rss`](fr/updates.rss) mirror the dated entries on **Updates** (newest items first in the feed). All published `*.html` pages include **Open Graph** and **Twitter Card** meta tags (`og:*`, `twitter:*`), using `https://ezkey.org/logo.svg` as the share image unless you introduce a dedicated social image later.
- **Logo / hero signature:** landing and inner hub pages reuse the same gradient, floating logo treatment, and particle background as before (`<img src="/logo.svg">` on published pages).
- **Public hostname:** **`ezkey.org`** (canonical production URL for this site).
- **Cloudflare:** The live site is deployed via **Cloudflare Pages**. The **only** project name to use in docs and scripts is **`ezkey-org`**; production traffic uses **`ezkey.org`**. Preview URLs from Wrangler may still show a **`*.pages.dev`** subdomain created under an early project name (e.g. `ezkey-teaser`) — that is a **legacy hostname** for the **same** project, not a second app. Details: **[docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)** (section *Why you may still see `ezkey-teaser` in preview URLs*). When an agent reads or updates Cloudflare state, treat **what is configured in Cloudflare** as the operational source of truth for hosting; treat **this folder** as the source of truth for **content and markup** to deploy.

---

## Primary navigation (evaluation-first)

Hub pages (`index`, `updates`, `articles`, `trust`) share a consistent **primary** nav (EN/FR labels):

| English | French | Target |
| ------ | ------ | ------ |
| Home | Accueil | `/` / `/fr/` |
| Docs | Documentation | `/source-and-evaluation.html#documentation` / `/fr/source-and-evaluation.html#documentation` |
| Run locally | Exécuter en local | `/source-and-evaluation.html#run-locally` / `/fr/source-and-evaluation.html#run-locally` |
| Trust | Confiance | `/trust.html` / `/fr/trust.html` |
| Updates | Mises à jour | `/updates.html` / `/fr/updates.html` |
| Articles | Articles | `/articles.html` / `/fr/articles.html` |

**Changelog** is intentionally **omitted** from that primary strip until the page carries real versioned notes. On **changelog** pages only, append the changelog item with `aria-current="page"`.

**Public site and private repository:** Do **not** link from **ezkey.org** to GitHub (or any authenticated-only host) for Ezkey source, docs, or `SECURITY.md` while the repository remains **private**. The intended public URL and onboarding copy will be wired when the repository opens (planned alongside the first public release — see **Updates**). Internal package metadata or future **`SECURITY.md`** in git may still name a future canonical repo URL for maintainers; that does not override this public-site rule.

When the repository is **public**, replace **Docs** / **Run locally** nav targets and homepage CTAs with the canonical README and docs index URLs in **one** coordinated edit (and trim redundant wording on [`source-and-evaluation.html`](source-and-evaluation.html) if appropriate).

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
- **Production deploy** (after content approval): [../../scripts/cloudflare/deploy-ezkey-org-production.sh](../../scripts/cloudflare/deploy-ezkey-org-production.sh) — same credentials; uses the Cloudflare **production** branch (default `main`). Same token constraint for agents.
- Do **not** assume DNS or edge rules are writable via every API token; some operations require specific Cloudflare permissions or dashboard steps.
- See [docs/cloudflare/README.md](../../docs/cloudflare/README.md) for the `scripts/cloudflare/` index.

---

## Draft Markdown (`draft-*.md`) vs published HTML

Some French drafts under **`fr/`** (e.g. `draft-ezkey-*.md`) are the **working source** for articles. They may contain **editorial positioning** that must **not** appear in the public HTML.

## Default article workflow

For long-form editorial work on ezkey.org, the default execution order is:

1. **Start with a French draft only** under **`sites/ezkey-org/fr/draft-<english-filename>.md`**.
2. **Do not** create or update the published HTML yet.
3. **Do not** attach the article to **[`fr/articles.html`](fr/articles.html)** (or the English [`articles.html`](articles.html)) at draft stage.
4. Refine the French draft until the narrative, angle, and exclusions are stable.
5. Only then, if explicitly requested or clearly part of the next approved step, generate the published French HTML, add it to **[`fr/articles.html`](fr/articles.html)**, and add the English counterpart to [`articles.html`](articles.html) when publishing both locales.

This means the expected first deliverable for an article plan is a **French draft markdown file not linked from the article index**.

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

## Guides & experimenter content (Exp1)

- **Purpose:** Step-by-step evaluator flows (e.g. mobile preview) live in the **Guides & walkthroughs** lane on [`articles.html`](articles.html) / [`fr/articles.html`](fr/articles.html), not mixed with generic essay cards without labeling.
- **Published (bilingual):** [`exp1-guided-tour.html`](exp1-guided-tour.html) and [`fr/exp1-guided-tour.html`](fr/exp1-guided-tour.html) — canonical `hreflang` pair with `x-default` on the English URL. Screenshots and other assets: **ASCII kebab-case** filenames under [`exp1-guided-tour/`](exp1-guided-tour/) (e.g. `exp1-tour-04-enrollment-qr-challenge.webp`); both locale pages reference the same paths.
- **Working drafts (French, not linked from indexes):** e.g. [`fr/draft-exp1-guided-tour.md`](fr/draft-exp1-guided-tour.md) (source notes), [`fr/draft-exp1-admin-ui-overview.md`](fr/draft-exp1-admin-ui-overview.md) (not yet a published page). Do **not** link `draft-*.md` from live navigation or the article index.
- **Checklist when adding or changing a guide page:** update both `articles.html` guides lane if needed, add or bump URLs in [`sitemap.xml`](sitemap.xml), and mirror Open Graph / Twitter / `hreflang` like other standalone articles.

---

## Information architecture and bilingual mirroring

| English path | French path | Purpose |
| ------------ | ----------- | ------- |
| `/` → `index.html` | `/fr/` → `fr/index.html` | Compact landing; evaluation CTAs |
| `/source-and-evaluation.html` | `/fr/source-and-evaluation.html` | Private-repo posture; docs/run-local explanation |
| `/trust.html` | `/fr/trust.html` | Trust, reporting, on-site pointers |
| `/updates.html` | `/fr/updates.html` | Product updates (newest first) |
| `/articles.html` | `/fr/articles.html` | Guides lane + article index |
| `/exp1-guided-tour.html` | `/fr/exp1-guided-tour.html` | Exp1 evaluator walkthrough (bilingual) |
| `/changelog.html` | `/fr/changelog.html` | Technical changelog (placeholder) |

**Rules:**

1. **Parallel URLs:** use the **same filename** under `fr/` as under the site root for hub pages (`updates.html`, `articles.html`, `changelog.html`, `trust.html`, `source-and-evaluation.html`).
2. **Pair every hub page with `hreflang`:** each of these files includes `link rel="alternate" hreflang="en"`, `hreflang="fr"`, and `hreflang="x-default"` (x-default follows the English canonical for the site).
3. **Pair article slugs:** English articles live at `/some-slug.html`; French translations at `/fr/some-slug.html` when both exist (same slug, `fr/` prefix).
4. **Navigation:** use the **Primary navigation** table above for hub pages; do not add **Changelog** to that strip until the page has substantive versioned content (changelog HTML pages may still list the item for `aria-current` on that route only).
5. **New product updates:** add a dated entry at the **top** of the list on **both** `updates.html` and `fr/updates.html`; keep wording aligned across locales. Add a matching **RSS `<item>`** at the **top** of [`updates.rss`](updates.rss) and [`fr/updates.rss`](fr/updates.rss) (reuse the same `id` / fragment as the new `<li id="update-YYYY-MM">` on the HTML page). Bump `lastmod` for the updates pages and RSS URLs in [`sitemap.xml`](sitemap.xml) when you publish.
6. **New standalone HTML pages:** copy the Open Graph / Twitter block from an existing hub page and set `og:url`, `og:title`, `og:description`, and `og:locale` (`en_US` vs `fr_FR`) to match; add the page URL to [`sitemap.xml`](sitemap.xml).

---

## Positioning and editorial principles

- **Lead with the problem shape, not founder mythology:** explain the backend-first gap Ezkey addresses before talking about the builder or the workflow.
- **Experimental does not mean casual:** when copy mentions that the project is opinionated, AI-first, or exploratory, pair that with signals of discipline, seriousness, and care.
- **Keep the homepage short:** the landing page may contain a compact positioning block, but longer arguments belong in standalone article pages linked from the home or `articles.html`.
- **Use the founder article as the canonical long explanation:** the current anchor text is [`why-ezkey-exists.html`](why-ezkey-exists.html) / [`fr/why-ezkey-exists.html`](fr/why-ezkey-exists.html); reuse and refine that narrative instead of re-explaining it differently on every page.
- **Distinguish content lanes clearly:** product momentum belongs on `updates.html`; long-form reasoning, positioning, and engineering reflections belong in the **Essays** catalog on `articles.html`; evaluator walkthroughs belong in the **Guides** lane.
- **Write for a serious technical audience:** sober tone, concrete claims, explicit constraints, and no inflated startup-style language.
- **Avoid defensive wording:** do not over-explain that the project is solo, AI-assisted, or unconventional; present those facts plainly and move quickly to architecture, trust boundaries, APIs, and operator value.
- **Do not overstate security assurance:** describe Ezkey as a pragmatic, opinionated middle ground that aims to be stronger than passwords and classic TOTP for some backend-oriented contexts; do not imply formal attestation chains, standards equivalence, complete certificate validation, or full-strength certificate pinning unless those capabilities truly exist.
- **Treat complexity judgments as subjective:** when copy says Ezkey is simpler, lower-ceremony, or easier to operate, frame that as the project's perception or hope, not as an objective claim about WebAuthn, FIDO2, or what every team experiences.
- **When publishing new flagship articles, add visible date metadata:** long-form essays should read like dated notes in the open, not anonymous evergreen marketing copy.

---

## Related paths

| Path | Role |
| ---- | ---- |
| [README.md](README.md) | Short description of folder contents |
| [index.html](index.html) | English landing page |
| [fr/index.html](fr/index.html) | French landing page |
| [source-and-evaluation.html](source-and-evaluation.html) / [fr/source-and-evaluation.html](fr/source-and-evaluation.html) | Private repo; docs/run-local framing |
| [trust.html](trust.html) / [fr/trust.html](fr/trust.html) | Trust & security diligence |
| [updates.html](updates.html) / [fr/updates.html](fr/updates.html) | Product updates archive |
| [articles.html](articles.html) / [fr/articles.html](fr/articles.html) | Guides lane + articles index |
| [exp1-guided-tour.html](exp1-guided-tour.html) / [fr/exp1-guided-tour.html](fr/exp1-guided-tour.html) | Exp1 guided tour (EN/FR) |
| [changelog.html](changelog.html) / [fr/changelog.html](fr/changelog.html) | Changelog placeholder |
| [sitemap.xml](sitemap.xml) | Sitemap for crawlers |
| [robots.txt](robots.txt) | Robots + sitemap URL |
| [updates.rss](updates.rss) / [fr/updates.rss](fr/updates.rss) | Product updates RSS |
| [../../docs/cloudflare/README.md](../../docs/cloudflare/README.md) | Cloudflare docs index |
