# Component Pack — Mobile

## Purpose

This pack documents the Ezkey mobile application: the React Native reference app that participates as a secure device in the Ezkey cryptographic MFA protocol. It complements the strong existing mobile corpus under [`../../../ezkey_mobile/docs/`](../../../ezkey_mobile/docs/) by framing the mobile app inside the product-wide documentation system.

## Upstream Module

- **Source code.** [`../../../ezkey_mobile/`](../../../ezkey_mobile/).
- **Module README.** [`../../../ezkey_mobile/README.md`](../../../ezkey_mobile/README.md).
- **Module AGENTS.** [`../../../ezkey_mobile/AGENTS.md`](../../../ezkey_mobile/AGENTS.md).
- **Module documentation hub.** [`../../../ezkey_mobile/docs/README.md`](../../../ezkey_mobile/docs/README.md).
- **Module PRD.** [`../../../ezkey_mobile/PRD.md`](../../../ezkey_mobile/PRD.md).

## Scope

In scope:

- Mobile-level architecture, screens, and navigation.
- Mappings between the Auth API contract and local mobile models.
- Core mobile workflows: enrollment wizard, pending attempt retrieval, respond flow.
- Mobile-specific error handling.
- Design decisions local to the mobile app.
- Secure storage and native key handling expectations at the documentation level.

Out of scope:

- Shared cryptographic protocol details — see [`../../../docs/CRYPTO.md`](../../../docs/CRYPTO.md) and [`../../../docs/MOBILE_DEVELOPER_GUIDE.md`](../../../docs/MOBILE_DEVELOPER_GUIDE.md).
- Release engineering for Android/Play — see [`../../../ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md`](../../../ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md) and [`../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md).
- Auth API endpoint behavior — see [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md).

## Global Feature Links

The mobile app contributes to the following global features (see [`../../global/features-and-phases.md`](../../global/features-and-phases.md)):

- `F-mobile-reference-app` — reference React Native implementation.
- `F-enrollment-bind-verify` — mobile side of the enrollment flow.
- `F-auth-pending-respond` — mobile side of the authentication flow.
- `F-rfc9457-errors` — mobile-side error surface and user-facing messaging.

## Relationship With the Existing Mobile Corpus

The mobile app already carries a well-structured documentation set. This pack:

- **References** rather than replaces the existing corpus under `ezkey_mobile/docs/`.
- **Frames** the mobile app inside the product-wide system so intent, phases, features, mappings, and traceability are visible at the top level.
- **Seeds** the eight standard documents with minimal content that points to the authoritative mobile documents. Over time, details may migrate here if the mobile docs evolve toward deprecation.

## Pack Contents

| Document | Purpose |
|----------|---------|
| [`settings-copy-end-user-compass.md`](settings-copy-end-user-compass.md) | **Draft** end-user editorial compass for Settings / Paramètres informational copy (About, What's new, Coming soon, Security). Follow before changing FR/EN strings in `ezkey_mobile/app/i18n/resources.ts`. |
| [`stack-and-architecture.md`](stack-and-architecture.md) | Stack, module layout, runtime shape. |
| [`functional-flows.md`](functional-flows.md) | Enrollment, pending retrieval, respond flows. |
| [`data-model-and-persistence.md`](data-model-and-persistence.md) | Installation trust zone, enrollments, Keystore/StrongBox + seal keys (cornerstone diagrams). |
| [`screens-and-wireflow.md`](screens-and-wireflow.md) | Primary screens and navigation. |
| [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md) | Auth API DTO ↔ local mobile models. |
| [`exception-and-error-model.md`](exception-and-error-model.md) | Error taxonomy and user messaging. |
| [`design-decisions.md`](design-decisions.md) | Component-local decisions. |
| [`spec-test-traceability.md`](spec-test-traceability.md) | Feature ↔ spec ↔ test mapping. |

## Reading Order

1. [`stack-and-architecture.md`](stack-and-architecture.md)
2. [`data-model-and-persistence.md`](data-model-and-persistence.md) — installation trust zone cornerstone
3. [`screens-and-wireflow.md`](screens-and-wireflow.md)
4. [`functional-flows.md`](functional-flows.md)
5. [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md)
6. [`exception-and-error-model.md`](exception-and-error-model.md)
7. [`design-decisions.md`](design-decisions.md)
8. [`spec-test-traceability.md`](spec-test-traceability.md)

## Related observations (global)

- [`../../global/mobile-orphan-enrollment-after-admin-delete.md`](../../global/mobile-orphan-enrollment-after-admin-delete.md) — draft alpha constat: local orphan cards after admin delete + recreate under pull-only (no push wipe).
