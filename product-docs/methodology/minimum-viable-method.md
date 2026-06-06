# Minimum Viable Method

## Purpose

This is the fastest reliable entry into the methodology.

Use it when you need the method to become usable quickly without first absorbing the whole pack.
It is intentionally small: enough structure to prevent drift, not enough structure to create a new
ceremony layer.

## When to use this

- first contact with the methodology,
- a tired human or a cold agent needs a quick restart point,
- the topic is real but not yet large enough to justify reading the whole pack first,
- you want the smallest method that still preserves integrity.

For the full system, continue with [`workflow-overview.md`](workflow-overview.md) and
[`session-start-guide.md`](session-start-guide.md).

## The minimum path

1. **Classify the entry point.**
   Use [`session-start-guide.md`](session-start-guide.md) to decide whether you are in normal
   delivery work, plan incubation, retrofit, post-delivery re-entry, or methodology feedback.
2. **Choose one concrete anchor.**
   Start from one idea, one working plan, one observed behavior, one historical source batch, or
   one methodology friction.
3. **Create only the next necessary artifact.**
   Do not materialize the whole corpus up front.
4. **Add one representation only when it removes ambiguity.**
   Use the smallest design artifact that makes the decision inspectable.
5. **Pick the cheapest meaningful validation.**
   A method slice is not complete until the evidence is explicit.
6. **Close honestly.**
   Update status, traceability, or the decision record only for what actually changed.

## Calibrate the method weight

Choose the smallest artifact set that protects the decision. Escalate only when uncertainty, risk,
or coordination needs justify it.

| Situation | Minimum method weight | Typical next artifact |
| --- | --- | --- |
| Small local change with clear intent | Fix and validate directly; update docs only if behavior or usage changed | None, or a targeted doc edit |
| Toolchain / dependency hygiene with a known recipe (e.g. applying a prior program's config fix) | Commit or PR + targeted module docs; challenge full methodological closeout | None; optional PR/issue labels |
| New idea with unclear value or scope | Capture the intent and stop before implementation pressure | `I-*` |
| Directional product question | Record the orientation before splitting into backlog work | `V-*` |
| Execution-ready but non-trivial slice | Define the smallest end-to-end proof and evidence | `TB-*` |
| Boundary, mapping, validation, or error ambiguity | Add only the design representation that exposes the controlling rule | Component design, mapping, decision table, or error model |
| Testing risk is not obvious | Select minimum and optional evidence explicitly | Test plan slice |
| Methodology friction | Record the process decision and update the smallest affected surface | Methodology decision |

If two rows seem applicable, start with the lighter one and escalate when a concrete ambiguity
remains.

## Default minimal routes

### A) New idea, not yet execution-ready

- create an `I-*` using [`../templates/backlog-idea.template.md`](../templates/backlog-idea.template.md),
- clarify scope, value, assumptions, and obvious risks,
- stop there unless challenge or promotion is already justified.

### B) Execution-ready slice

- create a `TB-*` using [`../templates/tracer-bullet-brief.template.md`](../templates/tracer-bullet-brief.template.md),
- add component design only where a boundary, mapping, validation rule, or error path is still
  unclear,
- choose the minimum test evidence and move toward implementation.

### C) Existing behavior that now needs change

- classify first: pure local defect or intent gap,
- if it is a pure local defect, fix and validate directly,
- if intent changed, re-enter through `TB-*`, `I-*`, or `V-*`.

### D) Historical source that still contains value

- create one bounded `R-*`,
- extract only durable signal,
- map it into canonical docs,
- annotate the source and close with explicit residual gaps.

### E) Methodology improvement itself

- record one methodology decision,
- update the smallest affected methodology files,
- define what next real use will prove the refinement helped.

## Representation minimum

If the change is non-trivial, choose only the representation that exposes the controlling reality:

- interaction problem: sequence-oriented functional workflow,
- branching rule problem: decision table,
- lifecycle problem: finite-state model,
- boundary translation problem: mapping matrix,
- UI journey problem: wireflow,
- error posture problem: error and exception model.

See [`analysis-and-design-canon.md`](analysis-and-design-canon.md) for the fuller chooser.

## Hygiene vs program closeout

Operators may invite a **methodological closeout** after work is done. That invitation is not, by
itself, a signal to create canonical backlog artifacts.

**Classify first.** State whether the slice is **hygiene** or **program**, then choose the lightest
honest closeout.

### Hygiene signals (prefer commit/PR + targeted docs)

- single bounded change set (often one PR or commit);
- applies a **known recipe** from an earlier program or decision;
- no new contract between generated output and application consumers;
- standard validation gates only (lint, test, build, routine device smoke);
- no new uncertainty that would block a cold agent from resuming.

**Closeout:** clear commit message, optional GitHub issue/PR, update `AGENTS.md` or module docs when
the pin/policy should outlive the PR. Do **not** add `I-*` / `TB-*` / `TSP-*` / `ML-*` unless the
operator explicitly confirms after your challenge.

### Program signals (canonical artifacts justified)

- multi-step ladder, unknown breakpoints, or repeated validation gates across PRs;
- codegen or API contract changes consumed by application code;
- new failure modes or config strategies that future work must rediscover;
- cross-module coordination or explicit Lane A / B / C / D / E program intent.

**Closeout:** use the appropriate artifact set (`I-*`, `TB-*`, `TSP-*`, `ML-*`, methodology
decision) proportional to the program — not reflexively all of them.

### Agent challenge (required on mismatch)

If the operator requests full methodology artifacts and you classify **hygiene**, say so explicitly,
propose the lighter path, and list what would justify escalation. See
[`decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md).

## Stop rules

Stop adding process when all of the following are already true:

- the direction is decided,
- the first cut is bounded,
- the validation is known,
- the remaining questions do not block the first cut.

At that point, the next step is implementation or validation, not more preparation.

## Aging rule

Old artifacts should not stay ambiguous by accident. When an artifact has not moved for a meaningful
period in the project's working rhythm, re-read it and choose one explicit outcome:

- keep it active and update `Updated at` or `Last reviewed at` where the template carries that
  field;
- move it to `parked` when it remains valid but is intentionally paused;
- move it to `archived` when it is retained for history but no longer active;
- move it to `dropped` when the idea or slice is explicitly rejected;
- add `Superseded` / `Supersedes` lineage when the substance was reformulated into another
  artifact.

Use project judgment for the interval. For a small active project, monthly or release-boundary
review is usually enough. Do not create a standing meeting or tracker ceremony unless repeated
stale artifacts prove the need.

## Prompt starters

- `Use the Minimum Viable Method for this topic. First classify the lane, then create only the next necessary artifact.`
- `Use the Minimum Viable Method. I want the smallest rigorous path, not the full methodology pass.`
- `Start with the Minimum Viable Method, then tell me whether this should stay lightweight or escalate into full Lane A.`
