---
public: true
---
# Cursor Plan mode: ephemeral scaffold vs retained working plan

## Date

2026-07-11

## Context

Cursor **Plan mode** is a durable product habit: operators and agents use it to compare options and
converge before writing canonical `product-docs`. The plan-incubation lane already requires
**bidirectional traceability** when a **retained** working plan is ingested.

A recurring friction appeared when Plan mode wrote a plan **outside the git clone** (typical Cursor
user path such as `~/.cursor/plans/*.plan.md`), the durable signal was then fully captured in an
`I-*`, and agents either:

- left a **half-link** from the corpus to a path cold agents and other clones cannot see, or
- felt obliged to **copy the plan into the repository** even when it added no residual option space
  beyond the `I-*`.

A source-project API-key edit evaluation was the concrete trigger: the investigation plan was
thin, while the canonical backlog artifact already held the matrix and non-goals. Forcing repo
materialization of the plan would have been ceremony without signal.

## Source signal

- Plan mode will keep happening; it is valuable and should not be discouraged.
- When Plan mode output is **sufficient to materialize an unambiguous `V-*` / `I-*` / `TB-*`**,
  forcing the plan into the codebase is often waste.
- Methodology must help cold agents **choose** between ephemeral closeout and retained-plan
  incubation — not leave a contradictory half-state.

## Working assumptions

- Repo-hosted working plans remain under `.cursor/plans/` (in the clone), `plans/`, or
  `.github/prompts/plan-*.prompt.md`.
- Editor- or user-home Cursor plan files are **not** automatically methodology working plans.
- The bidirectional gate applies only to **retained** working plans.
- Lightest honest closeout wins (same spirit as hygiene vs program).

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Always copy external Plan mode files into the repo before materialization | Uniform gate; always bidirectional | Noise; empty ceremony when `I-*` is complete |
| Always treat Plan mode as out-of-band; never link | Simple | Loses legitimate retained incubation when option space matters |
| **Explicit fork at materialization** | Matches reality; teaches agents; forbids half-links | Requires a short decision step |

## Decision

At materialization time, the agent (or human) must **classify the Plan mode artifact**:

### A — Ephemeral scaffold (default when signal is fully in canon)

Use when:

- the durable intent, scope, non-goals, and next slice are unambiguous in `V-*` / `I-*` / `TB-*`;
- the plan adds no important residual option space, rejected alternatives, or execution notes.

Then:

1. Materialize the canonical artifact(s) only.
2. Do **not** copy the plan into the repository solely to satisfy the bidirectional gate.
3. Do **not** link corpus docs to paths outside the clone (no half-links).
4. Optionally note in the `I-*` (or equivalent) that Cursor Plan mode was used as **ephemeral
   scaffolding** and is not retained — without pointing at a non-repo path as if it were durable.
5. Do **not** claim plan-incubation lane **definition of done** (bidirectional gate) for that plan;
   this closeout is **direct backlog/vision capture aided by Plan mode**, not a completed retained
   working-plan incubation.

### B — Retained working plan (full plan-incubation closeout)

Use when:

- the plan still carries useful option space, rejected alternatives, or execution notes that the
  canon should not absorb verbatim.

Then:

1. Promote or copy the plan into a **repo-hosted** location (`.cursor/plans/`, `plans/`, or
   `.github/prompts/plan-*.prompt.md`).
2. Complete the **bidirectional traceability gate** (corpus ↔ plan) as already required.
3. Keep the plan's post-ingestion role explicit.

### Hard rule

Never leave corpus → plan provenance that cold agents cannot resolve from a fresh clone.

## Consequences

- Plan mode stays encouraged as a brainstorming tool.
- Agents stop over-materializing empty plans and stop half-linking outside the repo.
- Retained incubation remains available when the plan still earns its keep.
- Surfaces updated: [`../plan-incubation-workflow.md`](../plan-incubation-workflow.md),
  [`../session-start-guide.md`](../session-start-guide.md), and the operational
  `plan-incubation` skill.

## Applied example

| Session | Classification | Canon |
| --- | --- | --- |
| Source-project API-key editable attributes (2026-07-11) | **A — ephemeral** | Canonical backlog artifact only; no in-repo plan |

## Related documents

- [`../plan-incubation-workflow.md`](../plan-incubation-workflow.md)
- [`../minimum-viable-method.md`](../minimum-viable-method.md) (lightest honest closeout)
