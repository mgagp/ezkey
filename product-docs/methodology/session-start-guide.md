# Session Start Guide and Lexicon

## Purpose

This is a practical cheat sheet for starting sessions with the right lane, vocabulary, and skill sequence.

## Choose the lane

### Lane A — Vision brainstorming (no immediate execution)

Use when the goal is orientation, options, or product direction.

- Output target: `V-*`, optionally `I-*` in `captured` or `incubating`
- Typical mode: **Plan**
- Typical skills: `ezkey-vision-intake`, `ezkey-backlog-triage`, `ezkey-grill-me`

For **multi-item capture sessions** (typical when several ideas accumulate or voice dictation is used), apply the [blitz intake pattern](blitz-intake-pattern.md) as a Lane A variant. It preserves the verbatim source through an explicit archival step.

For a **single substantial topic** where the operator prefers freeform planning first, use the [plan incubation workflow](plan-incubation-workflow.md) as another Lane A variant. This explicitly supports starting in agent Plan mode with a live working plan, then materializing the durable value into canonical docs without framing the session as retrofit.

- Typical skills for this variant: `ezkey-plan-incubation`, then `ezkey-vision-intake` and/or `ezkey-backlog-triage`

### Lane B — Delivery execution

Use when the goal is scoped implementation.

- Output target: `TB-*` + component design + test plan + implementation
- Typical mode: **Plan -> Agent**
- Typical skills: `ezkey-tracer-bullet-promote`, `ezkey-component-design-pack`, `ezkey-test-strategy-planner`, `ezkey-quality-gatekeeper`, `ezkey-traceability-sync`, `ezkey-closeout`

`TB-*` does **not** imply that delivery must be decomposed into many tiny iterative slices. The tracer bullet should be the **smallest meaningful end-to-end implementation cut**. Sometimes that is a narrow pilot; sometimes it is the whole first cut if the work is already bounded and coherent enough.

Action rule: when direction is decided, the first cut is bounded, validation criteria are known, and remaining open questions do not block the cut, switch from preparation to implementation by default.

### Lane C — Legacy knowledge retrofit

Use when the goal is extracting value from historical plans, verbal rationale, or ad hoc history.

- Output target: `R-*` + canonical doc updates
- Typical mode: **Plan -> Agent**
- Typical skills: `ezkey-legacy-plan-miner`, `ezkey-retrofit-curator`, `ezkey-traceability-sync`

## Vocabulary quick reference

- `V-*`: vision direction note
- `I-*`: backlog idea
- `TB-*`: tracer bullet execution slice
- `R-*`: legacy knowledge retrofit slice
- `working plan`: live non-canonical planning artifact used before canonical materialization
- `captured/triaged/incubating/ready/active/done/parked/archived/dropped`: backlog lifecycle
- `captured/mapped/integrated/archived`: retrofit lifecycle

## Prompt starters

### Start a vision brainstorm

`Use ezkey-vision-intake and ezkey-grill-me for this product direction. I do not want implementation now.`

### Start with a live working plan first

`Use ezkey-plan-incubation. Start in Plan mode with a live working plan for this topic, then materialize the result into V-* and/or I-* once the direction is coherent.`

### Start a delivery slice

`Promote this ready idea to TB and prepare component design plus test plan.`

### Start a retrofit session

`Start a weekly retrofit lane for topic X. Use ezkey-legacy-plan-miner and ezkey-retrofit-curator. Sources are verbal plus these plan files: ...`

### Promote principle candidates

`From this retrofit slice, extract principle candidates and propose where to adopt them (design principles vs AGENTS/rules).`
