# Component Packs as Conditional Methodology Support

## Date

2026-05-25

## Context

The methodology now has a clear ideation-to-delivery workflow, plus bounded parallel lanes for
plan incubation and legacy retrofit. The operator raised a separate question: whether the
`product-docs/components/` structure should become a first-class parallel methodological structure
for component-specific architecture, design constraints, decisions, and traceability.

The component packs already exist for Admin API, Admin UI, and Mobile. They contain real signal:
component-local ADRs, functional flows, boundary mappings, error models, and spec-test
traceability. They are not empty scaffolding. At the same time, treating component packs as a
second mandatory workflow lane for every product change would duplicate the global workflow and
create sync obligations even when a change is local or low-risk.

This decision applies the methodology values directly:

- **Proportional rigor:** component documentation should follow boundary risk, not habit.
- **Earned permanence:** permanent component packs must have update triggers and failure signals.
- **Bidirectional traceability:** component truth is useful when it links global intent to local
  design, tests, and decisions.
- **Reflective improvement with budget:** improve the existing structure before adding a new lane.

## Options considered

| Option | Advantages | Disadvantages |
| ------ | ---------- | ------------- |
| Promote component packs to a full parallel lane | Makes component thinking highly visible; strong local ownership | Adds ceremony; risks mandatory updates even when no component boundary changes |
| Drop the component-pack concept | Removes maintenance tax | Loses useful local ADRs, boundary maps, and spec-test traceability already proving value |
| Keep component packs as conditional support structure (chosen) | Preserves useful local truth while keeping the main workflow light | Requires clear update triggers so packs do not drift or become decorative |

## Decision

Keep `product-docs/components/` as a **first-class documentation projection**, but **not** as a
separate methodology lane.

Component packs are the canonical home for durable component-local truth:

- local responsibilities,
- inbound and outbound boundaries,
- component-specific workflows,
- DTO / API / persistence mappings,
- exception and error behavior,
- component-local ADRs,
- component-level spec-test traceability.

They are invoked conditionally by the main workflow, especially during Analyze and Design, when a
feature or tracer bullet changes component responsibilities, observable behavior, boundaries,
contracts, validation rules, error behavior, persistence semantics, or test evidence.

They are **not** updated merely because a change touched a file in a module. A local refactor,
copy change, styling tweak, internal cleanup, or implementation-only repair should not trigger
component-pack work unless it changes durable component truth.

## Operating rule

Use this decision test before updating or creating component-pack material:

> Does this change create or change durable component-local truth that a later human or agent must
> understand before safely modifying the component?

If yes, update the relevant component pack in the same change set.

If no, keep the fast path fast.

## Reflex in action

This is not an extra ceremony layer. It is a value-based reflex during feature work: use the
methodology values to notice whether the current feature needs a clearer component artifact, then
choose the smallest useful form. A decision table, finite-state model, mapping matrix, or lifecycle
note is appropriate only when it improves internal integrity and simplicity for the next person or
agent who must understand the component.

The desired behavior is introspective but lightweight: question the fit of the artifact while the
feature is being analyzed, create it when the triggers are real, and stop when the component truth
is already clear enough.

## Update triggers

Update the relevant component pack when at least one of these is true:

- A public or internal boundary contract changes.
- A workflow, exception path, or lifecycle rule changes.
- A mapping between API, DTO, domain, storage, or UI state changes.
- A component-local architectural decision is accepted, superseded, or rejected.
- Test evidence or traceability changes for a feature owned by that component.
- A global decision needs a component-local implementation consequence recorded.

Do not update the component pack for:

- purely internal implementation refactors with no boundary or behavior change,
- copy-only or presentation-only edits with no workflow implication,
- generated artifact refreshes when the canonical contract did not change,
- exploratory notes that have not earned permanence.

## Instantiation threshold for new component packs

Do not create a component pack for every module by default. Instantiate a new pack when the
component has at least one durable responsibility that needs local governance, such as:

- an external or cross-component boundary,
- recurring design decisions,
- component-owned workflows,
- component-specific error or lifecycle behavior,
- component-level traceability that would be noisy in the global matrix.

Until that threshold is met, reference the module README, existing docs, and global product docs.

## Consequences

- The current component packs remain valid and useful.
- The methodology rich view should not add a new "component lane". Component work remains part of
  Lane A Analyze and Design.
- `component-design-pack` remains a conditional skill, not a mandatory phase for every
  feature.
- `product-docs/components/README.md` should explicitly state this status and the update triggers.
- Future component packs should be created by evidence, not by inventory completion pressure.

## Verbatim source notes

The operator asked whether the component structure is an essential methodological support or a
potential distraction:

> est-ce que le concept meme de cette structure parallele en vaut la peine, ou est-ce que ca
> devient une idee sans fondement? Est-ce que ca ajoute de la complexite accidentelle qui n'a pas
> sa place, ou est-ce que ca devrait etre un element de premier plan?

The operator also framed the required posture:

> Soit on s'engage et on le fait proprement, soit on se retracte et on defait proprement.

The operator clarified that the goal is to use this questioning during real feature work, grounded
in the methodology values, to refine how the method operates in action:

> l'idee c'est utiliser ce questionnement en cours, baser ca sur les valeurs de méthodologie et
> faire cette introspection afin d'affiner comment notre metho opere dans l'action avec les bon
> reflexes, sans se surcharger de cerémonie mais en assurant l'intégrité interne (une des valeurs)
> et la simplicité

This decision chooses a third, more precise position: **commit to the component packs as durable
component truth, but retract the idea that they are an independent parallel workflow lane.**

## Related documents

- [`../methodological-values.md`](../methodological-values.md)
- [`../workflow-overview.md`](../workflow-overview.md)
- [`../analysis-and-design-canon.md`](../analysis-and-design-canon.md)
- [`../../components/README.md`](../../components/README.md)
- [`../../../.cursor/skills/component-design-pack/SKILL.md`](../../../.cursor/skills/component-design-pack/SKILL.md)
