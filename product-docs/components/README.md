# Component Documentation Packs

This folder holds the per-entrypoint documentation packs for Ezkey. Each pack answers **how** a specific component implements the product intent defined in [`../global/product-intent.md`](../global/product-intent.md) and the architecture described in [`../global/architecture-overview.md`](../global/architecture-overview.md).

## Phase 1 Instantiated Packs

| Component | Pack | Upstream module |
|-----------|------|-----------------|
| Admin UI | [`admin-ui/`](admin-ui/README.md) | [`../../ezkey-admin-ui/`](../../ezkey-admin-ui/) |
| Admin API | [`admin-api/`](admin-api/README.md) | [`../../ezkey-admin-api/`](../../ezkey-admin-api/) |
| Mobile | [`mobile/`](mobile/README.md) | [`../../ezkey_mobile/`](../../ezkey_mobile/) |

Other components (Auth API, Integration API, Core, Core Security, SDK, CLI, Docker stack) will be instantiated in phase 2 using the same skeleton.

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

## How to Add a New Component Pack

1. Create a folder `components/<kebab-component-name>/`.
2. Copy the relevant [templates](../templates/README.md) into the folder.
3. Seed content from the component's existing README and docs.
4. Register the pack in this index and update [`../global/features-and-phases.md`](../global/features-and-phases.md) to reference it where appropriate.

Full change-workflow rules live in [`../GOVERNANCE.md`](../GOVERNANCE.md).
