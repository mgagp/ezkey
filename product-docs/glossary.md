# Glossary and Naming Conventions

This document is the canonical source for the vocabulary used across the product documentation system. Consistent naming makes documents easier to search, diff, and compose.

## Document Types

| Term | Meaning |
| --- | --- |
| **Product intent** | High-level description of what the product is, why it exists, and its success criteria. Global scope. |
| **Roadmap** | Sequenced list of major product steps and themes. Global scope. |
| **Milestone** | A named product progression step in the roadmap with an explicit intent and scope. |
| **Phase** | A methodology workflow stage. Use for process progression, not for product roadmap progression. |
| **Feature** | A user-visible or integration-visible capability. Always tied to a milestone. |
| **Component** | A top-level entry point of the monorepo (e.g. Admin UI, Admin API, Mobile app). |
| **Workflow / Functional flow** | A described process involving one or more components, with nominal and exception paths. |
| **Mapping matrix** | A structured table describing how data or behavior crosses a boundary. |
| **Boundary** | Any interface where a translation happens: module to module, module to API, API to external system. |
| **Architecture decision** | A recorded decision explaining rationale, alternatives, and consequences. |
| **Lifecycle model** | Description of entities and their state transitions over time. |
| **Spec-test traceability** | Matrix that links a feature or workflow to its specification artifacts and verifying tests. |

## Document Naming Conventions

- Use **kebab-case** for filenames: `product-intent.md`, `spec-test-traceability.md`.
- Use **uppercase words** only for well-established historical docs (e.g. `ARCHITECTURE.md` in the legacy `docs/` hub).
- Templates use the suffix **`.template.md`**: `feature-brief.template.md`.
- Component packs sit under `components/<kebab-component-name>/`.

## Cross-Reference Conventions

- Use **relative markdown links** for cross-references inside this corpus.
- When linking to a module or a code file, use full workspace paths in markdown links: `[ezkey-admin-ui/README.md](../ezkey-admin-ui/README.md)`.
- When referencing a feature or milestone, link to its entry in [`global/features-and-phases.md`](global/features-and-phases.md).
- When referencing an architecture decision, link to the anchor in [`global/architecture-decisions.md`](global/architecture-decisions.md).

## Mermaid Conventions

- Node IDs use camelCase, not spaces.
- Wrap labels containing special characters in quotes.
- Avoid colors and class styling; rely on the default theme so dark mode renders correctly.

## Status Vocabulary

For feature, milestone, and traceability entries:

| Status | Meaning |
| --- | --- |
| `planned` | Scheduled but not started. |
| `in-progress` | Being actively designed or implemented. |
| `implemented` | Implemented and covered by tests. |
| `deprecated` | Still present but being retired. |
| `removed` | No longer part of the product. |

## Role Vocabulary

| Role | Meaning |
| --- | --- |
| **Global Admin** | Operator responsible for platform-wide, IT-level concerns (e.g. cryptographic keys, system configuration). |
| **Tenant Admin** | Operator responsible for day-to-day tenant administration (integrations, enrollments, API keys). |
| **End user** | A person being authenticated through an integration. |
| **Integrating application** | A backend system that uses the product to authenticate its users. |

## Protocol Vocabulary (Ezkey-specific)

| Term | Meaning |
| --- | --- |
| **Enrollment** | Cryptographic binding between a user context, an integration, and a mobile device. |
| **Authentication attempt** | A single MFA decision flow tied to an enrollment. |
| **Proof token** | One-time cryptographic material used to protect a flow step. |
| **Integration** | An application or backend protected by the product. |
| **Bind / Verify** | Enrollment lifecycle steps that are cryptographically linked. |
| **Pending / Respond** | Authentication lifecycle steps that are cryptographically linked. |

When reusing this structure in a non-Ezkey project, keep the document types and cross-reference conventions; replace the protocol vocabulary with your product's own.
