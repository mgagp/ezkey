---
public: false
---
# Plan incubation bidirectional traceability

## Date

2026-06-02

## Context

During materialization of `.github/prompts/plan-openApiSpecLifecycle.prompt.md`, the canonical
`V-*`, `I-*`, and `TB-*` artifacts linked back to the source plan. The follow-up question exposed
an important discoverability concern: if some working plans have been ingested into product-docs and
others have not, a reader should be able to inspect the plan corpus and quickly see which plans have
already been materialized.

A second incubation (`plan-openApiPresentationOrder`) showed the same gap when only one direction
of links was completed initially.

## Source signal

- The operator expected bidirectional traceability between a live working plan and its canonical
  ingestion outputs.
- The practical motivation is distinguishing materialized plans from plans that still need the
  ingestion lane.
- When revisiting a topic later (e.g. OpenAPI presentation order), humans and agents need a
  **conductive thread** from plan intent through vision to implementation — not orphaned corpus
  files or orphaned plans.

## Working assumptions

- Working plans may live under `.cursor/plans/`, `plans/`, or `.github/prompts/plan-*.prompt.md`.
- One incubation may retain **more than one** plan file; all retained copies must participate in
  bidirectional links.
- A lightweight **`Canonical materialization`** section on source plans is enough; a repository-wide
  plan ingestion index is not justified yet.
- The gate belongs in the workflow and the `plan-incubation` skill so agents and humans apply it
  at materialization time, not as an optional afterthought.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Keep only canonical-to-plan links | Minimal work; sufficient for one-way provenance | Hard to discover unmaterialized plans; plan corpus cannot advertise ingestion status |
| Add a materialization section to source plans | Simple, local, bidirectional, easy to inspect | Requires discipline at closeout |
| Create a central plan-ingestion index | Best global discovery | More ceremony before the need is proven |
| **Mandatory gate in workflow + skill** | Makes traceability a methodological value, not a memory item | Slightly more text at materialization time |

## Decision

Adopt **mandatory bidirectional traceability** at plan-incubation materialization:

1. Add an explicit **gate** and templates to [`../plan-incubation-workflow.md`](../plan-incubation-workflow.md).
2. Enforce the gate in [`.cursor/skills/plan-incubation/SKILL.md`](../../../.cursor/skills/plan-incubation/SKILL.md) as step 6 before exit.
3. Require a **`Canonical materialization`** section on each retained working plan listing all
   generated corpus artifacts, materialization date, lane `B`, and post-ingestion plan role.
4. Require **`Incubation sources`** (or equivalent back-links) on each materialized corpus artifact
   pointing to **all** retained plan paths.

Lane B is **not** definition-of-done complete until the gate passes.

## Consequences

- Source plans that completed ingestion advertise their materialized status directly.
- Scanning `.github/prompts/`, `.cursor/plans/`, or `plans/` distinguishes materialized plans from
  pending incubation artifacts.
- Corpus artifacts carry enough provenance to recover original intent when adjusting or extending work.
- `AGENTS.md` plan-incubation lane should reference the gate (see repo-wide agent notes).

## Applied examples

| Working plan(s) | Materialized corpus |
| --- | --- |
| `.github/prompts/plan-openApiSpecLifecycle.prompt.md` | `V-2026-06-02-openapi-spec-lifecycle`, `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`, `TB-2026-06-02-auth-api-cloudflare-schema-first-slice` |
| `.github/prompts/plan-openApiPresentationOrder.prompt.md`, `.cursor/plans/openapi_ordering_strategy_86087546.plan.md` | `V-2026-06-02-openapi-api-reference-presentation-order`, `I-2026-06-02-openapi-api-reference-presentation-order`, `TB-2026-06-02-openapi-presentation-order-phase2`, `openapi-presentation-order-design.md` |

## Related documents

- [`../plan-incubation-workflow.md`](../plan-incubation-workflow.md)
- [`../../../.cursor/skills/plan-incubation/SKILL.md`](../../../.cursor/skills/plan-incubation/SKILL.md)
- [`../../../AGENTS.md`](../../../AGENTS.md)
- [`../../../.github/prompts/plan-openApiSpecLifecycle.prompt.md`](../../../.github/prompts/plan-openApiSpecLifecycle.prompt.md)
- [`../../../.github/prompts/plan-openApiPresentationOrder.prompt.md`](../../../.github/prompts/plan-openApiPresentationOrder.prompt.md)
- [`../../global/openapi-presentation-order-design.md`](../../global/openapi-presentation-order-design.md)
