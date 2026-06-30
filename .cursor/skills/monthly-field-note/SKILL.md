---
name: monthly-field-note
description: Distills a calendar month of Git activity into a structured monthly field note (activity digest) published in the ezkey.org field-notes lane. Use when the operator asks for the monthly activity field note / monthly digest.
disable-model-invocation: true
---
# Monthly Field Note

## Purpose

Turn one calendar month of repository Git history into a short, distilled activity digest —
an executive summary plus a breakdown by subject — and publish it as a monthly field note on
ezkey.org. The value is comparable month-over-month signal, not an exhaustive changelog.

## Boundary contract

- **Enter when:** the operator asks for the monthly activity field note / monthly digest, or a
  calendar month has closed and a recap is wanted.
- **Exit when:** the digest is distilled into the template, the bilingual field note is published
  (or staged), and the indexes/sitemap are updated.
- **Call next:** the Cloudflare preview deploy for human visual validation, then production.
- **Not needed when:** the operator wants a day-level brain-dump note (use the informal field-notes
  lane instead), or a product-facing release note (use `updates.html`).

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
- Updated note indexes (`notes.html`, `notes/YYYY-MM.html`, French mirrors) and `sitemap.xml`.

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
5. **Instantiate the template** into the draft path above; author in English first.
6. **Omit empty sections** — only render buckets that saw real activity. Keep the palette order so
   months stay comparable.
7. **Publish bilingually** following the field-notes flow: create EN + FR HTML, add a
   `<li class="note-entry">` to `notes.html`, `fr/notes.html`, and the relevant
   `notes/YYYY-MM.html` mirrors, then add the URLs to `sitemap.xml`.
8. **Hand off to deploy:** Cloudflare preview -> human visual validation -> production.

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
- **French technical vocabulary follows common developer usage, not the most formal OQLF term**,
  when the two diverge. Clear, expected wording wins. In particular, use **`framework`** (not
  *cadriciel* or *cadre applicatif*).

## Rule

This is a distilled digest, never a commit log. If a month was quiet, say so plainly and keep it
short; do not pad with empty sections to look productive.

## Related documents

- `sites/ezkey-org/AGENTS.md` § *Informal field notes (brain dump lane)* — monthly digest sub-type
- `sites/ezkey-org-editorial/templates/monthly-field-note.template.md`
