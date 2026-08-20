# Features and Milestones

## Purpose

This document is the living catalog of Ezkey features. Every feature is tied to a milestone from the [roadmap](roadmap.md) and carries enough context to reach the relevant component documentation, specs, and tests.

The filename remains `features-and-phases.md` for link stability. Inside the corpus, use
**milestone** wording for product progression.

This is a **living document**: as features are added, re-scoped, or retired, the catalog is updated on the same branch as the behavior change. The [spec-test traceability matrix](spec-test-traceability.md) is updated in the same change set.

## Catalog Conventions

Feature identifiers follow the pattern `F-<short-kebab-name>` (for example `F-enrollment-bind-verify`). Identifiers are stable. Once a feature is removed, its identifier is not reused.

Status values follow the [glossary](../glossary.md):

- `planned`
- `in-progress`
- `implemented`
- `deprecated`
- `removed`

Backlog **progression markers** on `I-*` / `V-*` metadata use the same milestone identifiers
(`P0-foundations` … `P4-compliance-readiness`) — see
[`../methodology/nomenclature.md`](../methodology/nomenclature.md).

## Catalog

### Milestone `P0-foundations` — Core Protocol

#### `F-enrollment-bind-verify`

- **Intent.** Establish cryptographic enrollment between a user context, an integration, and a device through `bind` and `verify` steps that are cryptographically linked.
- **Status.** `implemented`.
- **Primary components.** [admin-api](../components/admin-api/README.md), [mobile](../components/mobile/README.md).
- **Key boundaries.** Auth API ↔ Mobile; Admin API ↔ Admin UI.
- **Key references.** [Enrollment flow](../components/admin-api/functional-flows.md#enrollment-bind-verify), [Mobile enrollment wizard](../components/mobile/functional-flows.md#enrollment-wizard).
- **Acceptance.** Signed bind payload, signed verify payload, device EC P-256 key material provisioned securely.

#### `F-auth-pending-respond`

- **Intent.** Provide a cryptographically linked authentication flow between `pending` and `respond` with one-time proof tokens.
- **Status.** `implemented`.
- **Primary components.** [admin-api](../components/admin-api/README.md), [mobile](../components/mobile/README.md).
- **Key references.** [Auth API endpoints](../../docs/ENDPOINT.md), [Authentication flow](../components/admin-api/functional-flows.md#authentication-pending-respond).

#### `F-mobile-reference-app`

- **Intent.** Deliver a React Native reference application that participates correctly in enrollment and authentication.
- **Status.** `implemented`.
- **Primary components.** [mobile](../components/mobile/README.md).

#### `F-admin-api-core`

- **Intent.** Expose administrative capabilities for integrations, enrollments, and authentication management.
- **Status.** `implemented`.
- **Primary components.** [admin-api](../components/admin-api/README.md).

### Milestone `P1-operability` — Day-To-Day Administration

#### `F-admin-ui-shell`

- **Intent.** Provide a role-aware Admin UI shell for Global Admins and Tenant Admins with passwordless login.
- **Status.** `implemented`.
- **Primary components.** [admin-ui](../components/admin-ui/README.md).

#### `F-admin-ui-workflows`

- **Intent.** Expose the primary operator workflows (integrations, enrollments, admins, API keys, tenants, audit logs) through the Admin UI.
- **Status.** `in-progress`.
- **Primary components.** [admin-ui](../components/admin-ui/README.md), [admin-api](../components/admin-api/README.md).

#### `F-admin-lifecycle`

- **Intent.** Manage admin identity lifecycle (pending activation, active, deactivated) separately from MFA credential lifecycle.
- **Status.** `implemented`.
- **Primary components.** [admin-api](../components/admin-api/README.md), [admin-ui](../components/admin-ui/README.md).

#### `F-tenant-lifecycle`

- **Intent.** Provide clean tenant activation and deactivation with system-tenant protection and no persistent cascade.
- **Status.** `implemented`.
- **Primary components.** [admin-api](../components/admin-api/README.md), [admin-ui](../components/admin-ui/README.md).

### Milestone `P2-hardening` — Security and Observability

#### `F-encryption-key-rotation`

- **Intent.** Allow safe encryption key rotation and background re-encryption without service disruption.
- **Status.** `in-progress`.
- **Primary components.** [admin-api](../components/admin-api/README.md).
- **Related decisions.** [ADR-0008](architecture-decisions.md#adr-0008-tink-keyset-sync-concurrent-read-path) — concurrent read path for Tink keyset sync (SEC-009); [ADR-0011](architecture-decisions.md#adr-0011-tink-native-database-keyset-envelope) — Tink-native database keyset envelope.

#### `F-audit-chain`

- **Intent.** Maintain an integrity-verified audit chain with lifecycle observability and heartbeat supervision.
- **Status.** `in-progress`.
- **Primary components.** [admin-api](../components/admin-api/README.md).

#### `F-rfc9457-errors`

- **Intent.** Provide uniform structured error contracts across Admin API and Auth API using RFC 9457 Problem Details.
- **Status.** `in-progress`.
- **Primary components.** [admin-api](../components/admin-api/README.md), [admin-ui](../components/admin-ui/README.md), [mobile](../components/mobile/README.md).

#### `F-rate-limiting`

- **Intent.** Protect login, pending, and respond endpoints against abuse with predictable behavior.
- **Status.** `implemented`.
- **Primary components.** [admin-api](../components/admin-api/README.md), [auth-api](../components/auth-api/README.md), [integration-api](../components/integration-api/README.md).
- **Policy reference.** [rate-limit-baseline-policy.md](rate-limit-baseline-policy.md) (cross-cutting families and inventory; `I-2026-0008`).

### Milestone `P3-distribution` — Integration and Distribution

#### `F-integration-api-maturity`

- **Intent.** Stabilize the Integration API surface for machine-to-machine workflows.
- **Status.** `planned`.

#### `F-api-key-lifecycle`

- **Intent.** Provide a full API key lifecycle (create, rotate, revoke, expire) with strong defaults.
- **Status.** `in-progress`.

#### `F-public-site`

- **Intent.** Publish and maintain the public-facing static site.
- **Status.** `in-progress`.

#### `F-sdk-and-cli-growth`

- **Intent.** Grow the SDK and CLI surfaces with consistent contracts.
- **Status.** `planned`.

### Milestone `P4-compliance-readiness` — Operational Discipline

Operational discipline as a product quality (SOC 2 as mapping vocabulary, not a certification
program). Current funded work remains the operable-release compass, not P4-as-certification. See
[`normative-posture.md`](normative-posture.md) and [`roadmap.md`](roadmap.md).

#### `F-provisioning-procedures`

- **Intent.** Document and operationalize admin provisioning and deprovisioning procedures with audit alignment.
- **Status.** `planned`.

#### `F-recovery-codes-lifecycle`

- **Intent.** Align recovery code lifecycle with admin lifecycle and durable operator-visible evidence.
- **Status.** `planned`.

#### `F-audit-artifacts`

- **Intent.** Produce stable audit artifacts for operator review and external export.
- **Status.** `planned`.

## Component Traceability Links

Component packs carry their own traceability matrices. The global matrix is a **summary view**; detailed test inventories live in the packs:

- Admin UI — [`../components/admin-ui/spec-test-traceability.md`](../components/admin-ui/spec-test-traceability.md).
- Admin API — [`../components/admin-api/spec-test-traceability.md`](../components/admin-api/spec-test-traceability.md).
- Mobile — [`../components/mobile/spec-test-traceability.md`](../components/mobile/spec-test-traceability.md).

## How to Add a Feature

See the authoritative workflow in [`../GOVERNANCE.md`](../GOVERNANCE.md). Summary:

1. Pick the right milestone in [`roadmap.md`](roadmap.md) (or propose a new milestone through an ADR).
2. Add an entry to the catalog above using the short format.
3. Create a feature brief using [`../templates/feature-brief.template.md`](../templates/feature-brief.template.md) inside the relevant component pack.
4. Update the component and [global traceability matrix](spec-test-traceability.md) in the same change set.
