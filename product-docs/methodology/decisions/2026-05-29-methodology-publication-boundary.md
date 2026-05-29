---
public: true
---
# Methodology Publication Boundary

## Date

2026-05-29

## Context

`product-docs/` plays two roles inside Ezkey:

1. the official working documentation system for ongoing Ezkey delivery,
2. the source of a publishable methodology product exposed through the public methodology explorer
   and download pack.

Those roles overlap structurally, but they do not have the same publication boundary. The working
corpus contains instantiated artifacts for regular Ezkey development: roadmap state, feature
catalog, architecture decisions, backlog items, vision notes, retrofit slices, component packs,
policy analyses, and editor-local collaboration assets. These are valid and often essential in the
source project, but they are not automatically part of the publishable methodology product.

Broken public links to `product-docs/global/`, `product-docs/components/`, and `.cursor/` exposed
that ambiguity: the public methodology site was still carrying source-project dependencies in docs
that were supposed to stand on their own.

## Decision

For publication of the methodology as a product, distinguish three classes clearly.

### 1. Publishable method canon

Publish by default:

- `product-docs/methodology/`
- `product-docs/templates/`
- derived public `skills/`
- `glossary.md`
- rich views attached to public methodology roots
- methodology decisions explicitly marked `public: true`

These are the surfaces that explain the method itself.

### 2. Source-project hooks

Source-project companions may be mentioned as examples, provenance, or case-study hooks, but they
must not become required navigation dependencies for the public methodology product.

Examples:

- product design principles
- operator or policy guides
- component pack indexes
- source-repo skills or rules

In public docs, these may appear as plain path references or explanatory mentions when useful, but
not as assumed public links.

### 3. Non-publishable Ezkey working corpus

Do not publish by default as part of the methodology product:

- `product-docs/global/` instantiated delivery artifacts
- `product-docs/components/` implementation packs
- backlog, roadmap, vision, and retrofit execution records
- active product matrices, policies, and delivery inventories
- editor-local `.cursor/` assets

If one of these artifacts contains a reusable methodological lesson, the lesson must be promoted or
restated into method-level canon instead of widening the publication boundary ad hoc.

## Consequences

- Public methodology docs must remain understandable and navigable without access to Ezkey-only
  working documents.
- Source-project references in public docs should be classified deliberately as either method-level
  canon to publish, source-project hooks to mention without public dependency, or non-publishable
  working artifacts to exclude.
- The methodology site build should audit public links and fail when a published document escapes
  the agreed publication boundary.

## Related documents

- [`../README.md`](../README.md)
- [`../methodological-values.md`](../methodological-values.md)
- [`2026-05-28-methodology-integrity-and-representation-selection.md`](2026-05-28-methodology-integrity-and-representation-selection.md)
- [`2026-05-29-download-pack-first-for-methodology-distribution.md`](2026-05-29-download-pack-first-for-methodology-distribution.md)
