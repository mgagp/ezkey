---
name: plan-incubation
description: Incubates a live working plan in agent Plan mode, then converges it toward canonical method artifacts. Use when the operator wants to start with a current-session working plan under .cursor/plans/ or plans/ before materializing durable output into V-*, I-*, or TB-*.
disable-model-invocation: true
---
# Plan Incubation

## Purpose

Use a live working plan as a deliberate brainstorming and convergence artifact before canonical materialization.

## Boundary contract

- **Enter when:** the operator deliberately starts from a current-session working plan before canonical docs.
- **Exit when:** durable signal is classified for `V-*`, `I-*`, `TB-*`, principle adoption, or a documented mix.
- **Call next:** `vision-intake`, `backlog-triage`, or `tracer-bullet-promote` depending on the destination.
- **Not needed when:** the source is historical retrofit input or the request is already a direct implementation task.

## Inputs

- current-session working plan under `.cursor/plans/` or `plans/`
- topic, options, or early recommendations
- optional links to related `V-*`, `I-*`, `TB-*`, or component docs

## Outputs

- clearer recommendation and open questions inside the working plan
- classification of the likely canonical destination (`V-*`, `I-*`, `TB-*`, principle candidate, or mix)
- explicit next materialization step

## Steps

1. Confirm that the source is a **current-session working plan**, not historical retrofit input.
2. Use the plan to compare options, structure the problem, and converge on the useful direction.
3. Identify what is durable enough to materialize into canonical docs.
4. Record the likely canonical destination:
   - `V-*` for direction or principle-level intent,
   - `I-*` for actionable backlog scope,
   - `TB-*` for a bounded executable slice.
5. Keep the working plan as a support artifact when it still contains valuable option space or execution notes.

## Rule

Treat the working plan as a legitimate incubation artifact.
Use the language of **materialization**, **canonicalization**, or **promotion into canonical docs**.
Do **not** frame current-session plan incubation as retrofit unless the source is genuinely historical or mixed with historical evidence.

## Related documents

- `product-docs/methodology/plan-incubation-workflow.md`
- `product-docs/methodology/session-start-guide.md`
- `product-docs/methodology/workflow-overview.md`
- `product-docs/methodology/legacy-retrofit-workflow.md`
