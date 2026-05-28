# Blitz Intake Pattern

## Purpose

The blitz intake pattern is a Lane A technique for capturing several ideation items in a single session, classifying them as a batch, materializing them into canonical product-docs artifacts, and **preserving the verbatim source** for later audit, re-derivation, or retrofit.

It complements the standard Lane A flow when the operator has multiple ideas to express in one go, with maturity ranging from a rough sketch to a long detailed dictation. It is particularly useful when voice dictation is in play, because verbatim wording is then irreplaceable source material that must not be lost during materialization.

## When to use

Use the blitz intake pattern when:

- the operator has multiple items to capture in one session,
- maturity varies across items (a few sentences to a long detailed dictation),
- classification and materialization should not interrupt capture flow,
- voice dictation is plausible and verbatim preservation matters.

For a single, focused idea, the standard Lane A flow (`vision-intake`, then `backlog-triage`) is faster and lighter.

## Phases

### Phase 0 — Setup

- Confirm signaling conventions (see below).
- **Identify and record the person conducting the session** — the `Captured by` field in the scratch board header. This is the human operator who dictated or provided the input, not the AI agent. When multiple people contribute in the same session, list them. This field is mandatory and must be set before capture begins.
- **Choose a topic slug** for the scratch board file (e.g. `mobile-auth`, `api-keys-redesign`).
  On a feature branch or worktree the slug is **required** — it prevents filename collisions at
  merge time. On `main` / single-branch, an ordinal suffix is acceptable if the theme is not
  yet clear. See File naming below and `multi-branch-workflow.md`.
- On a single-branch session: identify the next available IDs by reading existing indexes:
  - `product-docs/global/backlog/index.md` for `I-*`,
  - `product-docs/global/vision/product-orientation-notes.md` for `V-*`,
  - `product-docs/global/legacy-retrofit/index.md` for `R-*`.
  On a feature branch: defer index lookups — use date+slug IDs directly (no counter needed).
- Create the scratch board file at the active location (see file naming below).

### Phase 1 — Capture

- Transcribe each item verbatim into the scratch board under a draft slug (`D1`, `D2`, ...).
- Do not interrupt unless a phrase is unintelligible (one short clarification at most; otherwise mark `[?]` and move on).
- When the operator signals end-of-item, move to the next.
- Capture verbatim in the original language (typically French in this project). Translation to English happens at materialization.
- Apply only minimal normalization (for example, project-name variants normalized according to local dictation rules).

### Phase 2 — Classification

For each `Dn`, propose a compact classification record covering:

- **Lane** — A-vision, A-backlog, B-delivery, C-retrofit, principle candidate, code-only, or vague-to-reformulate.
- **Type** — `V` / `I` / `TB` / `R` / `ADR` / `principle` / `code-only`.
- **Status** — `captured` or `triaged` initially.
- **Priority** — `P0` / `P1` / `P2` / `P3` with one-line rationale.
- **Phase tag(s)** — using the catalog vocabulary (`P0-foundations` ... `P4-compliance-readiness`).
- **Component tag(s)** — concrete component or module names.
- **Profile** — security, performance, operability, UX, debt, feature, docs, etc.
- **Confidence** — `high` / `medium` / `low`. Low confidence flags items that need explicit operator validation.

Cross-check against existing artifacts to detect:

- **Existing plans** that make the item a retrofit candidate (lane C),
- **Adjacency** to existing `V-*` / `I-*` / `R-*` to add cross-links,
- **Transversal patterns** that may merit a parent `V-*` (for example, a deployment-profile theme that recurs).

Validate per item, per batch of 5–10, or all at once depending on operator preference.

### Phase 3 — Materialization

- Write canonical artifacts (`V-*` files, `I-*` files, `R-*` files) in **English**.
- Use **date+slug identifiers** for all new artifacts (e.g. `I-2026-05-22-rate-limit-analysis`);
  see [`nomenclature.md`](nomenclature.md) for the full convention.
- Add cross-links between artifacts and to existing related artifacts.
- **On a feature branch or worktree:** do **not** update index files (`backlog/index.md`,
  `vision/product-orientation-notes.md`, `legacy-retrofit/index.md`). Index updates happen
  post-merge on `main`. See [`multi-branch-workflow.md`](multi-branch-workflow.md).
- **On `main` or in a single-branch session:** update the relevant indexes after writing the
  artifact files.

### Phase 4 — Archival (mandatory; **never deletion**)

- Move the scratch board from its active location (`_blitz-...md`) to the archive subfolder (`blitz-archive/blitz-...md`), dropping the `_` prefix.
- Add a header line in the archived file indicating the canonical artifacts the items were materialized into.
- The operator decides when (or if) to delete the archived scratch.

### Archived scratch board header fields

The header of an archived scratch board must include the following fields:

```
> **Status:** archived after materialization. …
> **Materialized into:** …
> **Captured by:** [name or initials of the human operator]
> **Source language:** …
> **Date captured:** YYYY-MM-DD.
> **Items:** Dn → Dm.
```

