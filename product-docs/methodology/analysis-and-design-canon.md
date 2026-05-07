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
- define test evidence.

Then link these component views into a global feature view.

## Practical rigor rule

Prefer clear and concise artifacts over exhaustive ceremony.  
If an artifact does not change understanding or decisions, omit it.
