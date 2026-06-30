# Ezkey.org static site — Agent notes

For agents working in **`sites/ezkey-org/`** (public landing page, mini blog copy, static assets) and for **Cloudflare Pages** operations that serve this content at **ezkey.org**.

For repo-wide rules (Maven, Java, etc.), see **[../../AGENTS.md](../../AGENTS.md)**. For Cloudflare workflow and time horizons, see **[../../docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)** and **[../../docs/cloudflare/README.md](../../docs/cloudflare/README.md)**.

---

## What this folder is

- **Source of truth in git:** static HTML/CSS under this directory — no bundler. **English** default locale; **French** under `fr/`.
- **API portal (first cut):** public API portal under [`api-docs.html`](api-docs.html) / [`fr/api-docs.html`](fr/api-docs.html) with per-API pages [`admin-api-reference.html`](admin-api-reference.html), [`auth-api-reference.html`](auth-api-reference.html), [`integration-api-reference.html`](integration-api-reference.html) and their French mirrors under `fr/`. Static OpenAPI JSON assets live under [`api-specs/`](api-specs/).
- **ReDoc CE dependency posture:** use the official Redocly standalone bundle with an **explicit pinned version**, not the floating `latest` alias. Current pinned version: **`v2.5.2`**.
- **ReDoc CE re-evaluation rule:** do **not** rotate versions on a calendar just to stay “current”. Re-evaluate only on one of three triggers: (1) security / availability / CDN-trust issue, (2) rendering or compatibility problem that affects the portal, or (3) explicit decision to revisit renderer posture (for example self-hosting or replacement). When triggered: pin explicit version -> preview -> human visual validation -> production.
- **French API portal posture:** localize the **portal shell** (navigation, summary, guidance) in French. Do **not** hand-translate generated OpenAPI annotation text in the site pages; the detailed spec content may remain in the source-language generated artifacts unless a dedicated contract-localization effort is explicitly approved.
- **Landing pages (compact):** [`index.html`](index.html) (en), [`fr/index.html`](fr/index.html) (fr) — hero, pillars, primary navigation, evaluation CTAs (run locally, docs, trust), and secondary links into product writing. They are **not** the archive for updates or articles.
- **Trust / diligence (short):** [`trust.html`](trust.html) / [`fr/trust.html`](fr/trust.html) — plain-language trust model, disclosure posture, security reporting (`security@ezkey.org`), and **on-site** pointers (no public GitHub URLs while the repository stays private).
- **Source & evaluation hub:** [`source-and-evaluation.html`](source-and-evaluation.html) / [`fr/source-and-evaluation.html`](fr/source-and-evaluation.html) — explains that Ezkey is **planned** as MIT open source but the **main repository is private** until the scheduled public opening; anchors `#documentation` and `#run-locally` are the nav targets for **Docs** / **Run locally**.
- **Product updates (reverse chronological, dated):** [`updates.html`](updates.html) / [`fr/updates.html`](fr/updates.html) — short build-status notes; optional `data-update-type` on each `<li class="update-entry">` for future filtering. This is the **product-facing timeline**. It cross-links to the changelog for future versioned notes.
- **Guides & walkthroughs hub:** [`guides.html`](guides.html) / [`fr/guides.html`](fr/guides.html) — dedicated hub for evaluator-oriented step-by-step content (exp1 guided tour, upcoming guides). Linked from the primary nav as **Guides**.
- **Articles & notes index:** [`articles.html`](articles.html) / [`fr/articles.html`](fr/articles.html) — two labeled sections: **About Ezkey** (product-explaining content, newest first) and **Craft & engineering** (independent essays, newest first). No longer contains a guides lane.
- **Changelog placeholder (technical release notes later):** [`changelog.html`](changelog.html) / [`fr/changelog.html`](fr/changelog.html) — reserved for versioned technical notes. **Not** listed on the primary navigation of the landing page or content hubs so an empty section does not dilute IA integrity; the changelog page itself repeats full nav **plus** a contextual “Changelog” / “Notes de version” item with `aria-current="page"`. Discoverable by URL and from copy on **Updates**.
- **Discovery & sharing (static):** [`sitemap.xml`](sitemap.xml) and [`robots.txt`](robots.txt) at the site root; [`updates.rss`](updates.rss) / [`fr/updates.rss`](fr/updates.rss) mirror the dated entries on **Updates** (newest items first in the feed). All published `*.html` pages include **Open Graph** and **Twitter Card** meta tags (`og:*`, `twitter:*`), using `https://ezkey.org/logo.svg` as the share image unless you introduce a dedicated social image later.
- **Logo / hero signature:** landing and inner hub pages reuse the same gradient, floating logo treatment, and particle background as before (`<img src="/logo.svg">` on published pages).
- **Public hostname:** **`ezkey.org`** (canonical production URL for this site).
- **Cloudflare:** The live site is deployed via **Cloudflare Pages** (project `ezkey-org`, domain `ezkey.org`). **Always use the dedicated scripts** — never invoke Wrangler ad hoc:
  - Preview: `scripts/cloudflare/deploy-ezkey-org-preview.sh` (sources `.env`, no git push required)
  - Production: `scripts/cloudflare/deploy-ezkey-org-production.sh` (same credentials, `--branch=main`)
  - Cleanup: `scripts/cloudflare/cleanup-ezkey-org-previews.sh`
  - On Windows without Git Bash, run the equivalent `npx wrangler pages deploy sites/ezkey-org --project-name=ezkey-org` with env vars loaded from `.env`. Preview URLs from Wrangler may show a `*.ezkey-teaser.pages.dev` subdomain — that is a **legacy hostname** for the same project, not a second app. Details: **[docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)**. When an agent reads or updates Cloudflare state, treat **what is configured in Cloudflare** as the operational source of truth for hosting; treat **this folder** as the source of truth for **content and markup** to deploy.

