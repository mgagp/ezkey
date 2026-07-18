# Design Judgment Principles

## Purpose

This document captures design principles that help break ties during analysis, design, and review.

The methodology already defines:

- which artifacts best represent a problem in [`analysis-and-design-canon.md`](analysis-and-design-canon.md),
- which workflow values govern the method in [`methodological-values.md`](methodological-values.md).

This companion answers a different question:

**when several plausible design options exist, what judgment principles should guide the choice?**

These principles are intentionally method-level and publishable. They are not a replacement for any
source-project design canon. In Ezkey, the product-specific companion still lives in
`product-docs/global/design-principles.md`.

## How to use this document

Use these principles when a feature, API, workflow, or component change has multiple credible
solutions and the methodology needs a disciplined way to choose among them.

In practice:

1. Restate the design problem in one line.
2. Identify which principles are most relevant.
3. Explain the trade-off explicitly when two principles pull in different directions.
4. Record the chosen cut line in the most appropriate artifact: design brief, tracer bullet,
   decision note, ADR, or component-local pack.

## Core principles

### 1. Simplicity and pragmatism

Prefer the simplest solution that meets the need. Target roughly 80% of the value with 20% of the
complexity. Add accidental complexity only when it buys something observable.

### 2. Essential over accidental complexity

Essential complexity is acceptable when the problem genuinely requires it. Accidental complexity
must be minimized. If an abstraction, indirection, or extra process layer does not materially
improve the outcome, it does not belong.

### 3. Explicit boundaries and verification posture

Every important boundary between systems, actors, or components should be named and documented.
The design should answer:

- who is trusted for what,
- what is verified,
- what is only asserted,
- what happens on failure.

Use the lightest representation that makes the boundary inspectable: workflow, mapping matrix,
data-flow diagram, error model, or ADR.

### 4. Primary-user-centered design

Design from the perspective of the people who must integrate, operate, debug, or maintain the
system. Interfaces, documentation, and error behavior should optimize for the primary users of the
system rather than for abstract narratives.

In many systems, those primary users are developers and operators. Name the real audience before
optimizing the surface.

### 5. Stable external contracts

External contracts should be disciplined, inspectable, and stable. Favor widely understood
standards and predictable evolution. When a contract changes, the change should be deliberate,
traceable, and reflected in generated artifacts, examples, and tests.

### 6. Stay within the chosen stack

Prefer the idioms and tools that developers expect for the chosen stack over unnecessary novelty.
New dependencies, new frameworks, and new abstraction layers should carry explicit justification.

This principle reduces long-term maintenance surprise.

### 7. Spec-first, evidence-backed implementation

Specify the intended behavior before implementation detail hardens. Use workflows, mappings,
decision tables, lifecycle notes, error models, and test traceability to make the intended behavior
 inspectable before coding closes off options.

The spec does not need to be heavy. It needs to be sufficient to make the controlling decisions and
expected evidence explicit.

### 8. One canonical place per concept

Every important concept should have one canonical home in the documentation. Elsewhere, reference
that home rather than silently duplicating it.

This keeps the documentation truthful over time and reduces divergence between explanation,
specification, and implementation.

### 9. Documentation-backed transparency

Meaningful design decisions, mappings, flows, and trade-offs should be discoverable without forcing
the next reader to reconstruct the entire reasoning from code alone.

The goal is not maximal verbosity. The goal is that the next human or agent can inspect the design
intent with reasonable effort.

### 10. Beautiful problems

When judging pragmatism and complexity, ask whether a future problem would appear for the right
reasons: real adoption, real scale, real workflow friction, real coupling pressure.

- If yes, let it become a visible problem before adding sophistication.
- If no, avoid speculative complexity now.

This principle governs scale and refinement questions, not security holes or undefined behavior.

### 11. Simple cases stay simple

Well-understood, bounded, low-risk topics should keep a genuinely short path from idea to action.
Iteration exists to manage uncertainty, not to turn every topic into a staged ceremony.

This applies both to product design and to the methodology itself.

### 12. Name fail-open vs fail-closed at critical boundaries

When a control or side effect can fail, make the **failure posture** explicit:

- **Fail-closed:** stop or refuse the primary path when the control fails — prefer when continuing
  would silently weaken a claimed security, integrity, or trust guarantee.
- **Fail-open:** continue the primary path when the control fails — prefer when availability matters
  more, and only if the failure stays **observable** to operators.

Ask: *if this step fails, does the important path continue or stop — and how do we know?* Use the
vocabulary as a design compass at boundaries where availability and integrity (or honesty of
claims) are in tension. Do not build a ceremony or matrix for every call site.

This principle deepens **#3** (explicit boundaries and verification posture: *what happens on
failure*). Source-project companion with product examples:
`product-docs/global/design-principles.md` (principle 17).

## Relationship to existing methodology canon

- [`analysis-and-design-canon.md`](analysis-and-design-canon.md) says **which artifacts** clarify a
  problem.
- [`methodological-values.md`](methodological-values.md) says **how the workflow** preserves
  integrity.
- This document says **how to judge design trade-offs** when multiple plausible solutions remain.

Use the three documents together, not as substitutes for one another.

## Publication and source-project boundary

This document is a promoted methodology companion, not a relocation of the Ezkey source-project
canon.

If a source-project principle is broadly reusable, promote or restate it here. If it depends on
product-local semantics, domain constraints, or local ADRs, keep it in the source-project canon and
mention it only as a hook or case-study reference.

## Related documents

- [`analysis-and-design-canon.md`](analysis-and-design-canon.md)
- [`methodological-values.md`](methodological-values.md)
- [`README.md`](README.md)
- [`decisions/2026-05-29-promote-generic-design-principles-into-methodology.md`](decisions/2026-05-29-promote-generic-design-principles-into-methodology.md)
- [`decisions/2026-07-18-fail-open-fail-closed-design-compass.md`](decisions/2026-07-18-fail-open-fail-closed-design-compass.md)
