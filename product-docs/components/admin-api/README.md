# Component Pack — Admin API

## Purpose

This pack documents the Ezkey Admin API component: the administrative backend surface for integrations, enrollments, authentication management, admins, tenants, API keys, audit logs, and encryption key operations. It is the authoritative source of durable state for operator-facing workflows.

## Upstream Module

- **Source code.** [`../../../ezkey-admin-api/`](../../../ezkey-admin-api/).
- **Module README.** [`../../../ezkey-admin-api/README.md`](../../../ezkey-admin-api/README.md).
- **Module AGENTS.** [`../../../ezkey-admin-api/AGENTS.md`](../../../ezkey-admin-api/AGENTS.md).
- **Module configuration.** [`../../../ezkey-admin-api/CONFIGURATION.md`](../../../ezkey-admin-api/CONFIGURATION.md).

## Scope

In scope:

- Administrative endpoints and their semantics (at the level of intent; details remain in [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md)).
- Mappings between Admin API DTOs and the shared core domain.
- Authentication, authorization, and tenant scoping rules.
- RFC 9457 error surface owned by the Admin API.
- Encryption key lifecycle and audit chain endpoints at the intent level.
- Design decisions that shape Admin API behavior.

Out of scope:

- Shared business logic in `ezkey-core` (referenced when it shapes a boundary).
- Auth API endpoints used by the mobile app — see [`../mobile/README.md`](../mobile/README.md) and [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md).
- Wire-format cryptographic details — see [`../../../docs/CRYPTO.md`](../../../docs/CRYPTO.md).

## Global Feature Links

The Admin API contributes to the following global features (see [`../../global/features-and-phases.md`](../../global/features-and-phases.md)):

- `F-admin-api-core` — administrative surface.
- `F-admin-lifecycle` — admin identity lifecycle.
- `F-tenant-lifecycle` — tenant governance.
- `F-encryption-key-rotation` — key rotation and re-encryption.
- `F-audit-chain` — audit integrity and observability.
- `F-rfc9457-errors` — unified error contract.
- `F-rate-limiting` — abuse prevention.
- `F-api-key-lifecycle` — machine credential lifecycle.

## Pack Contents

| Document | Purpose |
|----------|---------|
| [`stack-and-architecture.md`](stack-and-architecture.md) | Stack, layering, and patterns. |
| [`functional-flows.md`](functional-flows.md) | Key backend workflows. |
| [`data-model-and-persistence.md`](data-model-and-persistence.md) | Entities, persistence, and lifecycle hooks. |
| [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md) | DTO ↔ domain ↔ database mappings. |
| [`exception-and-error-model.md`](exception-and-error-model.md) | Exception taxonomy and error contract. |
| [`design-decisions.md`](design-decisions.md) | Component-local decisions. |
| [`spec-test-traceability.md`](spec-test-traceability.md) | Feature ↔ spec ↔ test mapping. |

This component is not UI-bearing, so there is no `screens-and-wireflow.md`.

## Reading Order

1. [`stack-and-architecture.md`](stack-and-architecture.md)
2. [`data-model-and-persistence.md`](data-model-and-persistence.md)
3. [`functional-flows.md`](functional-flows.md)
4. [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md)
5. [`exception-and-error-model.md`](exception-and-error-model.md)
6. [`design-decisions.md`](design-decisions.md)
7. [`spec-test-traceability.md`](spec-test-traceability.md)