---

## Primary navigation (evaluation-first)

Hub pages (`index`, `updates`, `articles`, `trust`) share a consistent **primary** nav (EN/FR labels):

| English | French | Target |
| ------ | ------ | ------ |
| Home | Accueil | `/` / `/fr/` |
| Docs | Documentation | `/source-and-evaluation.html#documentation` / `/fr/source-and-evaluation.html#documentation` |
| API Docs | Documentation API | `/api-docs.html` / `/fr/api-docs.html` |
| Run locally | Exécuter en local | `/source-and-evaluation.html#run-locally` / `/fr/source-and-evaluation.html#run-locally` |
| Trust | Confiance | `/trust.html` / `/fr/trust.html` |
| Updates | Mises à jour | `/updates.html` / `/fr/updates.html` |
| Guides | Guides | `/guides.html` / `/fr/guides.html` |
| Articles | Articles | `/articles.html` / `/fr/articles.html` |
| Notes | Notes | `/notes.html` / `/fr/notes.html` |
| Methodology | Méthodologie | `/methodology.html` / `/fr/methodologie.html` |

**Changelog** is intentionally **omitted** from that primary strip until the page carries real versioned notes. **Methodology** (`/methodology.html` / `/fr/methodologie.html`) is the published rich view generated from `product-docs/methodology/view/index.html` via `scripts/publish-methodology-view.ps1`; update by re-running that script whenever the canonical changes. On **changelog** pages only, append the changelog item with `aria-current="page"`.

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

**Deploy reflex: always reach for the scripts in `scripts/cloudflare/` first — never invoke Wrangler ad hoc.**

- Preferred workflow: edit content → `deploy-ezkey-org-preview.sh` → human visual validation → `deploy-ezkey-org-production.sh`. See [ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md) for the full flow.
- **Preview deploy:** `scripts/cloudflare/deploy-ezkey-org-preview.sh` — sources `.env` from the repo root for `CLOUDFLARE_API_TOKEN` / `CLOUDFLARE_ACCOUNT_ID`. On Windows without Git Bash, run the equivalent `npx wrangler pages deploy sites/ezkey-org --project-name=ezkey-org --branch=<preview-branch>` after loading the env vars from `.env`.
- **Production deploy** (after preview approval): `scripts/cloudflare/deploy-ezkey-org-production.sh` — same credentials; deploys to the Cloudflare production branch (`main` by default).
- Do **not** assume DNS or edge rules are writable via every API token; some operations require specific Cloudflare permissions or dashboard steps.
- See [docs/cloudflare/README.md](../../docs/cloudflare/README.md) for the `scripts/cloudflare/` index.

---

## Draft Markdown (`draft-*.md`) — location and lifecycle

### Physical location — critical rule

**Draft files MUST live in `sites/ezkey-org-editorial/fr/`, never in `sites/ezkey-org/`.** Wrangler deploys everything under `sites/ezkey-org/` to ezkey.org with no filtering. Any `draft-*.md` left inside the deploy folder is publicly accessible in plain text.

```
sites/
  ezkey-org/            ← Wrangler deploys all of this — NO drafts here
  ezkey-org-editorial/  ← never touched by Wrangler
    fr/
      draft-*.md        ← all drafts live here
```

### YAML front matter — lifecycle metadata

Every draft carries a YAML front matter block. Standard fields:

