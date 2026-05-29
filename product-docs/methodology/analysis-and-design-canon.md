# Analysis and Design Canon

## Purpose

This canon defines the minimal analytical and design artifacts expected for non-trivial product changes.

It intentionally keeps classic software analysis methods while remaining lightweight.

Use [`design-judgment-principles.md`](design-judgment-principles.md) alongside this canon when the
question is not which artifact to produce, but which design option to choose.

## Canonical artifact set

Use only what adds signal for the change:

- user stories,
- user-facing use cases,
- technical use cases,
- functional workflows (nominal and exception paths),
- sequence diagrams,
- conceptual data models or class diagrams,
- data-flow or context diagrams,
- decision tables,
- finite-state models,
- mapping matrices,
- error and exception models,
- lifecycle and persistence notes,
- screens and wireflows,
- component-level design decisions.

## Representation chooser

Choose the representation that makes the controlling reality easiest to inspect.

| If the problem is mainly about... | Prefer... | Why |
| ----- | ----- | ----- |
| Multi-actor interaction across steps or systems | functional workflow + sequence diagram | Makes ordering, handoffs, and exception return paths explicit. |
| Branching rules with multiple conditions | decision table | Makes rule coverage inspectable and testable without prose ambiguity. |
| Entity lifecycle, eligibility, activation, deactivation, or parent-chain effects | finite-state model | Makes states, guards, and transitions explicit. |
| Translation across API, DTO, domain, persistence, or UI state boundaries | mapping matrix | Makes field-level transformations and invariants explicit. |
| Domain structure, ownership, and important relationships | conceptual data model or class diagram | Makes the conceptual shape of the slice visible before implementation detail. |
| Cross-system transit, trust-sensitive material, or protocol movement | data-flow or context diagram | Makes data movement, trust boundaries, and sequencing constraints explicit. |
| UI navigation, screen orchestration, and operator journeys | screens and wireflow | Makes the navigational and task-flow surface explicit. |
| Failure taxonomy, propagation, and operator recovery posture | error and exception model | Makes failure handling explicit instead of scattering it through workflows. |

When two representations seem plausible, prefer the one that exposes the decision, invariant, or
boundary most directly. Add a second representation only when it removes real ambiguity.

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
- Does the change involve trust-sensitive transit or cross-system movement that needs a data-flow
  or context diagram?
- Does the change reshape a UI journey enough to need a wireflow?
- Does the resulting artifact describe durable component-local truth that should be integrated
  into `product-docs/components/<pack>/`?

If yes, create the artifact close to the impacted component pack and link it back to the global
feature or tracer bullet. If no, keep the analysis in the feature or tracer-bullet context.

## Practical rigor rule

Prefer clear and concise artifacts over exhaustive ceremony.  
If an artifact does not change understanding or decisions, omit it.
