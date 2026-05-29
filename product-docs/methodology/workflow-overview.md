# Workflow Overview

## Purpose

This document defines the default end-to-end workflow from idea capture to implemented feature.

It complements, but does not replace, the engineering and governance rules in the repository.

## Terminology note

- In this methodology, **phase** refers to workflow stages only.
- For product-level progression, use **milestone** wording.

## Fast routing

- Start in **Lane A** for ordinary ideation-to-delivery work.
- Start in **Lane B** when freeform planning should precede canonicalization.
- Start in **Lane C** when the source is historical and must be mined.
- Start in **Lane D** when the trigger is existing implemented behavior.
- Start in **Lane E** when the subject is the methodology itself.

If you need the fastest reliable entry before reading this full overview, start with
[`minimum-viable-method.md`](minimum-viable-method.md).

## Parallel lane: legacy retrofit

In addition to ideation-to-delivery, use a retrofit lane for historical plans and ad hoc implementation history:

1. Select a small source batch.
2. Extract decisions, invariants, patterns, and test signal.
3. Map into canonical docs.
4. Record a retrofit slice (`R-*`).
5. Close with residual gaps and next action.

See `legacy-retrofit-workflow.md`.

## Parallel lane: plan incubation

In addition to direct ideation capture, use a plan-incubation lane when the operator deliberately starts with a live working plan in agent Plan mode:

1. Create or evolve a current-session working plan.
2. Explore options and converge on the useful direction.
3. Materialize the durable signal into canonical artifacts.
4. Cross-link the working plan and canonical artifacts when useful.

See `plan-incubation-workflow.md`.

## Parallel lane: post-delivery evolution and corrective re-entry

After delivery, new work often starts from an observed limitation, requested improvement, or
apparent bug in existing behavior. Use a lightweight change-inception lane:

1. Classify the observation: pure local technical defect, execution-slice correction, backlog-scope
   change, or vision-level correction.
2. If it is a pure local technical defect, fix it directly with bounded validation.
3. If the issue reflects missing or changed intent, re-enter the canonical flow at the highest
   appropriate level (`TB-*`, `I-*`, or `V-*`).
4. Continue through the normal delivery workflow from that re-entry point.

This keeps routine debugging lightweight while giving requirement, rule, and direction changes a
traceable way back into the corpus.

## Parallel lane: methodology feedback and evolution

In addition to product work, use a methodology-feedback lane when the subject is the method itself:

1. Capture the observed friction, ambiguity, or proposed improvement from a live session.
2. Record the methodological decision and preserve short verbatim source signal when the exact
   phrasing matters.
3. Update the smallest methodology surfaces needed to make the new rule resumable.
4. Close with the evidence that will show whether the change helped in the next real use.

This lane exists for reflective improvement of the workflow itself. It is not a product backlog
lane and does not create `V-*`, `I-*`, `TB-*`, or `R-*` by default.

See `decisions/README.md` and `methodological-values.md`.

## Parallel lane: multi-branch and multi-worktree

When working across parallel Git branches or Git worktrees (solo or with a team):

1. Create artifact files (`I-*`, `V-*`, `TB-*`, `R-*`) freely on the branch — no coordination
   needed with other branches.
2. Use **date+slug identifiers** for all new artifacts — no counter lookup, no contention.
3. Defer index updates (`backlog/index.md`, `vision/product-orientation-notes.md`) to
   post-merge on `main`.
4. At merge: run an index reconciliation pass.

See `multi-branch-workflow.md`.

## End-to-end flow

1. **Capture**
   - Record a new idea in `../global/backlog/ideas/` using the backlog idea template.
   - Keep the first version short and expressive.
2. **Triage**
   - Clarify intent, user value, risk, and rough scope.
   - Assign status, priority, progression markers, and component tags.
3. **Challenge**
   - Run a structured questioning pass ("Grill Me" style).
   - Surface assumptions, exceptions, error paths, and non-goals.
