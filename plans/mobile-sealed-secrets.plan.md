---
name: mobile-sealed-secrets-plan
overview: Closed implementation note for the Ezkey Mobile Android sealed-secrets hardening. The active source of truth now lives in code, mobile docs, and ADR-MOB-0004.
todos:
  - id: capture-current-model
    content: Document the historical, previous, and current Ezkey Mobile Android security/storage models with explicit trust boundaries.
    status: completed
  - id: choose-sealed-secret-variant
    content: Choose and justify the target sealed-secrets architecture variant for local secret persistence.
    status: completed
  - id: map-data-classification
    content: Define the information classification model and the correct storage location/representation for each data type.
    status: completed
  - id: design-refactor-surface
    content: Map the code, documentation, and verification surfaces that the sealed-secrets refactor would change.
    status: completed
  - id: define-acceptance-criteria
    content: Define technical and security acceptance criteria for the future implementation and verification flow.
    status: completed
isProject: false
---

# Ezkey Mobile Sealed-Secrets Refactor Plan

## Status
Closed on 2026-05-03. The refactor is implemented and this file is retained only as a closure note.

## Final decision
- Keep one non-exportable EC P-256 signing key per enrollment in Android Keystore, with StrongBox requested when available.
- Use a separate app-level AES/GCM seal key in Android Keystore for long-lived enrollment secrets.
- Seal both `enrollmentProofToken` and `integrationPublicKey` at rest.
- Store only versioned sealed envelopes in AsyncStorage for those secret values.
- Keep one-time proof material in memory only.

## Implemented outcome
- Android secure storage now uses an app-level Keystore-backed AES key to seal long-lived enrollment secrets.
- AsyncStorage metadata no longer contains `enrollmentProofToken` or `integrationPublicKey` in cleartext.
- Android sealed-secret envelopes are versioned JSON records containing `version`, `algorithm`, `iv`, and `ciphertext`.
- Legacy Android Keychain-backed values are migrated through the secure storage delegate.
- Non-Android platforms remain on the lower-assurance secure-storage fallback path.

## Verification outcome
The implementation was verified by targeted unit tests, Android JVM crypto tests, and a real-device ADB inspection on 2026-05-03.

Observed on device after a fresh enrollment plus multiple auth attempts:
- `ezkey-mobile/enrollments` existed and contained one enrollment.
- `ezkey-mobile/enrollments` did not contain `enrollmentProofToken`.
- `ezkey-mobile/enrollments` did not contain `integrationPublicKey`.
- `ezkey-mobile/sealed-secret.ezkey-mobile/enrollment-proof-token.10` existed as an AES/GCM envelope.
- `ezkey-mobile/sealed-secret.ezkey-mobile/integration-public-key.10` existed as an AES/GCM envelope.
- The inspected sealed envelopes contained `version`, `algorithm`, `iv`, and `ciphertext`, with no sensitive plaintext markers.
- Log inspection did not surface the searched sensitive strings.

## Active source of truth
Use these artifacts instead of this closed plan for current behavior and future changes:
- [ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt](ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt)
- [ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/SealedSecretEnvelope.kt](ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/SealedSecretEnvelope.kt)
- [ezkey_mobile/app/services/storage/secureStorage.ts](ezkey_mobile/app/services/storage/secureStorage.ts)
- [ezkey_mobile/app/services/storage/enrollmentStorage.ts](ezkey_mobile/app/services/storage/enrollmentStorage.ts)
- [ezkey_mobile/docs/MOBILE_DATA_MODEL.md](ezkey_mobile/docs/MOBILE_DATA_MODEL.md)
- [ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md](ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md)
- [ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md](ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md)
- [product-docs/components/mobile/design-decisions.md](product-docs/components/mobile/design-decisions.md)
- [product-docs/components/mobile/data-model-and-persistence.md](product-docs/components/mobile/data-model-and-persistence.md)
- [product-docs/components/mobile/spec-test-traceability.md](product-docs/components/mobile/spec-test-traceability.md)

## Notes
- This refactor intentionally accepts a dev-first Android scope and a lower-assurance fallback outside Android.
- The historical investigation documents remain useful as historical context, but they are no longer the description of the active storage model.

## Follow-up note for a future test-hardening session

This storage hardening work is complete enough to commit independently. A separate follow-up session may extend the
Android-native test strategy around `AndroidKeyStore`, StrongBox fallback behavior, and the bridge between the native
crypto module and the mobile storage delegate.

Suggested scope for that future session:
- Evaluate whether to add Robolectric tests, Android instrumented tests, or both.
- Prefer the smallest test layer that validates real remaining risk instead of adding broad mobile test infrastructure.
- Keep the focus on storage-security guarantees, not generic UI/mobile testing.

Suggested prompt for a future session:

```text
Continue from the completed Android sealed-secrets storage hardening work in ezkey_mobile.

Current implemented baseline:
- Per-enrollment EC P-256 signing keys remain in Android Keystore, with StrongBox requested when available.
- Long-lived enrollment secrets now use a separate app-level AES/GCM seal key in Android Keystore.
- enrollmentProofToken and integrationPublicKey are stored on Android as sealed JSON envelopes in AsyncStorage, not in cleartext enrollment metadata.
- Real-device ADB verification already confirmed that ezkey-mobile/enrollments does not contain those values in cleartext and that ezkey-mobile/sealed-secret.* rows exist with version/algorithm/iv/ciphertext.
- Existing tests already cover the JavaScript storage split plus Android JVM tests for SealedSecretEnvelope round-trip, AAD binding, tamper detection, IV freshness, and algorithm/version rejection.

Goal of this follow-up:
- Review the remaining assurance gap in the Android-native test strategy.
- Decide whether Robolectric, Android instrumented tests, or a small combination is justified.
- If justified, implement only the narrowest high-value tests for real remaining risk areas such as AndroidKeyStore key creation/reuse, seal/unseal bridge behavior, and StrongBox-unavailable fallback behavior.
- Keep the change set pragmatic and security-focused.

Important files:
- ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt
- ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/SealedSecretEnvelope.kt
- ezkey_mobile/android/app/src/test/kotlin/org/ezkey/mobile/crypto/SealedSecretEnvelopeTest.kt
- ezkey_mobile/app/services/storage/secureStorage.ts
- ezkey_mobile/app/services/storage/enrollmentStorage.ts
- ezkey_mobile/scripts/verify-android-sensitive-storage.sh
- product-docs/components/mobile/design-decisions.md
- plans/mobile-sealed-secrets.plan.md

Constraints:
- Stay Android-first.
- Do not broaden into generic mobile UI testing.
- Prefer tests that discriminate real security properties over infrastructure-heavy additions.
- If a heavier test layer is not worth it, explain why clearly and leave a minimal concrete recommendation.
```
