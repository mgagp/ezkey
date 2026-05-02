# Mobile — Spec-Test Traceability

## Intent

This document links mobile features and workflows to the specifications that define their expected behavior and to the tests that verify that behavior. It is a **living document** that is updated in the same change set as any behavior, contract, or test change.

Promote items to the global matrix when they materially affect product-level coverage: [`../../global/spec-test-traceability.md`](../../global/spec-test-traceability.md).

## Traceability Matrix

| Feature | Spec | Acceptance criterion | Test suite | Status |
|---------|------|----------------------|------------|--------|
| [`F-enrollment-bind-verify`](../../global/features-and-phases.md#f-enrollment-bind-verify) | [`functional-flows.md#w-mob-enrollment-wizard`](functional-flows.md#w-mob-enrollment-wizard), [`../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md) | Bind payload signature verified; algorithm mismatch fails closed; EC P-256 key generated on keystore. | `yarn test`; integration tests against Auth API in a clean-start stack. | `implemented` |
| [`F-auth-pending-respond`](../../global/features-and-phases.md#f-auth-pending-respond) | [`functional-flows.md#w-mob-pending-check`](functional-flows.md#w-mob-pending-check), [`functional-flows.md#w-mob-respond`](functional-flows.md#w-mob-respond), [`../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md) | User-initiated polling only; integration-signed outcome verified before success state; respond signs canonical payload. | `yarn test`; integration tests against Auth API. | `implemented` |
| [`F-mobile-reference-app`](../../global/features-and-phases.md#f-mobile-reference-app) | [`stack-and-architecture.md`](stack-and-architecture.md), [`../../../ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`](../../../ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md) | Contract-first client generation flow works; no hand-edited DTOs; Android debug APK installable without Metro. | `yarn generate:api`, `yarn typecheck`, `yarn android:install:debug`. | `implemented` |
| [`F-rfc9457-errors`](../../global/features-and-phases.md#f-rfc9457-errors) | [`exception-and-error-model.md`](exception-and-error-model.md) | Problem types from Auth API are parsed and surfaced as non-deceptive states; heartbeat-degraded banner behaves correctly. | `yarn test` for parser; exploratory testing against clean-start stack. | `in-progress` |

## Coverage Summary

- **Features in scope.** 4.
- **Implemented coverage.** 3.
- **In-progress coverage.** 1.

## Open Gaps

- `F-rfc9457-errors` — no automated test exercises the full matrix of Auth API problem types on the mobile side. Owner: mobile maintainers. Next: add unit tests for the Problem Details parser covering representative types, plus an exploratory scenario for the heartbeat-degraded banner.
- `W-mob-pending-check` — the "same `deviceProofToken` can be reused until claim" behavior would benefit from an explicit test to prevent accidental regression. Owner: mobile maintainers. Next: add a targeted test or exploratory checklist entry.

## Update Cadence

- Update this matrix on any behavior, contract, or test change in the mobile app.
- Review at phase boundaries.
- Promote items to the global matrix when they impact product-level acceptance.

## Related Documents

- [`../../global/spec-test-traceability.md`](../../global/spec-test-traceability.md).
- [`../../global/features-and-phases.md`](../../global/features-and-phases.md).
- Mobile canonical: [`../../../ezkey_mobile/docs/README.md`](../../../ezkey_mobile/docs/README.md).
