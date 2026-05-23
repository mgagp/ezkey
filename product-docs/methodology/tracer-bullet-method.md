# Tracer Bullet Method

## Purpose

A tracer bullet is a deliberate, end-to-end vertical slice that validates a feature path across boundaries before full rollout.

In Ezkey, tracer bullets are the default bridge from ideation to implementation confidence.

## When to use

Use a tracer bullet when:

- multiple components are impacted,
- contract boundaries are changing,
- workflow risk is significant,
- lifecycle and validation rules are non-trivial.

Use the **smallest meaningful end-to-end slice**, not the smallest imaginable task.

- If the whole bounded job is already small, coherent, and low-risk enough to validate in one pass, the tracer bullet may cover the **entire first implementation cut**.
- Do **not** force artificial micro-slices just to “be iterative” when that adds ceremony without improving confidence.
- Split into multiple tracer bullets only when the separation creates real value: lower risk, clearer evidence, cleaner rollback, or simpler cross-boundary reasoning.

## Action threshold

Once a topic has reached all four conditions below, the default next step is **implementation**, not more preparatory artifacts:

1. the **direction** is decided;
2. the **first cut** is bounded;
3. the **validation criteria** are known;
4. the **remaining open questions** do not block the first cut.

At that point:

- move from preparation to action;
- implement the smallest useful change;
- validate it;
- then adjust the documentation and follow-up scope from evidence.

Do **not** create another preparation layer unless it removes a concrete blocker or materially reduces cross-boundary risk.

## Tracer bullet structure

Each tracer bullet should define:

- target outcome,
- in-scope boundaries,
- out-of-scope boundaries,
- first executable slice,
- acceptance evidence,
- rollback or fallback posture.

## Boundary-first planning

For each impacted component, specify:

- boundary contract touched,
- data and mapping impact,
- error and exception impact,
- test and traceability impact.

This enables parallel component analysis while preserving global coherence.

## Recommended sequence

1. Define global flow intent.
2. Select smallest meaningful end-to-end slice.
3. Confirm boundary contracts and mapping assumptions.
4. Confirm exception behavior and validation rules.
5. Implement with targeted tests.
6. Update traceability.
7. Expand slice iteratively.

Iteration is optional, not mandatory. If step 2 yields a bounded one-shot first cut with acceptable risk and evidence cost, execute that cut directly and validate it as the tracer bullet.

## Completion criteria

A tracer bullet is complete when:

- the vertical slice works end to end,
- critical boundaries are validated,
- key error paths are validated,
- documents and traceability are synchronized.
