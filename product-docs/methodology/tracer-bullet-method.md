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

## Completion criteria

A tracer bullet is complete when:

- the vertical slice works end to end,
- critical boundaries are validated,
- key error paths are validated,
- documents and traceability are synchronized.