```yaml
---
status: draft              # draft | published | archived
audience: "..."            # target reader, tone guidance — NOT published in HTML
# --- Fields added at publication time ---
published_html_en: /some-slug.html
published_html_fr: /fr/some-slug.html
published_date: 2026-05-13
html_amended_post_publish: false   # true if HTML was edited directly after publication
source_of_truth: html              # draft | html — which file is authoritative if they diverge
# --- Optional planning fields (pre-publication) ---
planned_slug_fr: "some-slug.html"
planned_canonical: "https://ezkey.org/fr/some-slug.html"
---
```

**`source_of_truth` semantics:**
- `draft` — the markdown is the master; HTML was generated from it and not since amended.
- `html` — the published HTML has been edited directly after generation; the markdown may be outdated. Treat the HTML as authoritative. The draft is an archived working document only.

### Article lifecycle states

| State | `status` value | `source_of_truth` | Location |
|-------|---------------|-------------------|----------|
| Work in progress | `draft` | `draft` | `ezkey-org-editorial/fr/` |
| Just published, HTML not yet amended | `published` | `html` | `ezkey-org-editorial/fr/` |
| Published, HTML amended post-publish | `published` | `html` | `ezkey-org-editorial/fr/` |
| No longer relevant | `archived` | — | `ezkey-org-editorial/fr/` |

### Publication checklist (when a draft is ready)

1. Generate the French HTML under `sites/ezkey-org/fr/<slug>.html`.
2. Generate or translate the English HTML under `sites/ezkey-org/<slug>.html`.
3. Add article cards to both `articles.html` and `fr/articles.html` (newest first within the correct section).
4. Add both URLs to `sitemap.xml`.
5. Update the draft front matter: set `status: published`, add `published_html_en`, `published_html_fr`, `published_date`, `html_amended_post_publish: false`, `source_of_truth: html`.
6. The draft stays in `ezkey-org-editorial/fr/` — do **not** move it to the deploy folder.
7. Deploy to production.

### Workflow flexibility

There is no requirement to route every HTML amendment through the draft. Editing the HTML directly is often the most efficient path. When that happens, set `html_amended_post_publish: true` and `source_of_truth: html` in the draft front matter. The draft then serves as an archived working document; it may eventually be deleted if it no longer adds value.

### Draft file conventions (two-layer structure)

1. **YAML front matter** at the very top — lifecycle metadata and editorial context; never copied into published HTML.
2. **HTML comment exclude blocks** for internal notes that must not publish:

   ```text
   <!-- ezkey-org:exclude-start
   Internal notes only. Not publishable.
   ezkey-org:exclude-end -->
   ```

   Do **not** use an empty `<!-- ezkey-org:exclude-start -->` immediately closed with `-->` — text after it would not be inside the comment and would still be treated as publishable.

   Use exclude blocks for: editorial objectives, internal working titles, scratch notes, illustration captions, duplicate context kept for drafting. **Headings and paragraphs outside these blocks** are the publishable article body.

When generating HTML from a draft, **omit** the YAML front matter and **omit** every `<!-- ezkey-org:exclude-start … ezkey-org:exclude-end -->` block entirely.

---

## Guides & experimenter content (Exp1)

- **Purpose:** Step-by-step evaluator flows (e.g. mobile preview) live in the **Guides & walkthroughs** lane on [`articles.html`](articles.html) / [`fr/articles.html`](fr/articles.html), not mixed with generic essay cards without labeling.
- **Published (bilingual):** [`exp1-guided-tour.html`](exp1-guided-tour.html) and [`fr/exp1-guided-tour.html`](fr/exp1-guided-tour.html) — canonical `hreflang` pair with `x-default` on the English URL. Screenshots and other assets: **ASCII kebab-case** filenames under [`exp1-guided-tour/`](exp1-guided-tour/) (e.g. `exp1-tour-04-enrollment-qr-challenge.webp`); both locale pages reference the same paths.
- **Working drafts (French, not linked from indexes):** e.g. [`fr/draft-exp1-guided-tour.md`](fr/draft-exp1-guided-tour.md) (source notes), [`fr/draft-exp1-admin-ui-overview.md`](fr/draft-exp1-admin-ui-overview.md) (not yet a published page). Do **not** link `draft-*.md` from live navigation or the article index.
- **Checklist when adding or changing a guide page:** update the `guides.html` / `fr/guides.html` hub, add or bump URLs in [`sitemap.xml`](sitemap.xml), and mirror Open Graph / Twitter / `hreflang` like other standalone articles.

---

## Information architecture and bilingual mirroring

