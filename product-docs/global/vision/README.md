# Vision Notes

This folder captures product orientation ideas that complement the PRD and roadmap.

Use it for:

- direction hypotheses,
- positioning refinements,
- medium-term product intent evolution,
- strategic notes not yet promoted to concrete backlog items.

## File structure

Each vision note is a **standalone file**: `V-YYYY-MM-DD-<slug>.md` (new artifacts) or
`V-YYYY-NNNN-<slug>.md` (legacy format, pre-2026-05-22). The index is
[`product-orientation-notes.md`](product-orientation-notes.md) — a lightweight table, not an
accumulator. Do not embed vision note content directly in the index.

## Relationship to other documents

- Long-term stable intent belongs in `../product-intent.md`.
- Prioritized actionable work belongs in `../backlog/`.
- **Final product decisions** belong in durable canon — not permanently in a `V-*` file (see below).

## Vision notes vs decision recording

A `V-*` file is an **orientation** artifact: hypotheses, trade-offs, scope, and signals. It is **not** the canonical home for a final decision.

| Stage | Where decisions live | `V-*` status |
|-------|---------------------|--------------|
| Capture / early grill | `V-*` body and/or `backlog/grill-sessions/*` | `draft` or `under-review` |
| Decision tranched, not yet written to canon | Grill session (authoritative for session) + summary in `V-*` / `I-*` | `under-review` |
| Decision written to durable artifact(s) | See canon destinations below | move toward `promoted` |
| Orientation abandoned or reformulated | Successor `V-*` with `Supersedes:` / predecessor with `Superseded:` | `archived` |

**During grilling**, it is normal for tranched decisions to appear in the `V-*` intent block (see `V-2026-0003`) and in the grill session file. That is working state — not a substitute for canonization.

**Before `promoted`**, copy durable substance into at least one canonical destination:

- [`../architecture-decisions.md`](../architecture-decisions.md) — global ADRs
- [`../design-principles.md`](../design-principles.md) — product principles
- [`../features-and-phases.md`](../features-and-phases.md) — feature catalog
- Component `design-decisions.md`, operator guides, global policies (for example pagination or SQL limits)
- [`../../methodology/decisions/`](../../methodology/decisions/) — **only** for methodology/process choices, not product behavior

Do **not** wait for every adjacent `I-*` to ship before moving a `V-*` to `promoted` when the **orientation** has been fully absorbed into canon and backlog. Implementation backlog can remain open while the vision note is `promoted`.

## Recommended flow

1. Create a standalone `V-YYYY-MM-DD-<slug>.md` file in this directory.
2. Review and refine (`grill-me` when the orientation needs stress-testing).
3. Promote actionable parts into backlog ideas (`I-*`).
4. Canonize durable decisions into the destinations above; then set `V-*` to `promoted` or `archived` (with supersession lines when reformulated).
5. Update `product-orientation-notes.md` post-merge on `main` (see
   [`../../methodology/multi-branch-workflow.md`](../../methodology/multi-branch-workflow.md)).
