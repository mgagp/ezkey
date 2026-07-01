---
name: monthly-digest
description: Distills a calendar month of Git activity into a structured monthly digest published in the ezkey.org monthly-digests lane (index excerpt + detailed page). Use when the operator asks for the monthly digest / monthly activity recap.
disable-model-invocation: true
---
# Monthly Digest

## Purpose

Turn one calendar month of repository Git history into a short, distilled activity digest —
an executive summary plus a breakdown by subject — and publish it on ezkey.org in the unified
**Monthly digests** lane. The value is comparable month-over-month signal, not an exhaustive
changelog.

## Boundary contract

- **Enter when:** the operator asks for the monthly digest / monthly activity recap, or a
  calendar month has closed and a recap is wanted.
- **Exit when:** the digest is distilled into the template, the bilingual detail page is
  published (or staged), the index excerpt is added to `monthly-digest.html` / RSS (EN + FR),
  and `sitemap.xml` is updated.
- **Call next:** the Cloudflare preview deploy for human visual validation, then production.
- **Not needed when:** the operator wants a product-facing **versioned** release note (use
  `changelog.html` when that lane carries real content).

## Input

- The target month (default: the most recently completed calendar month).
- Repository Git history for that window (working/default branch).
- Optional enrichment: `I-*` / `TB-*` artifacts closed during the month for program names.
- Template: `sites/ezkey-org-editorial/templates/monthly-digest.template.md`.

## Output

- A draft at `sites/ezkey-org-editorial/fr/draft-monthly-digest-YYYY-MM.md`
  (body authored in English first).
- Published bilingual HTML detail pages: `sites/ezkey-org/monthly-digest-YYYY-MM.html` (EN) and
  `sites/ezkey-org/fr/monthly-digest-YYYY-MM.html` (FR).
- **Index excerpt** (draft section in the markdown file; published into the matching
  `<li class="digest-entry" id="digest-YYYY-MM">` on `monthly-digest.html` and
  `fr/monthly-digest.html`, plus RSS mirrors).
- One new digest entry at the **top** of the index lists (newest first).
- Detail page URLs added to `sitemap.xml`.

## Procedure

1. **Fix the window.** Determine `YYYY-MM`; the range is `[YYYY-MM-01, next-month-01)`.
2. **Pull the raw signal**, always with `--no-merges` to cut noise from merge/multi-area commits:
   - Subjects/volume: `git log --no-merges --since="YYYY-MM-01" --until="YYYY-MM+1-01" --pretty="%h %s"`
   - Per-path footprint: `git log --no-merges --since=... --until=... --pretty="%s" -- <path>`
   - Optional shape: `git shortlog -sn --no-merges --since=... --until=...`
   - Note: path-scoped counts still overlap when one commit touches several areas; read them as
     a qualitative footprint for the trend sentence, not exact totals.
3. **Map commits to subject buckets by path** (see palette below) and gauge where the energy went.
   The proportion across buckets drives the "In short" trend sentence.
4. **Distill, do not dump.** Write the executive summary, then 1-3 sentences per *active* bucket.
   Cite at most a few concrete milestones. Never list commits line by line.
5. **Write the index excerpt** (see rules below) — a tighter version of "In short" for the hub page.
6. **Instantiate the template** into the draft path above; author in English first.
7. **Omit empty sections** — only render buckets that saw real activity. Keep the palette order so
   months stay comparable.
8. **Publish bilingually:** create EN + FR HTML for the detailed digest; prepend the index entry on
   `monthly-digest.html` and `fr/monthly-digest.html`; add matching RSS `<item>` at the top of
   [`monthly-digest.rss`](../../sites/ezkey-org/monthly-digest.rss) and
   [`fr/monthly-digest.rss`](../../sites/ezkey-org/fr/monthly-digest.rss); add detail URLs to
   `sitemap.xml`.
9. **Hand off to deploy:** Cloudflare preview -> human visual validation -> production.

## Index excerpt rules

