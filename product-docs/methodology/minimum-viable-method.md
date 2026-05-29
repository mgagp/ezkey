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

## Stop rules

Stop adding process when all of the following are already true:

- the direction is decided,
- the first cut is bounded,
- the validation is known,
- the remaining questions do not block the first cut.

At that point, the next step is implementation or validation, not more preparation.

## Prompt starters

- `Use the Minimum Viable Method for this topic. First classify the lane, then create only the next necessary artifact.`
- `Use the Minimum Viable Method. I want the smallest rigorous path, not the full methodology pass.`
- `Start with the Minimum Viable Method, then tell me whether this should stay lightweight or escalate into full Lane A.`
