---
name: monthly-field-note
description: Distills a calendar month of Git activity into a structured monthly field note (activity digest) published in the ezkey.org field-notes lane, plus a one-paragraph Product Update blurb linked to the detailed note. Use when the operator asks for the monthly activity field note / monthly digest.
disable-model-invocation: true
---
# Monthly Field Note

## Purpose

Turn one calendar month of repository Git history into a short, distilled activity digest —
an executive summary plus a breakdown by subject — and publish it on ezkey.org. Also produce
a one-paragraph **Product Update** blurb (EN + FR) for `updates.html`, with a link to the
detailed note. The value is comparable month-over-month signal, not an exhaustive changelog.

## Boundary contract

- **Enter when:** the operator asks for the monthly activity field note / monthly digest, or a
  calendar month has closed and a recap is wanted.
- **Exit when:** the digest is distilled into the template, the bilingual field note is published
  (or staged), the Product Update blurb is added to `updates.html` / RSS (EN + FR), and
  `notes.html` / `sitemap.xml` are updated.
- **Call next:** the Cloudflare preview deploy for human visual validation, then production.
- **Not needed when:** the operator wants a product-facing **versioned** release note (use
  `changelog.html` when that lane carries real content).

## Input

- The target month (default: the most recently completed calendar month).
- Repository Git history for that window (working/default branch).
- Optional enrichment: `I-*` / `TB-*` artifacts closed during the month for program names.
- Template: `sites/ezkey-org-editorial/templates/monthly-field-note.template.md`.

## Output

- A draft at `sites/ezkey-org-editorial/fr/draft-field-note-YYYY-MM-monthly-activity.md`
  (body authored in English first).
- Published bilingual HTML: `sites/ezkey-org/field-note-YYYY-MM-monthly-activity.html` (EN) and
  `sites/ezkey-org/fr/field-note-YYYY-MM-monthly-activity.html` (FR).
- **Product Update blurb** (draft section in the markdown file; published into the matching
  `<li class="update-entry" id="update-YYYY-MM">` on `updates.html` and `fr/updates.html`, plus
  RSS mirrors).
- One new `<li class="note-entry">` at the **top** of `notes.html` and `fr/notes.html` (newest
  first).
- URLs added to `sitemap.xml`.

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
5. **Write the Product Update blurb** (see rules below) — a tighter version of "In short".
6. **Instantiate the template** into the draft path above; author in English first.
7. **Omit empty sections** — only render buckets that saw real activity. Keep the palette order so
   months stay comparable.
8. **Publish bilingually:** create EN + FR HTML for the detailed note; prepend the index entry on
   `notes.html` and `fr/notes.html`; add or update the Product Update entry on `updates.html`,
   `fr/updates.html`, and both RSS feeds; add URLs to `sitemap.xml`.
9. **Hand off to deploy:** Cloudflare preview -> human visual validation -> production.

## Product Update blurb rules

- **Length:** one short paragraph (typically 3–5 sentences). No bullet lists.
- **Source:** derived from the detailed note's "In short" block — same facts, tighter prose.
- **Tone:** milestone posture — sober, outward-facing, momentum and context (matches existing
  `data-update-type="milestone"` entries).
- **Required link:** end the paragraph with an inline link to the detailed monthly note:
  - EN label: `Detailed monthly field note` → `/field-note-YYYY-MM-monthly-activity.html`
  - FR label: `Note de terrain mensuelle détaillée` → `/fr/field-note-YYYY-MM-monthly-activity.html`
  - Use the same inline link styling as existing update entries (`color:white;font-weight:600;text-decoration:underline`).
- **RSS:** mirror the HTML blurb in the matching `<item>` description; include the detailed note URL.
- **Pre-existing hand-written updates:** do not rewrite older milestone copy unless the operator
  asks. When a month already has a hand-written update, adding the link to the detailed note is
  sufficient.

## Field notes index (single page)

- **One index only:** `/notes.html` and `/fr/notes.html` — no monthly archive pages under
  `notes/YYYY-MM.html`.
- **Content:** monthly Git-distilled activity digests only (no day-level notes).
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
  published French notes.
- **French technical vocabulary follows common developer usage, not the most formal OQLF term**
  when the two diverge. Clear, expected wording wins. In particular, use **`framework`** (not
  *cadriciel* or *cadre applicatif*).

## Rule

This is a distilled digest, never a commit log. If a month was quiet, say so plainly and keep it
short; do not pad with empty sections to look productive.

## Related documents

- `sites/ezkey-org/AGENTS.md` § *Monthly activity field notes*
- `sites/ezkey-org-editorial/templates/monthly-field-note.template.md`
