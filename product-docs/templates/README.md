# Templates

This folder contains the reusable markdown templates used across the product documentation system.

Templates are **opinionated and minimal**. Every section must earn its place. When instantiating a template, remove sections that do not apply rather than leaving them empty, and prefer linking to other documents instead of duplicating content.

## Template Index

`README.md` stays first in the explorer navigation. After that, keep templates ordered by the most
common documentation journey rather than alphabetically.

### Ideation and delivery flow

- [vision-note.template.md](vision-note.template.md) — Capture directional product orientation notes. Instantiated as global vision notes.
- [backlog-idea.template.md](backlog-idea.template.md) — Capture one backlog idea with temporal metadata. Instantiated as global backlog ideas.
- [tracer-bullet-brief.template.md](tracer-bullet-brief.template.md) — Define a vertical slice from ideation to validation. Instantiated for tracer bullet planning.
- [test-plan-slice.template.md](test-plan-slice.template.md) — Define the minimum and optional test layers for a bounded slice. Instantiated for test planning in ideation-to-delivery flow.
- [legacy-plan-retrofit.template.md](legacy-plan-retrofit.template.md) — Extract and map legacy knowledge signal (plans, verbal, ad hoc) into canonical docs. Instantiated for weekly or opportunistic legacy retrofit sessions.
- [feature-brief.template.md](feature-brief.template.md) — Describe a feature: intent, scope, acceptance, dependencies. Instantiated in the global feature catalog or component-local features.

### Component and boundary design

- [component-design-brief.template.md](component-design-brief.template.md) — Summarize component-focused design for a bounded slice. Instantiated for component-focused analysis and design work.
- [functional-workflow.template.md](functional-workflow.template.md) — Describe a nominal workflow with exception paths. Instantiated for component functional flows.
- [decision-table.template.md](decision-table.template.md) — Capture branching rules in a verifiable table. Instantiated for workflows, services, and contract decisions.
- [mapping-matrix.template.md](mapping-matrix.template.md) — Describe mappings across a boundary. Instantiated for component API and boundary mappings.
- [error-and-exception.template.md](error-and-exception.template.md) — Define exception categories and error handling rules. Instantiated for component exception and error models.
- [persistence-and-lifecycle.template.md](persistence-and-lifecycle.template.md) — Describe persistence needs and entity lifecycles. Instantiated for component data models and global lifecycle work.
- [screens-and-wireflow.template.md](screens-and-wireflow.template.md) — Describe screens and navigation for a UI-bearing component. Instantiated for Admin UI and Mobile.

### Governance and cross-cutting records

- [spec-test-traceability.template.md](spec-test-traceability.template.md) — Link features to specs and tests. Instantiated in global and component traceability matrices.
- [architecture-decision.template.md](architecture-decision.template.md) — Record an architecture or design decision. Instantiated in global and component decision logs.
- [methodology-release-note.template.md](methodology-release-note.template.md) — Summarize a published methodology SemVer release. Instantiated in `methodology/release-notes/`.

### Global product framing

- [product-intent.template.md](product-intent.template.md) — Express product purpose, positioning, audience, and success criteria. Instantiated as global product intent.
- [roadmap.template.md](roadmap.template.md) — Sequenced phases and major product steps. Instantiated as the global roadmap.

## Conventions for Template Use

- **Copy, do not edit in place.** Instantiate the template by copying it into the target document and filling it in.
- **Do not break the section order** unless the target document clearly benefits; consistency across components matters more than local optimization.
- **Keep diagrams minimal.** Prefer clear Mermaid diagrams to decorative visuals.
- **Link, do not paste.** When referencing a decision, a mapping, or a spec, link to it.
- **Keep each document focused.** If a workflow becomes too large, split it into a main workflow and sub-workflows, each using the same template.

## Authoring Rules Across Templates

Every instantiated document should:

- Start with a short **intent** section: what this document answers and who it is for.
- Declare **assumptions and constraints** when they materially shape the content.
- Describe **boundaries** explicitly when crossing a boundary (module-to-module, module-to-API, API-to-external).
- Define **exception and error handling** where outcomes are not purely nominal.
- State **verifiable acceptance criteria** where the subject is a feature or workflow.
- Link to **specs and tests** that validate the behavior described.

## Reusing Outside Ezkey

The templates are product-agnostic. To reuse them:

1. Copy `product-docs/templates/` into the target monorepo.
2. Replace product-specific vocabulary in the [glossary](../glossary.md) with your product's terms.
3. Recreate the `global/` and `components/` skeletons using the templates.
