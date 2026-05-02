# Component Pack — Admin UI

## Purpose

This pack documents the Ezkey Admin UI component: what it is, which product features it realizes, how it is structured, how it communicates with the Admin API, and how its behavior is verified.

The Admin UI is the **primary web surface for human administration** — the operator-facing interface for Global Admins and Tenant Admins. It is an integration surface, not the root of trust. Durable state, verification, and policy decisions remain on the Admin API.

## Upstream Module

- **Source code.** [`../../../ezkey-admin-ui/`](../../../ezkey-admin-ui/).
- **Module README.** [`../../../ezkey-admin-ui/README.md`](../../../ezkey-admin-ui/README.md).
- **Agent notes.** [`../../../ezkey-admin-ui/AGENTS.md`](../../../ezkey-admin-ui/AGENTS.md).
- **Module-local design note.** [`../../../ezkey-admin-ui/docs/LIST_DATA_LOADING_DESIGN.md`](../../../ezkey-admin-ui/docs/LIST_DATA_LOADING_DESIGN.md).

## Scope

In scope for this pack:

- UI-level architecture, navigation, and composition.
- Mappings between UI state and Admin API contracts.
- User-visible workflows (nominal and exception paths).
- Error handling at the UI layer (Problem Details translation, session handling, 401 behavior).
- Screens and wireflow for Global Admin and Tenant Admin.
- Design decisions local to the UI.

Out of scope:

- Admin API contract semantics — see [`../admin-api/README.md`](../admin-api/README.md).
- Shared cryptographic protocol details — see [`../../../docs/CRYPTO.md`](../../../docs/CRYPTO.md).
- Operational deployment at the edge — see [`../../../docs/admin-ui-security.md`](../../../docs/admin-ui-security.md) and [`../../../docs/cloudflare/admin-ui-pages.md`](../../../docs/cloudflare/admin-ui-pages.md).

## Global Feature Links

The Admin UI contributes to the following global features (see [`../../global/features-and-phases.md`](../../global/features-and-phases.md)):

- `F-admin-ui-shell` — role-aware shell with passwordless login.
- `F-admin-ui-workflows` — primary operator workflows.
- `F-admin-lifecycle` — admin identity lifecycle UI.
- `F-tenant-lifecycle` — tenant activation/deactivation UI.
- `F-rfc9457-errors` — RFC 9457 error translation and localization.
- `F-api-key-lifecycle` — API key lifecycle surfaces.

## Pack Contents

| Document | Purpose |
|----------|---------|
| [`stack-and-architecture.md`](stack-and-architecture.md) | Stack, libraries, architectural patterns. |
| [`functional-flows.md`](functional-flows.md) | UI-level workflows with nominal and exception paths. |
| [`data-model-and-persistence.md`](data-model-and-persistence.md) | UI state model, caching, and session storage. |
| [`screens-and-wireflow.md`](screens-and-wireflow.md) | Screen inventory and navigation. |
| [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md) | Admin API mappings, DTOs, error envelopes. |
| [`exception-and-error-model.md`](exception-and-error-model.md) | Error taxonomy and handling. |
| [`design-decisions.md`](design-decisions.md) | Component-local decisions. |
| [`spec-test-traceability.md`](spec-test-traceability.md) | Feature ↔ spec ↔ test mapping. |

## Reading Order

1. [`stack-and-architecture.md`](stack-and-architecture.md)
2. [`screens-and-wireflow.md`](screens-and-wireflow.md)
3. [`functional-flows.md`](functional-flows.md)
4. [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md)
5. [`exception-and-error-model.md`](exception-and-error-model.md)
6. [`data-model-and-persistence.md`](data-model-and-persistence.md)
7. [`design-decisions.md`](design-decisions.md)
8. [`spec-test-traceability.md`](spec-test-traceability.md)
