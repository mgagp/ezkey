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
- Approved direction affecting architecture should be reflected through ADRs.

## Recommended flow

1. Create a standalone `V-YYYY-MM-DD-<slug>.md` file in this directory.
2. Review and refine.
3. Promote actionable parts into backlog ideas (`I-*`).
4. Update `product-orientation-notes.md` post-merge on `main` (see
   [`../../methodology/multi-branch-workflow.md`](../../methodology/multi-branch-workflow.md)).
