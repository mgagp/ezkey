# Session Start Guide and Lexicon

## Purpose

This is a practical cheat sheet for starting sessions with the right lane, vocabulary, and skill sequence.

If you need the shortest reliable entry before using this guide in full, start with
[`minimum-viable-method.md`](minimum-viable-method.md).

For day-to-day use, think in **one default delivery flow** plus a small set of explicit entry,
re-entry, and feedback lanes:

- **Lane A**: the default end-to-end ideation-to-delivery workflow
- **Lane B**: plan incubation
- **Lane C**: legacy knowledge retrofit
- **Lane D**: post-delivery evolution and corrective re-entry
- **Lane E**: methodology feedback and evolution

## Minimum viable start

When speed matters more than completeness, use this reduced entry:

1. classify the lane;
2. pick one anchor;
3. create only the next necessary artifact;
4. pick the cheapest meaningful validation;
5. close honestly.

See [`minimum-viable-method.md`](minimum-viable-method.md) for the compact version.

## Choose the lane

### Lane A — Default ideation-to-delivery workflow

Use when the work belongs to the normal product flow: raw idea capture, backlog shaping,
challenge, promotion to `TB-*`, design, test planning, implementation, and closeout.

- Output target: `V-*`, `I-*`, `TB-*`, design/test artifacts, implementation evidence
- Typical mode: **Plan**, then **Agent** when execution begins
- Typical skills depend on the current entry point:
  - early orientation: `vision-intake`, `backlog-triage`, `grill-me`
  - ready-for-execution slice: `tracer-bullet-promote`, `component-design-pack`,
    `test-strategy-planner`, `quality-gatekeeper`, `traceability-sync`, `closeout`

For **multi-item capture sessions** (typical when several ideas accumulate or voice dictation is used), apply the [blitz intake pattern](blitz-intake-pattern.md) as a Lane A variant. It preserves the verbatim source through an explicit archival step.

### Lane B — Plan incubation

Use when the operator deliberately wants to start with a live working plan before materializing
the durable result into canonical artifacts.

- Output target: working plan plus later `V-*`, `I-*`, or `TB-*` materialization
- Typical mode: **Plan**
- Typical skills: `plan-incubation`, then the Lane A skills required by the resulting entry point

Lane B is the deliberate freeform entry path into normal delivery work. Once the direction is
coherent, materialize into `V-*`, `I-*`, or `TB-*` and continue through Lane A.

### Lane C — Legacy knowledge retrofit

Use when the goal is extracting value from historical plans, verbal rationale, or ad hoc history.

- Output target: `R-*` + canonical doc updates
- Typical mode: **Plan -> Agent**
- Typical skills: `legacy-plan-miner`, `retrofit-curator`, `traceability-sync`

### Lane A delivery rule

`TB-*` does **not** imply that delivery must be decomposed into many tiny iterative slices. The
tracer bullet should be the **smallest meaningful end-to-end implementation cut**. Sometimes that
is a narrow pilot; sometimes it is the whole first cut if the work is already bounded and coherent
enough.

Action rule: when direction is decided, the first cut is bounded, validation criteria are known,
and remaining open questions do not block the cut, switch from preparation to implementation by
default.

### Lane D — Post-delivery evolution and corrective re-entry

Use when the goal starts from existing implemented behavior: enhancement requests, changed rules,
missing validation, apparent bugs, or operator feedback on something already built.

- Output target: either a direct bounded fix, or re-entry into `TB-*`, `I-*`, or `V-*`
- Typical mode: **Plan -> Agent**
- Typical skills: start with root-cause classification, then reuse the normal delivery skills only
  at the level that actually changed

Fast-path rule:

- If the problem is a pure local technical defect, fix it directly and validate it.
- If the problem exposes missing or changed intent, re-enter at `TB-*`, `I-*`, or `V-*`.

### Lane E — Methodology feedback and evolution

Use when the goal is improving the methodology itself: lane definitions, artifact rules, naming,
decision posture, handoff conventions, or other process mechanics.

- Output target: methodology decision record + targeted methodology doc updates
- Typical mode: **Plan**
- Typical skills: no dedicated skill required by default; capture the decision, update the smallest
  impacted methodology surfaces, then close with the expected validation signal

This lane is the reflective feedback loop of the method. It reuses `product-docs/methodology/decisions/`
instead of creating a new artifact family.

## Vocabulary quick reference

- `V-*`: vision direction note
- `I-*`: backlog idea
- `TB-*`: tracer bullet execution slice
- `R-*`: legacy knowledge retrofit slice
- `working plan`: live non-canonical planning artifact used before canonical materialization
- `methodology decision`: a decision about the workflow itself, recorded under `methodology/decisions/`
- `post-delivery re-entry`: a new change initiated from existing implemented behavior and routed
  back to `TB-*`, `I-*`, or `V-*` only when intent changed
- `captured/triaged/incubating/ready/active/done/parked/archived/dropped`: backlog lifecycle
- `captured/mapped/integrated/archived`: retrofit lifecycle

## Prompt starters

### Start a vision brainstorm

`Use vision-intake and grill-me for this product direction. I do not want implementation now.`

### Start with a live working plan first

`Use plan-incubation. Start in Plan mode with a live working plan for this topic, then materialize the result into V-* and/or I-* once the direction is coherent.`

### Start a delivery slice

`Use Lane A from a ready idea. Promote this to TB and prepare component design plus test plan.`

### Start a retrofit session

`Start a weekly retrofit lane for topic X. Use legacy-plan-miner and retrofit-curator. Sources are verbal plus these plan files: ...`

### Promote principle candidates

`From this retrofit slice, extract principle candidates and propose where to adopt them (design principles vs AGENTS/rules).`

### Start a methodology feedback session

`Start a methodology feedback lane from this live discussion. Record the decision, preserve the key verbatim source signal, and update only the methodology files that must change.`

### Close out completed work (hygiene check first)

`Classify this closeout as hygiene or program before creating I/TB/TSP artifacts. If hygiene, use commit/PR and targeted docs only unless I confirm escalation.`

### Start from an enhancement or apparent bug in existing code

`Start a post-delivery change inception. First classify whether this is a local technical defect or a corpus-level intent gap. If intent changed, re-enter at TB, I, or V; otherwise fix directly.`
