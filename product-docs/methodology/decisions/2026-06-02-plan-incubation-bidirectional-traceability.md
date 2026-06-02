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

## Source signal

- The operator expected bidirectional traceability between a live working plan and its canonical
  ingestion outputs.
- The practical motivation is distinguishing materialized plans from plans that still need the
  ingestion lane.

## Working assumptions

- The current `plan-incubation-workflow.md` already contains the strongest rule: cross-link the
  canonical artifacts back to the working plan and record related artifact IDs in the plan when the
  plan remains useful.
- The current `plan-incubation` skill is less explicit than the workflow document and may let agents
  stop after one-way traceability from canonical docs to the source plan.
- A lightweight source-plan materialization section is enough for current sessions; a repository-wide
  plan ingestion index is not justified yet.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Keep only canonical-to-plan links | Minimal work; already sufficient for artifact provenance | Harder to discover unmaterialized plans by scanning the plan corpus |
| Add a materialization section to source plans | Simple, local, bidirectional, and easy to inspect | Depends on agents remembering the step |
| Create a central plan-ingestion index | Best global discovery | More ceremony and maintenance before the need is proven |

## Decision

For this session, add a `Canonical materialization` section to the source plan that lists the
generated `V-*`, `I-*`, and `TB-*` artifacts and states the plan's post-materialization role.

Record this decision as a repo-only methodology feedback note. A later methodology retrospective
should decide whether to update the `plan-incubation` skill and/or workflow wording so this
bidirectional step is mandatory whenever a working plan remains in the repo after ingestion.

## Consequences

- Source plans that have completed ingestion can advertise their materialized status directly.
- Scanning `.github/prompts/`, `.cursor/plans/`, or `plans/` can distinguish already materialized
  plans from pending incubation artifacts.
- Future agents should consider bidirectional traceability part of closeout for plan-incubation
  sessions, even though the skill text may still need tightening.

## Related documents

- [`../plan-incubation-workflow.md`](../plan-incubation-workflow.md)
- [`../../../.cursor/skills/plan-incubation/SKILL.md`](../../../.cursor/skills/plan-incubation/SKILL.md)
- [`../../../.github/prompts/plan-openApiSpecLifecycle.prompt.md`](../../../.github/prompts/plan-openApiSpecLifecycle.prompt.md)
- [`../../global/vision/V-2026-06-02-openapi-spec-lifecycle.md`](../../global/vision/V-2026-06-02-openapi-spec-lifecycle.md)
- [`../../global/backlog/ideas/I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation.md`](../../global/backlog/ideas/I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation.md)
- [`../../global/backlog/ideas/TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md`](../../global/backlog/ideas/TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md)
