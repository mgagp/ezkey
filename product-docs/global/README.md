# Global Documentation Pack

This folder holds the product-wide living truth for Ezkey. It is the canonical entry point for product intent, roadmap, features, architecture, design principles, lifecycle, and traceability.

## Contents

| Document | Role |
|----------|------|
| [`product-intent.md`](product-intent.md) | Product purpose, positioning, audience, requirements, success criteria. |
| [`roadmap.md`](roadmap.md) | Major product steps and phase sequencing. |
| [`features-and-phases.md`](features-and-phases.md) | Feature catalog linked to phases, status, and ownership. |
| [`architecture-overview.md`](architecture-overview.md) | Architectural view, components, boundaries, patterns. |
| [`architecture-decisions.md`](architecture-decisions.md) | Global decision log. |
| [`design-principles.md`](design-principles.md) | Cross-product design principles. |
| [`lifecycle-model.md`](lifecycle-model.md) | Entity relationships and global lifecycle rules. |
| [`spec-test-traceability.md`](spec-test-traceability.md) | Global spec-test traceability matrix. |
| [`vision/`](vision/README.md) | Product orientation notes and evolving direction. |
| [`backlog/`](backlog/README.md) | Markdown-native backlog with temporal status lifecycle. |
| [`legacy-retrofit/`](legacy-retrofit/README.md) | Structured retrofit bridge from historical plans to canonical docs. |

## Reading Order

1. `product-intent.md`
2. `roadmap.md`
3. `features-and-phases.md`
4. `architecture-overview.md`
5. `design-principles.md`
6. `lifecycle-model.md`
7. `spec-test-traceability.md`
8. Relevant [component pack](../components/README.md).
9. `vision/README.md` and `backlog/README.md` when planning future work.

## Boundary With Component Packs

The global pack answers **what** and **why** at the product level. Component packs answer **how** each entry point implements that intent. Component-local architecture, flows, data model, mappings, and decisions live in the component packs; cross-reference them from here instead of duplicating the content.