- **Length:** one short sentence or two (typically under ~30 words). No bullet lists.
- **Source:** derived from the detailed digest's "In short" block — same facts, tighter prose.
- **No month prefix:** do **not** open with "A distilled digest of [Month]:" (EN) or
  "Un sommaire distillé de [month]:" (FR). The month already appears in `.digest-date`; go
  straight to the substance (e.g. *"A more useful admin console, …"* not *"A distilled digest of
  June: a more useful admin console, …"*).
- **Tone:** sober, outward-facing, momentum and context.
- **Index HTML pattern** (months with a full digest):
  - Month label in `.digest-date`
  - Title as link (`.digest-title`) → `/monthly-digest-YYYY-MM.html` (EN) /
    `/fr/monthly-digest-YYYY-MM.html` (FR)
  - Excerpt in `.digest-text`
- **RSS:** mirror the excerpt in the matching `<item>` description; link to the detail page URL.
- **Legacy index-only months:** older hand-written entries (Feb–Apr 2026 and earlier without a
  digest page) keep their paragraph on the index with an optional type pill; do not rewrite unless
  the operator asks. Retrofill later by running this skill for those months.

## Monthly digests index (single page)

- **One index only:** `/monthly-digest.html` and `/fr/monthly-digest.html` — no monthly archive
  pages under `monthly-digest/YYYY-MM/`.
- **Content:** monthly Git-distilled activity digests (detail pages when published) plus legacy
  index-only milestone paragraphs for months not yet retrofilled.
- **Ordering:** newest month first.
- **Page intro:** state that these are monthly distilled looks at project activity from Git
  history — not day-by-day reflections.

## Subject palette (path -> bucket)

| Bucket | Typical paths |
| --- | --- |
| Documentation & methodology | `product-docs/`, `docs/` |
| Code hygiene | dependency bumps, `.cursor/`, `.github/`, tooling configs, React Doctor, mobile upgrades |
| Admin UI | `ezkey-admin-ui/` |
| Mobile app | `ezkey_mobile/` |
| Backend & API contracts | `ezkey-core*/`, `ezkey-*-api/`, `specs/`, `postman/` |
| The rest | `ezkey-sdk/`, `ezkey-tests/`, `docker/`, `sites/ezkey-org/`, audit/integrity code |

## Distillation rules

- One executive paragraph that states the dominant theme(s) and the month's rhythm.
- Aggregate by subject; translate volume into a qualitative trend, not raw counts.
- **Summarize the nature and intent of the work, not internal tracking notations.** The activity
  narrative (what changed and why it matters to a reader) wins over internal milestone bookkeeping.
  Translate or drop notations that do not communicate on their own:
  - Drop tier labels (`Tier A/B`), wave/cluster codenames (`Wave B`), security-finding IDs
    (`SEC-001`), issue/PR numbers (`#251`), and HTTP status codes (`202 Accepted`).
  - Keep concrete facts that are self-explanatory (e.g. *"Spring Boot 4.1 / Jackson 3
    modernization"*, *"a move to the latest React"*).
  - Restate internal motion as plain meaning, e.g. `Tier A/B list enrichment` →
    *"lists and detail screens now show meaningful names and context instead of bare identifiers"*;
    `Wave B integrity cluster` → *"work began on letting operators verify the audit trail has not
    been tampered with"*.
- Sober, factual tone; no inflated or marketing language; honest about scope and quiet months.
- English canonical, then a faithful French mirror — do not invent content in either locale.
- **French HTML accents use named HTML entities** (`&eacute;`, `&egrave;`, `&agrave;`, `&ccedil;`,
  `&ocirc;`, ...), never raw accented characters. This is a deliberate convention adopted after
  repeated UTF-8 handling problems: verbose but infallible across the toolchain. Match the existing
  published French digests.
- **French technical vocabulary follows common developer usage, not the most formal OQLF term**
  when the two diverge. Clear, expected wording wins. In particular, use **`framework`** (not
  *cadriciel* or *cadre applicatif*).

## Detail page conventions

- **Slug:** `monthly-digest-YYYY-MM.html` (same filename under `fr/`).
- **Meta line:** `Monthly digest · <Month> <Year>` (FR: `Sommaire mensuel · <Month> <Year>`).
- **Nav back-link:** `/monthly-digest.html` (EN) / `/fr/monthly-digest.html` (FR).

## Rule

This is a distilled digest, never a commit log. If a month was quiet, say so plainly and keep it
short; do not pad with empty sections to look productive.

## Related documents

- `sites/ezkey-org/AGENTS.md` § *Monthly digests*
- `sites/ezkey-org-editorial/templates/monthly-digest.template.md`
