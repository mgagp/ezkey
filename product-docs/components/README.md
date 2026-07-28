# Component Documentation Packs

This folder holds the per-entrypoint documentation packs for Ezkey. Each pack answers **how** a specific component implements the product intent defined in [`../global/product-intent.md`](../global/product-intent.md) and the architecture described in [`../global/architecture-overview.md`](../global/architecture-overview.md).

## Milestone 1 Instantiated Packs

| Component | Pack | Upstream module |
| --------- | ---- | --------------- |
| Admin UI | [`admin-ui/`](admin-ui/README.md) | [`../../ezkey-admin-ui/`](../../ezkey-admin-ui/) |
| Admin API | [`admin-api/`](admin-api/README.md) | [`../../ezkey-admin-api/`](../../ezkey-admin-api/) |
| Mobile | [`mobile/`](mobile/README.md) | [`../../ezkey_mobile/`](../../ezkey_mobile/) |

Other components (Auth API, Integration API, Core, Core Security, SDK, CLI, Docker stack) will be instantiated in milestone 2 using the same skeleton.

## Pack Skeleton

Every component pack has the same layout:

```text
<component>/
  README.md                      Scope, boundaries, links to global.
  stack-and-architecture.md      Stack, libraries, architectural patterns.
  functional-flows.md            Nominal and exception workflows.
  data-model-and-persistence.md  Internal data model and persistence rules.
  screens-and-wireflow.md        Screens and navigation (UI-bearing only).
  api-and-boundary-mappings.md   Mappings with external and internal boundaries.
  exception-and-error-model.md   Exception categories and error handling rules.
  design-decisions.md            Component-local design decisions.
  spec-test-traceability.md      Component-local traceability matrix.
```

Backend components omit `screens-and-wireflow.md`.

## How Packs Relate to Global

- The **global pack** holds product-wide truth: intent, roadmap, features, architecture, design principles, lifecycle, and global traceability.
- Each **component pack** holds truth specific to one entry point and links back to global entries instead of duplicating them.
- The global [`features-and-phases.md`](../global/features-and-phases.md) is the connective tissue: every component feature entry refers back to a feature in the global catalog.

## Methodology Status

Component packs are a **first-class documentation projection**, not a separate workflow lane.

They are invoked from the standard methodology when a feature, tracer bullet, or implementation
slice changes durable component-local truth. In practice, this means component packs are most often
used during Analyze and Design, then updated again during implementation or closeout if behavior,
contracts, tests, or decisions change.

Use this decision test before updating or creating component-pack material:

> Does this change create or change durable component-local truth that a later human or agent must
> understand before safely modifying the component?

If yes, update the relevant component pack in the same change set. If no, keep the fast path fast.

Update a component pack when at least one of these is true:

- a public or internal boundary contract changes;
- a workflow, exception path, or lifecycle rule changes;
- a mapping between API, DTO, domain, storage, or UI state changes;
- a component-local architectural decision is accepted, superseded, or rejected;
- test evidence or traceability changes for a feature owned by that component;
- a global decision needs a component-local implementation consequence recorded.

Do not update a component pack merely because a file changed in the corresponding module. Local
refactors, copy-only edits, generated artifact refreshes, and exploratory notes do not create a
component-pack obligation unless they change durable component truth.

See [`../methodology/decisions/2026-05-25-component-packs-methodological-status.md`](../methodology/decisions/2026-05-25-component-packs-methodological-status.md).

## How to Add a New Component Pack

Do not create a component pack for every module by inventory pressure alone. Instantiate a new pack
when the component has at least one durable responsibility that needs local governance: an external
or cross-component boundary, recurring design decisions, component-owned workflows,
component-specific error or lifecycle behavior, or component-level traceability that would be noisy
in the global matrix.

When that threshold is met:

1. Create a folder `components/<kebab-component-name>/`.
2. Copy the relevant [templates](../templates/README.md) into the folder.
3. Seed content from the component's existing README and docs.
4. Register the pack in this index and update [`../global/features-and-phases.md`](../global/features-and-phases.md) to reference it where appropriate.

**ADR address is stable even before full instantiation.** The `design-decisions.md` address for a
component is fixed by its name, not by whether the rest of the pack skeleton exists yet. When a
legacy-documentation triage session (or any other work) surfaces a genuine component-scoped design
decision and that component's pack has not reached the instantiation threshold above, create the
single `design-decisions.md` file for that component (using the
[architecture decision template](../templates/architecture-decision.template.md)) rather than
parking the decision in `global/` or leaving it only cross-linked from its original location. The
rest of the skeleton is filled in later, when the threshold is met. See
[`../methodology/decisions/2026-07-28-legacy-documentation-default-gravity-to-adr.md`](../methodology/decisions/2026-07-28-legacy-documentation-default-gravity-to-adr.md).

Full change-workflow rules live in [`../GOVERNANCE.md`](../GOVERNANCE.md).