The `Captured by` field identifies the human operator(s) who provided the input. It must be preserved verbatim from the active scratch board into the archive.

## Why preserve the verbatim post-materialization

- The verbatim is **irreplaceable source material**, especially under voice dictation.
- Materialized `V-*` / `I-*` / `R-*` artifacts are **filtered and reformulated** in English; the scratch retains nuance, hesitations, and contextual reasoning that may not survive translation.
- The verbatim supports **audit** of the materialization (was it faithful?).
- The verbatim supports **re-derivation** if a `V-*` / `I-*` later needs revision.
- The verbatim is reusable as **legacy retrofit input** if the methodology evolves.

## Capture conventions (signaling)

| Signal | Effect |
|--------|--------|
| `point suivant` / `next` | Close current item, move to next |
| `je reprends ce point` / `j'ajoute` | Append to last open item |
| `annule ce point` | Discard the last `Dn` |
| `pause` | Stop for discussion before next item |
| `fin du blitz` | Close the capture phase, proceed to classification |

The agent accepts French or English forms. The agent does not auto-detect end-of-item from silence or topic shift; an explicit signal is required.

## File naming and locations

| Context | State | Location | Example |
|---------|-------|----------|---------|
| Feature branch / worktree | **Active** | `product-docs/global/backlog/_blitz-YYYY-MM-DD-<slug>.md` | `_blitz-2026-05-22-mobile-auth.md` |
| Feature branch / worktree | **Archived** | `product-docs/global/backlog/blitz-archive/blitz-YYYY-MM-DD-<slug>.md` | `blitz-2026-05-22-mobile-auth.md` |
| `main` / single-branch | **Active** | `product-docs/global/backlog/_blitz-YYYY-MM-DD[-N].md` | `_blitz-2026-05-08-2.md` |
| `main` / single-branch | **Archived** | `product-docs/global/backlog/blitz-archive/blitz-YYYY-MM-DD[-N].md` | `blitz-2026-05-08-1.md` |
| Any | **Deleted** | n/a | Operator-initiated only; never automatic |

**On a feature branch or worktree**: the slug is **required**, not optional. Choose it at session
start — it answers "what is this blitz about?" before capture begins. This prevents filename
collisions at merge time and relies on the orthogonality assumption: when topics are distinct by
design and coordination, their slugs will be distinct too. See
`methodology/decisions/2026-05-22-blitz-naming-multi-branch.md` for the full rationale.

**On `main` / single-branch**: the `[-N]` ordinal is acceptable as a fallback when the theme is
not yet clear at session start. Use the suffix consistently even for the first blitz when a second
blitz on the same date exists or is anticipated.

## Materialization completion criteria

A blitz materialization is complete when **all** of the following are true:

- every captured item has a stable ID assigned (or has been intentionally dropped with reason recorded),
- canonical artifacts (`V-*`, `I-*`, `R-*`) are written and consistent,
- relevant indexes are updated,
- cross-links between artifacts are in place,
- the scratch board has been **moved** (not deleted) to `blitz-archive/`,
- the archived file's header records the list of materialized artifact IDs.
- after grill sessions are **complete**, backlog and retrofit statuses are aligned: grilled `I-*` at least `incubating` (not stale `captured`); `R-*` at least `mapped` once the mapping table is filled (see checklist point 4–5 in [`nomenclature.md`](nomenclature.md)).
use the **Corpus completeness audit** checklist in [`nomenclature.md`](nomenclature.md).

## AI agent rule

When an AI agent drives a blitz materialization, it MUST:

- move the scratch board to `blitz-archive/` rather than deleting it,
- preserve the original verbatim content; the only edits allowed are agreed normalizations (such as phonetic variants of the project name),
- record the list of materialized artifact IDs at the top of the archived file,
- **preserve the `Captured by` field exactly as recorded in the active scratch board** — never replace it with "the user", "the operator", or the AI agent's own identity,
- never delete an archived scratch board autonomously — only the operator may delete archived scratches.

When propagating blitz authorship to canonical artifacts (`I-*`, `V-*`, `TB-*`), carry the `Captured by` value forward into the `Captured by` metadata field of each materialized artifact.

## Archive cleanup

- Operator-initiated only. No automatic expiration.
- If the archive grows large, year-based subfolders (`blitz-archive/2026/`) may be introduced; not needed initially.

## Related documents

- [`workflow-overview.md`](workflow-overview.md) — Lane A standard flow.
- [`session-start-guide.md`](session-start-guide.md) — when to choose the blitz intake variant.
- [`nomenclature.md`](nomenclature.md) — IDs and filename conventions.
- [`legacy-retrofit-workflow.md`](legacy-retrofit-workflow.md) — when a captured item is a retrofit candidate.
- [`../../.cursor/rules/voice-dictation.mdc`](../../.cursor/rules/voice-dictation.mdc) — voice-dictation phonetic normalization.
