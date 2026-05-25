# Analysis and Design Canon

## Purpose

This canon defines the minimal analytical and design artifacts expected for non-trivial product changes.

It intentionally keeps classic software analysis methods while remaining lightweight.

## Canonical artifact set

Use only what adds signal for the change:

- user stories,
- user-facing use cases,
- technical use cases,
- functional workflows (nominal and exception paths),
- sequence diagrams,
- decision tables,
- finite-state models,
- mapping matrices,
- lifecycle and persistence notes,
- component-level design decisions.

## Minimum set per non-trivial feature

1. Functional workflow with exception paths.
2. Boundary mapping matrix for each changed contract.
3. Decision table when branching logic is non-trivial.
4. Traceability row linking criteria to tests.

## Component-first decomposition

For each affected component:

- define local responsibilities,
- define inbound/outbound boundaries,
- define validation and error behavior,
- decide whether a decision table, finite-state model, mapping matrix, or lifecycle note is the
  clearest representation of the component-local behavior,
- define test evidence.

Then link these component views into a global feature view.

## Component projection checkpoint

During Analyze and Design, component artifacts are created only when they put the right value in
the right format and location. Ask:

- Does the component behavior branch enough to need a decision table?
- Does the component carry lifecycle or eligibility states that need a finite-state model?
- Does the change cross API, DTO, domain, storage, or UI state boundaries that need a mapping
  matrix?
- Does the resulting artifact describe durable component-local truth that should be integrated
  into `product-docs/components/<pack>/`?

If yes, create the artifact close to the impacted component pack and link it back to the global
feature or tracer bullet. If no, keep the analysis in the feature or tracer-bullet context.

## Practical rigor rule

Prefer clear and concise artifacts over exhaustive ceremony.  
If an artifact does not change understanding or decisions, omit it.
