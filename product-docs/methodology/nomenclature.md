# Nomenclature

## Purpose

This document defines stable IDs and status vocabulary for vision, backlog, and tracer-bullet work.

## Identifier conventions

- `V-YYYY-NNNN` — vision notes
- `I-YYYY-NNNN` — backlog ideas
- `TB-YYYY-NNNN` — tracer bullets
- `R-YYYY-NNNN` — legacy retrofit slices
- `F-<short-kebab-name>` — feature catalog entries
- `ADR-XXXX` / scoped ADR IDs — architecture or design decisions

## Filename conventions

- `V-YYYY-NNNN-<slug>.md`
- `I-YYYY-NNNN-<slug>.md`
- `TB-YYYY-NNNN-<slug>.md`
- `R-YYYY-NNNN-<slug>.md`

Use stable IDs. Do not reuse retired identifiers.

## Blitz scratch board filename conventions

Blitz intake scratch boards (see [`blitz-intake-pattern.md`](blitz-intake-pattern.md)) follow:

- **Active** (during the session): `product-docs/global/backlog/_blitz-YYYY-MM-DD[-N].md` (with `_` prefix as a staging signal).
- **Archived** (post-materialization): `product-docs/global/backlog/blitz-archive/blitz-YYYY-MM-DD[-N].md` (no `_` prefix).

`[-N]` is an optional ordinal for multiple blitz sessions on the same day (omitted for the first, `-2` for the second, etc.). Archived scratches are never deleted automatically; deletion is operator-initiated.

## Backlog status vocabulary

Backlog ideas (`I-*`) use the following lifecycle:

- `captured` — raw idea recorded.
- `triaged` — clarified and categorized.
- `incubating` — being explored but not yet execution-ready.
- `ready` — actionable for tracer-bullet promotion.
- `active` — currently under execution.
- `done` — fully delivered and closed.
- `parked` — intentionally paused for later review.
- `archived` — retained for history, not active.
- `dropped` — explicitly rejected.

## Vision status vocabulary

Vision notes (`V-*`) follow a lightweight 4-state lifecycle:

- `draft` — orientation captured, not yet stress-tested.
- `under-review` — under grilling, or a decision has been tranched but is not yet recorded in a durable artifact.
- `promoted` — substance canonized into a durable artifact (e.g. `architecture-decisions.md`, `design-principles.md`, the feature catalog in `features-and-phases.md`). The vision note has fulfilled its orientation purpose successfully.
- `archived` — retained for history without canonization. Used when the orientation is abandoned, merged into another note, or made obsolete. Not a failure flag, just a non-promotion outcome.

A vision note is **not** the canonical home for a final decision. When a decision is made, the orientation transitions to `under-review`, and the substance must be canonized in a durable artifact before the note moves to `promoted`.

## Tracer-bullet status vocabulary

Tracer bullets (`TB-*`) follow the same 4-state lifecycle as vision notes:

- `draft` — slice scope and exit criteria sketched, not yet executing.
- `under-review` — slice executed, awaiting closure and canonization of learnings.
- `promoted` — learnings and decisions canonized into the durable artifacts they touched (component design notes, ADRs, feature catalog, etc.). The tracer bullet has fulfilled its bounded-execution purpose.
- `archived` — slice closed without promotion. Used when the experiment is intentionally not graduated, or when learnings are absorbed elsewhere without dedicated canonization.

## Retrofit status vocabulary

Retrofit slices (`R-*`) follow a 4-state lifecycle with explicit transition triggers:

- `captured` — retrofit slice initialized from source plans; the slice file exists with intent and source batch identified, but the canonical destinations have not yet been mapped.
- `mapped` — canonical destinations identified and listed in the slice's mapping table. Items in the table may still be marked as gaps (`pending follow-up`); the slice is `mapped` once the table itself is filled, regardless of how many entries are still gaps.
- `integrated` — **all** canonical destinations identified during mapping are updated. Gaps recorded at the time of mapping are either closed in this slice or migrated to a follow-up `R-*`. A slice with residual unaddressed gaps is not `integrated`.
- `archived` — retrofit slice closed and retained for history. Used after `integrated`, or when the slice is intentionally abandoned (rare, requires recorded rationale in the slice file).

## Priority vocabulary

- `P0` — **critical blocker.** Total blocker; must be addressed as soon as possible. Reserved for situations where the platform, a release, or a customer-facing flow is materially broken or at unacceptable risk. Should remain rare.
- `P1` — **most pressing.** Strategic, structural, foundational; items with the highest dependency footprint on other items. Use for items whose absence delays multiple downstreams, or for foundational decisions that unblock the next phase.
- `P2` — **standard.** Functional-mode work — targeted improvements across the platform. Default tier for most backlog items in routine evolution.
- `P3` — **comfort.** Polish, optional improvements, non-essential. Acceptable to defer indefinitely; review periodically to confirm continued relevance.

## Identifier hygiene (avoid silent duplicates)

For **slug-based identifiers** (`F-*` feature catalog entries, and the slug portion of any artifact filename), check for similar keywords before creating a new entry:

- For `F-*`: scan `product-docs/global/features-and-phases.md` for adjacent topics or near-synonyms. Prefer extending an existing entry over creating a duplicate.
- For artifact slugs: a quick `rg` on the topic keywords in `product-docs/global/backlog/ideas/`, `product-docs/global/vision/`, and `product-docs/global/legacy-retrofit/` is usually enough to detect overlap.

Numeric IDs (`V-YYYY-NNNN`, `I-YYYY-NNNN`, `TB-YYYY-NNNN`, `R-YYYY-NNNN`) cannot collide by construction; this hygiene check applies only to the human-readable parts.