4. **Promote**
   - If ready, promote the idea into a tracer bullet (`TB-*`) brief.
   - Define the first vertical slice and expected evidence.
   - If direction, first cut, validation criteria, and non-blocking open questions are already explicit, do **not** add another preparation layer by default; move toward implementation.
5. **Analyze and design**
   - Perform global analysis.
   - Perform component-specific analysis for each impacted boundary.
6. **Plan tests**
   - Select minimum and optional test layers (unit, functional, elective, operational, UI).
   - Keep selection risk-based and cost-aware.
7. **Gate**
   - Apply quality gates (contracts, tests, docs, traceability).
8. **Implement**
   - Execute the plan in code and tests.
9. **Close out**
   - Update feature and traceability documents.
   - Transition backlog and tracer-bullet status.

## Artifacts by stage

| Stage | Primary artifact |
| ----- | ---------------- |
| Capture | `I-*` backlog file |
| Triage / Challenge | Updated `I-*` + open questions |
| Promote | `TB-*` tracer bullet brief |
| Analyze / Design | Component design briefs and mappings |
| Test planning | Test plan slice |
| Gate / Implement | Code, tests, contract artifacts, docs updates |
| Close out | Status transitions + traceability updates |

## Exit criteria per stage

- **Capture done** when the idea has clear intent and tags.
- **Triage done** when scope and value are understandable.
- **Challenge done** when key risks and exceptions are explicit.
- **Promote done** when one vertical slice is testable.
- **Analyze/Design done** when boundaries and decision points are explicit.
- **Test planning done** when minimum test layers are explicitly selected.
- **Gate done** when mandatory quality checks pass.
- **Close out done** when status and traceability are updated.

## Preparation-to-action threshold

Default to **implementation** once all of the following are true:

1. the direction is decided;
2. the first cut is bounded;
3. validation criteria are known;
4. remaining open questions do not block that first cut.

This avoids two opposite failure modes:

- coding too early with unresolved boundary confusion;
- staying in perpetual preparation after the slice is already implementable.

## Post-delivery re-entry rule

Delivery is not a terminal state. Later observations can start new work in two different ways:

- **Local technical defect**: the intent is still correct and the issue is bounded to the current
  implementation. Fix it live, validate it, and close it without escalating to methodology unless
  the defect exposes a missing design rule or documentation gap.
- **Intent or scope change**: the observation reveals that the current product or implementation
  intent is incomplete, ambiguous, or wrong. Re-enter the flow at the highest level whose truth is
  changing:
  - `TB-*` when the implementation slice changes but the backlog idea and vision remain valid;
  - `I-*` when scope, validation rules, acceptance expectations, or operator-facing behavior change;
  - `V-*` when the product direction, positioning, or governing principle changes.

## Canonical update rule for post-delivery change

When post-delivery change updates the corpus:

- Prefer **amending the existing `V-*` or `I-*`** when the same artifact remains the right
   canonical home and the goal is to keep current truth unified.
- Use **`Superseded` / `Supersedes` lineage** when the change materially reframes the scope,
   splits one concern into several, or replaces the original formulation.
- Prefer a **new `TB-*`** for each new bounded implementation slice. Do not reopen a closed
   tracer bullet once its original execution and canonization job is complete.

## Capture variants

The standard Capture step (one idea per session) suffices for most cases. When the operator wants to capture multiple items in one session — typical when ideas accumulate, or when voice dictation is used — apply the [blitz intake pattern](blitz-intake-pattern.md). It preserves the verbatim source through an explicit archival step under `product-docs/global/backlog/blitz-archive/`, so the original wording is never lost when materialization happens.

For a single substantial topic, the operator may also start with a **live working plan** outside the canonical docs when early exploration benefits from freeform planning. In that case, treat the plan as a deliberate incubation artifact and later **materialize** its durable value into `V-*`, `I-*`, `TB-*`, and other canonical docs as appropriate. Do not treat this as retrofit unless the source is genuinely historical.