| English path | French path | Purpose |
| ------------ | ----------- | ------- |
| `/` → `index.html` | `/fr/` → `fr/index.html` | Compact landing; evaluation CTAs |
| `/source-and-evaluation.html` | `/fr/source-and-evaluation.html` | Private-repo posture; docs/run-local explanation |
| `/api-docs.html` | `/fr/api-docs.html` | API portal landing page |
| `/trust.html` | `/fr/trust.html` | Trust, reporting, on-site pointers |
| `/updates.html` | `/fr/updates.html` | Product updates (newest first) |
| `/guides.html` | `/fr/guides.html` | Evaluator guides & walkthroughs hub |
| `/articles.html` | `/fr/articles.html` | Guides lane + article index |
| `/admin-api-reference.html` | `/fr/admin-api-reference.html` | Admin API public reference |
| `/auth-api-reference.html` | `/fr/auth-api-reference.html` | Auth API public reference |
| `/integration-api-reference.html` | `/fr/integration-api-reference.html` | Integration API public reference |
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
- **Distinguish content lanes clearly:** product momentum belongs on `updates.html`; evaluator walkthroughs belong on `guides.html`; product-explaining writing belongs in the **About Ezkey** section on `articles.html`; engineering craft essays belong in the **Craft & engineering** section on `articles.html`.
- **Write for a serious technical audience:** sober tone, concrete claims, explicit constraints, and no inflated startup-style language.
- **Avoid defensive wording:** do not over-explain that the project is solo, AI-assisted, or unconventional; present those facts plainly and move quickly to architecture, trust boundaries, APIs, and operator value.
- **Do not overstate security assurance:** describe Ezkey as a pragmatic, opinionated middle ground that aims to be stronger than passwords and classic TOTP for some backend-oriented contexts; do not imply formal attestation chains, standards equivalence, complete certificate validation, or full-strength certificate pinning unless those capabilities truly exist.
- **Treat complexity judgments as subjective:** when copy says Ezkey is simpler, lower-ceremony, or easier to operate, frame that as the project's perception or hope, not as an objective claim about WebAuthn, FIDO2, or what every team experiences.
- **When publishing new flagship articles, add visible date metadata:** long-form essays should read like dated notes in the open, not anonymous evergreen marketing copy.

---

## Monthly activity field notes

The **Field notes** nav lane (`/notes.html`, `/fr/notes.html`) is dedicated to **monthly
Git-distilled activity digests** only — not day-level reflections or long-form essays.

- **Trigger phrase:** *"time for the monthly activity field note"* or *"do the monthly digest"*.
  A cold agent should run [`.cursor/skills/monthly-field-note/SKILL.md`](../../.cursor/skills/monthly-field-note/SKILL.md).
- **Template:** [`../ezkey-org-editorial/templates/monthly-field-note.template.md`](../ezkey-org-editorial/templates/monthly-field-note.template.md)
- **Detailed note slug:** `field-note-YYYY-MM-monthly-activity.html`; meta line:
  `Monthly field note · <Month> <Year>`.
- **Index:** single page (`notes.html` / `fr/notes.html`), newest month first — no monthly archive
  pages under `notes/YYYY-MM.html`.
- **Product updates pairing:** each new digest also produces a one-paragraph milestone blurb on
  `updates.html` (EN + FR + RSS) with a link to the detailed note. Pre-existing hand-written
  updates may keep their copy; add the link when a digest is published retroactively.
- **Tone:** sober, factual, structured summary — never a commit log. Author in English first,
  then French mirror at publish time.

**Distinction from other lanes:**

| Lane | Role |
| ---- | ---- |
| **Product updates** | Short outward-facing milestone paragraph (+ link to detailed note when available) |
| **Field notes** | Detailed monthly digest by subject |
| **Articles** | Long-form essays and craft writing |

---

## Visual design identity and patterns

This section records the design decisions made during active development, in reverse chronological order. Use it as a reference when evolving the site to ensure new work stays coherent with established patterns.

### May 2026 — Amber pill section dividers on `articles.html`

**Decision:** Section headings on `articles.html` / `fr/articles.html` use an **amber pill divider** style (Direction B, chosen over a plain rule and an architectural band after A/B/C preview comparison).

**Implementation (`.page-subheading` CSS + `<span>` wrapper in HTML):**
- Container: `display: flex; align-items: center; gap: 16px;` — traits grow from each side of the pill via `::before` / `::after` pseudo-elements.
- Left trait: `linear-gradient(90deg, transparent → rgba(251,191,36,0.62))`, right trait: inverse direction.
- Pill (`span`): `font-size: 0.78rem`, `font-weight: 700`, `letter-spacing: 0.14em`, `text-transform: uppercase`, `color: #fef3c7` (warm cream), `padding: 5px 15px`, `border-radius: 20px`, `background: rgba(251,191,36,0.13)`, `border: 1px solid rgba(251,191,36,0.48)`, `box-shadow: 0 0 14px rgba(251,191,36,0.2)`.
- HTML pattern: `<p class="page-subheading"><span>Section title</span></p>`.

