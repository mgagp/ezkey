---
name: plan-incubation
description: Incubates a live working plan, then materializes durable output into product-docs with mandatory bidirectional traceability. Use when starting from a current-session plan under .cursor/plans/, plans/, or .github/prompts/plan-*.prompt.md before creating V-*, I-*, or TB-* artifacts.
disable-model-invocation: true
---
# Plan Incubation

## Purpose

Use a live working plan as a deliberate brainstorming and convergence artifact before canonical
materialization — and **close the loop** with bidirectional traceability so intent survives in
`product-docs`.

## Boundary contract

- **Enter when:** the operator deliberately starts from a current-session working plan before canonical docs.
- **Exit when:** durable signal is materialized into `product-docs` **and** the bidirectional traceability gate passes.
- **Call next:** `vision-intake`, `backlog-triage`, or `tracer-bullet-promote` depending on the destination.
- **Not needed when:** the source is historical retrofit input or the request is already a direct implementation task.

## Inputs

- current-session working plan under `.cursor/plans/`, `plans/`, or `.github/prompts/plan-*.prompt.md`
- topic, options, or early recommendations
- optional links to related `V-*`, `I-*`, `TB-*`, or component docs

## Outputs

- clearer recommendation and open questions inside the working plan
- classification of the likely canonical destination (`V-*`, `I-*`, `TB-*`, principle candidate, or mix)
- materialized canonical artifacts under `product-docs/`
- **`Canonical materialization`** section on each retained working plan
- **`Incubation sources`** (or equivalent links) on each materialized corpus artifact

## Steps

1. Confirm that the source is a **current-session working plan**, not historical retrofit input.
2. Use the plan to compare options, structure the problem, and converge on the useful direction.
3. Identify what is durable enough to materialize into canonical docs.
4. Record the likely canonical destination:
   - `V-*` for direction or principle-level intent,
   - `I-*` for actionable backlog scope,
   - `TB-*` for a bounded executable slice.
5. **Materialize** — write canonical artifacts in English; do not copy the working plan verbatim.
6. **Bidirectional traceability gate (mandatory)** — do not mark incubation complete until all pass:
   - [ ] **Corpus → plan:** each new `product-docs` artifact lists all retained working plan paths.
   - [ ] **Plan → corpus:** each retained plan has a **`Canonical materialization`** block listing every generated artifact, date, lane `B`, and post-ingestion plan role.
   - [ ] **Cross-IDs:** `V-*` / `I-*` / `TB-*` IDs appear in both directions.
   - [ ] **Implementation** (if started): `#NNN` and branch name in `I-*` / `TB-*` and optionally in plan closeout.
7. Keep the working plan as a support artifact when it still contains valuable option space or execution notes.

## Rule

Treat the working plan as a legitimate incubation artifact.
Use the language of **materialization**, **canonicalization**, or **promotion into canonical docs**.
Do **not** frame current-session plan incubation as retrofit unless the source is genuinely historical.

## Related documents

- `product-docs/methodology/plan-incubation-workflow.md` (gate templates and definition of done)
- `product-docs/methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md`
- `product-docs/methodology/session-start-guide.md`
- `product-docs/methodology/workflow-overview.md`
- `product-docs/methodology/legacy-retrofit-workflow.md`
