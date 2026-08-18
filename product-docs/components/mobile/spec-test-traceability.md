# Mobile — Spec-Test Traceability

## Intent

This document links mobile features and workflows to the specifications that define their expected behavior and to the tests that verify that behavior. It is a **living document** that is updated in the same change set as any behavior, contract, or test change.

Promote items to the global matrix when they materially affect product-level coverage: [`../../global/spec-test-traceability.md`](../../global/spec-test-traceability.md).

## Traceability Matrix

| Feature | Spec | Acceptance criterion | Test suite | Status |
| --- | --- | --- | --- | --- |
| [`F-enrollment-bind-verify`](../../global/features-and-phases.md#f-enrollment-bind-verify) | [`functional-flows.md#w-mob-enrollment-wizard`](functional-flows.md#w-mob-enrollment-wizard), [`../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md) | Bind payload signature verified; algorithm mismatch fails closed; EC P-256 key generated on keystore. | `yarn test`; integration tests against Auth API in a clean-start stack. | `implemented` |
| [`F-auth-pending-respond`](../../global/features-and-phases.md#f-auth-pending-respond) | [`functional-flows.md#w-mob-pending-check`](functional-flows.md#w-mob-pending-check), [`functional-flows.md#w-mob-respond`](functional-flows.md#w-mob-respond), [`../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md) | User-initiated polling only; integration-signed outcome verified before success state; respond signs canonical payload. | `yarn test`; integration tests against Auth API. | `implemented` |
| [`F-mobile-reference-app`](../../global/features-and-phases.md#f-mobile-reference-app) | [`stack-and-architecture.md`](stack-and-architecture.md), [`data-model-and-persistence.md`](data-model-and-persistence.md), [`design-decisions.md#adr-mob-0004--android-app-level-sealed-secrets-for-long-lived-enrollment-values`](design-decisions.md#adr-mob-0004--android-app-level-sealed-secrets-for-long-lived-enrollment-values), [`../../../ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`](../../../ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md), [`../../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md`](../../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md) | Contract-first client generation flow works; no hand-edited DTOs; Android debug APK installable without Metro; `enrollmentProofToken` and `integrationPublicKey` stay out of AsyncStorage cleartext and are rehydrated through the secure secret delegate; local enrollment id / Keystore / seals are installation-scoped (`deriveLocalEnrollmentId`) so two Auth APIs allocating the same server `enrollment_id` cannot collide. | `yarn generate:api`, `yarn typecheck`, `yarn android:install:debug`, targeted Jest (`localEnrollmentIdentity`, enrollmentStorage collision, claim missing-key); operator Pixel multi-install smoke (2026-07-23). | `implemented` |
| [`F-rfc9457-errors`](../../global/features-and-phases.md#f-rfc9457-errors) | [`exception-and-error-model.md`](exception-and-error-model.md) | Problem Details parser accepts `type`/`title`/`detail`/`status` and rejects bodies that are not problems. Hook unification (surfacing `detail` on pending/detail) is deferred. | `yarn test` — `app/services/api/__tests__/authApiProblem.test.ts`. Heartbeat-degraded banner remains exploratory. | `in-progress` |

## Coverage Summary

- **Features in scope.** 4.
- **Implemented coverage.** 3.
- **In-progress coverage.** 1.

## Open Gaps

- `F-rfc9457-errors` — parser unit tests exist; hooks still extract axios fields ad hoc and do not yet share `parseAuthApiProblemDetail`. Heartbeat-degraded banner remains exploratory. Owner: mobile maintainers.
- `W-mob-pending-check` — Auth API may reuse a `deviceProofToken` until claim; the **client** generates a fresh token on every user-initiated pull (`claimPendingAttempt` / `generateProofToken`). Client contract is covered in `claimPendingAttempt.test.ts`. Do not add a client-side reuse cache to “match” the API property.
- **Real-device operational churn (`TB-2026-0002` F1)** — Maestro pilot (single pending/respond) validated on hardware; repeatable multi-iteration churn with JUnit/API truth and session artifact layout **not yet validated**. Owner: mobile maintainers. Next: Phase A green run per [`TSP-2026-06-26-mobile-real-device-churn-harness.md`](../../global/backlog/test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md); then add matrix row via `traceability-sync`. Design: [`../../../ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`](../../../ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md).
- Sealed-secret **JVM** coverage exists (`SealedSecretEnvelopeTest`). Keystore seal isolation is instrumented (`sealSecret_isIsolatedPerInstallationScope_MOB017`), not CI. Relational persist→rehydrate→claim isolation across two trust zones is tracked in [`MOBILE_TEST_STRATEGY.md`](../../../ezkey_mobile/docs/MOBILE_TEST_STRATEGY.md) (trust-zone crypto section).
- Installation-scoped Keystore **signing-alias** instrumentation — optional distinct-alias `androidTest` deferred at closeout of `TB-2026-07-20-mobile-installation-scoped-enrollment-identity` (unit + Pixel smoke accepted). Owner: mobile maintainers. Next: add only if alias-encoding regression is suspected.

## Update Cadence

- Update this matrix on any behavior, contract, or test change in the mobile app.
- Review at milestone boundaries or bounded delivery checkpoints.
- Promote items to the global matrix when they impact product-level acceptance.
- **2026-07-23 note:** installation-scoped enrollment identity closeout stayed on this component matrix under `F-mobile-reference-app` (no new global `F-*`; no global `spec-test-traceability.md` row).

## Related Documents

- [`../../global/spec-test-traceability.md`](../../global/spec-test-traceability.md).
- [`../../global/features-and-phases.md`](../../global/features-and-phases.md).
- Mobile canonical: [`../../../ezkey_mobile/docs/README.md`](../../../ezkey_mobile/docs/README.md).