**Rationale:** The pill is immediately recognisable during fast scroll; it creates a visual pause without being aggressive. It borrows directly from the EXP1 badge color vocabulary on the homepage, creating site-wide coherence without introducing a new palette.

**Sections using this pattern:** "About Ezkey" / "À propos d'Ezkey" and "Craft & engineering" / "Craft & ingénierie" on `articles.html` / `fr/articles.html`.

---

### April–May 2026 — Article card pattern (`pub-card`)

**Decision:** Long-form article cards on `articles.html` / `fr/articles.html` always include a **dated meta line** above the title (e.g. `Essay · May 13, 2026`). Cards are sorted **newest first** within each section.

**Implementation (`.pub-card`):**
- `background: rgba(255,255,255,0.08)`, `border: 1px solid rgba(255,255,255,0.2)`, `border-radius: 16px`, `padding: 22px 22px 20px`.
- Hover: `translateY(-4px)` lift + `box-shadow`.
- Elements in order: `.pub-card-meta` (date + type, uppercase, 0.76rem, muted), `.pub-card-title` (1.08rem, white, 600), `.pub-card-desc` (0.92rem, white 78%, flex:1), `.pub-card-cta` (underline bottom border, 0.88rem).
- Grid: `repeat(auto-fit, minmax(260px, 1fr))`, gap 18px.

**Rationale:** Dates visible on cards reinforce the "dated notes in the open" editorial principle. Two sections ("About Ezkey" product content vs. "Craft & engineering" independent essays) separate product-explaining articles from methodology writing.

---

### Design palette summary

The site uses a **single coherent palette** derived from the background gradient:

| Role | Color / value | Usage |
| ---- | ------------- | ----- |
| Background gradient | `#667eea → #764ba2` (135°) | `body` on all pages |
| Brand amber (primary accent) | `#f59e0b` / `#ea580c` / `#fde68a` gradient | EXP1 badge, pill dividers, amber glow effects |
| Pill accent | `rgba(251, 191, 36, …)` at various opacities | Section divider pills, divider lines |
| Warm cream text | `#fef3c7` | Pill label text, accent headings |
| White primary | `#ffffff` | Card titles, nav links |
| White muted | `rgba(255,255,255, 0.62–0.82)` | Meta, descriptions, secondary text |
| Panel background | `rgba(255,255,255, 0.08–0.15)` | Cards, page-panel |

**Rule for new visual elements:** before introducing a new color, check whether the amber family or the existing white-on-gradient treatments already cover the need. Prefer extending the amber vocabulary over introducing new hues.

---

## Related paths

| Path | Role |
| ---- | ---- |
| [README.md](README.md) | Short description of folder contents |
| [../../sites/ezkey-org-editorial/fr/](../../sites/ezkey-org-editorial/fr/) | Editorial drafts — NOT deployed by Wrangler |
| [index.html](index.html) | English landing page |
| [fr/index.html](fr/index.html) | French landing page |
| [source-and-evaluation.html](source-and-evaluation.html) / [fr/source-and-evaluation.html](fr/source-and-evaluation.html) | Private repo; docs/run-local framing |
| [trust.html](trust.html) / [fr/trust.html](fr/trust.html) | Trust & security diligence |
| [updates.html](updates.html) / [fr/updates.html](fr/updates.html) | Product updates archive |
| [guides.html](guides.html) / [fr/guides.html](fr/guides.html) | Evaluator guides & walkthroughs hub |
| [articles.html](articles.html) / [fr/articles.html](fr/articles.html) | About Ezkey + Craft & engineering sections |
| [exp1-guided-tour.html](exp1-guided-tour.html) / [fr/exp1-guided-tour.html](fr/exp1-guided-tour.html) | Exp1 guided tour (EN/FR) |
| [changelog.html](changelog.html) / [fr/changelog.html](fr/changelog.html) | Changelog placeholder |
| [sitemap.xml](sitemap.xml) | Sitemap for crawlers |
| [robots.txt](robots.txt) | Robots + sitemap URL |
| [updates.rss](updates.rss) / [fr/updates.rss](fr/updates.rss) | Product updates RSS |
| [../../docs/cloudflare/README.md](../../docs/cloudflare/README.md) | Cloudflare docs index |
