---
name: plan-incubation
description: Incubates a live working plan, then materializes durable output into product-docs. Classify ephemeral vs retained; apply bidirectional traceability only for retained plans. Use when starting from a current-session plan under .cursor/plans/, plans/, or .github/prompts/plan-*.prompt.md before creating V-*, I-*, or TB-* artifacts.
disable-model-invocation: true
---
# Plan Incubation

## Purpose

Use a live working plan as a deliberate brainstorming and convergence artifact before canonical
materialization — and **close the loop** correctly: full bidirectional traceability when the plan
is **retained**, or honest ephemeral closeout when the durable signal already lives in canon.

## Boundary contract

- **Enter when:** the operator deliberately starts from a current-session working plan before canonical docs.
- **Exit when:** durable signal is materialized into `product-docs`, **and** either:
  - **retained:** the bidirectional traceability gate passes, or
  - **ephemeral:** canon alone is complete with no half-links outside the clone.
- **Call next:** `vision-intake`, `backlog-triage`, or `tracer-bullet-promote` depending on the destination.
- **Not needed when:** the source is historical retrofit input or the request is already a direct implementation task.

## Inputs

- current-session working plan under `.cursor/plans/`, `plans/`, or `.github/prompts/plan-*.prompt.md`
  (repo-hosted), **or** Cursor Plan mode scaffolding outside the clone that may stay ephemeral
- topic, options, or early recommendations
- optional links to related `V-*`, `I-*`, `TB-*`, or component docs

## Outputs

- clearer recommendation and open questions inside the working plan (when retained)
- classification of the likely canonical destination (`V-*`, `I-*`, `TB-*`, principle candidate, or mix)
- **retention classification:** ephemeral scaffold (A) vs retained working plan (B)
- materialized canonical artifacts under `product-docs/`
- for **retained** only: **`Canonical materialization`** on each plan + **`Incubation sources`** on corpus artifacts

## Steps

1. Confirm that the source is a **current-session working plan**, not historical retrofit input.
2. Use the plan to compare options, structure the problem, and converge on the useful direction.
3. Identify what is durable enough to materialize into canonical docs.
4. Record the likely canonical destination:
   - `V-*` for direction or principle-level intent,
   - `I-*` for actionable backlog scope,
   - `TB-*` for a bounded executable slice.
5. **Materialize** — write canonical artifacts in English; do not copy the working plan verbatim.
6. **Classify retention** (mandatory — see decision `2026-07-11-cursor-plan-ephemeral-vs-retained-working-plan`):
   - **A — Ephemeral:** durable signal is fully unambiguous in canon; plan adds no important residual option space → do **not** copy into the repo for ceremony; do **not** link paths outside the clone; optional one-line ephemeral note; do **not** claim the bidirectional gate.
   - **B — Retained:** plan still holds useful option space → promote/copy into a repo-hosted location, then continue to step 7.
7. **Bidirectional traceability gate (mandatory for retained only)** — do not mark retained incubation complete until all pass:
   - [ ] **Corpus → plan:** each new `product-docs` artifact lists all retained working plan paths.
   - [ ] **Plan → corpus:** each retained plan has a **`Canonical materialization`** block listing every generated artifact, date, lane `B`, and post-ingestion plan role.
   - [ ] **Cross-IDs:** `V-*` / `I-*` / `TB-*` IDs appear in both directions.
   - [ ] **Implementation** (if started): `#NNN` and branch name in `I-*` / `TB-*` and optionally in plan closeout.
8. Keep the working plan as a support artifact when it still contains valuable option space or execution notes.

## Rule

Treat the working plan as a legitimate incubation artifact when retained.
Use the language of **materialization**, **canonicalization**, or **promotion into canonical docs**.
Do **not** frame current-session plan incubation as retrofit unless the source is genuinely historical.
Do **not** invent half-links to editor- or user-home plan paths.

## Related documents

- `product-docs/methodology/plan-incubation-workflow.md` (gate templates and definition of done)
- `product-docs/methodology/decisions/2026-07-11-cursor-plan-ephemeral-vs-retained-working-plan.md`
- `product-docs/methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md`
- `product-docs/methodology/session-start-guide.md`
- `product-docs/methodology/workflow-overview.md`
- `product-docs/methodology/legacy-retrofit-workflow.md`
