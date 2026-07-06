# Mobile — Design Decisions

## Purpose

This document records architecture and design decisions **scoped to the mobile app**. Global decisions live in [`../../global/architecture-decisions.md`](../../global/architecture-decisions.md). Each entry follows the [architecture decision template](../../templates/architecture-decision.template.md).

## Index

| ID | Title | Status | Date |
|----|-------|--------|------|
| [ADR-MOB-0001](#adr-mob-0001-no-background-polling) | No background polling for authentication attempts | accepted | 2025-06-15 |
| [ADR-MOB-0002](#adr-mob-0002-ec-p256-keys-on-native-keystore) | EC P-256 keys generated and stored on the native keystore | accepted | 2025-07-20 |
| [ADR-MOB-0003](#adr-mob-0003-fail-closed-on-signature-checks) | Fail-closed on signature and algorithm checks | accepted | 2025-08-05 |
| [ADR-MOB-0004](#adr-mob-0004-android-app-level-sealed-secrets) | Android app-level sealed secrets for long-lived enrollment values | accepted | 2026-05-03 |
| [ADR-MOB-0005](#adr-mob-0005-ios-native-layer-rebuilt-from-clean-scaffold) | iOS native layer rebuilt from clean scaffold, existing code discarded | accepted | 2026-05-24 |

## ADR-MOB-0001 — No background polling for authentication attempts

### Metadata

- **ID:** ADR-MOB-0001.
- **Date:** 2025-06-15.
- **Status:** accepted.
- **Scope:** component:mobile.

### Context

Any background polling mechanism would introduce battery cost, privacy concerns, and the possibility of push-fatigue-like scenarios where a malicious flow could repeatedly solicit user attention. The product thesis favors explicit user intent and a simple mobile runtime.

### Decision

The mobile app polls the Auth API only on explicit user actions. There is no background service, scheduled task, or push-driven fetch for pending authentication attempts.

### Alternatives Considered

- **Scheduled background polling.** Rejected — battery cost, complexity, push-fatigue equivalent.
- **OS-level push notifications.** Rejected — reintroduces dependency on opaque third-party platform services.

### Consequences

- **Positive.** Simple runtime, no push fatigue, transparent behavior.
- **Negative.** Users must pull explicitly; acceptable for the target audience.

### Impact

- Home and Pending Auth screens.
- Pending endpoint usage.

### Related Decisions

- Global: [ADR-0002](../../global/architecture-decisions.md#adr-0002-user-initiated-mobile-polling).

## ADR-MOB-0002 — EC P-256 keys generated and stored on the native keystore

### Metadata

- **ID:** ADR-MOB-0002.
- **Date:** 2025-07-20.
- **Status:** accepted.
- **Scope:** component:mobile.

### Context

The device's private key is the most sensitive local artifact. Keeping it in JavaScript memory or in non-native storage would expose it to the JS context and make it far easier to exfiltrate. The native keystore provides strong hardware-backed isolation on Android and Keychain-backed isolation on iOS, with optional `StrongBox` on supporting Android devices.

### Decision

EC P-256 key pairs are generated and held on the native keystore (`Android Keystore`, iOS Keychain as it aligns). Application code never sees private key bytes; signing operations go through the native `EzkeyCryptoModule`. On Android, the app requests `StrongBox` when available.

### Alternatives Considered

- **Software-only key storage in JS.** Rejected — weak isolation, poor trust posture.
- **Use a single global key for all enrollments.** Rejected — reduces per-enrollment isolation.

### Consequences

- **Positive.** Strong isolation, aligned with platform best practices.
- **Negative.** Signing has a slight IPC cost; negligible in practice.
- **Clarification.** This decision covers the device private signing key. Smaller persisted application secrets such as `enrollmentProofToken` still use the app's secure-storage path and are not currently unsealed by the enrollment private key itself.

### Impact

- All enrollment and authentication flows that require device signatures.
- Reference: [`../../../ezkey_mobile/docs/NATIVE_MODULES.md`](../../../ezkey_mobile/docs/NATIVE_MODULES.md).

### Related Decisions

- Global: [ADR-0006](../../global/architecture-decisions.md#adr-0006-dual-signing-algorithms-device-ec-p256-integration-ed25519) (integration remains Ed25519; this ADR covers device signing only).

## ADR-MOB-0003 — Fail-closed on signature and algorithm checks

### Metadata

- **ID:** ADR-MOB-0003.
- **Date:** 2025-08-05.
- **Status:** accepted.
- **Scope:** component:mobile.

### Context

The protocol relies on the app verifying integration signatures on bind payloads and on the outcome of `respond`. Accepting an unsigned-or-badly-signed outcome would silently weaken the trust chain and mislead the user.

### Decision

The app aborts the current flow when:

- `integrationKeyAlgorithm` is not `ed25519`.
- Signature verification on `enrollmentBindPayloadSignedByIntegration` fails.
- Signature verification on `authAttemptProofTokenSignedByIntegration` fails.
- Signature verification on `authAttemptProofTokenResultSignedByIntegration` fails.

Failure surfaces as a fail-closed UI state that does not claim success, and logs carry only non-secret identifiers.

### Alternatives Considered

- **Show a degraded success when signatures cannot be verified.** Rejected — misleading and undermines trust.
- **Fall back to a weaker algorithm.** Rejected — protocol is defined around Ed25519/EC P-256 per the canonical payload docs.

### Consequences

- **Positive.** Preserves trust chain integrity on the device; prevents deceptive UX.
- **Negative.** Operators must ensure their backend-issued artifacts are well-formed; acceptable price.

### Impact

- Enrollment wizard, Pending Auth screen, Respond Result screen.
- Reference: [`../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`](../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md).

## ADR-MOB-0004 — Android app-level sealed secrets for long-lived enrollment values

### Metadata

- **ID:** ADR-MOB-0004.
- **Date:** 2026-05-03.
- **Status:** accepted.
- **Scope:** component:mobile.

### Context

The mobile app already keeps the per-enrollment EC P-256 signing key in `Android Keystore`, but long-lived values such
as `enrollmentProofToken` and `integrationPublicKey` still need their own at-rest protection and should not remain in
cleartext in the AsyncStorage enrollment collection. Using one additional app-level Keystore-backed encryption key is a
better fit than generating a second keystore encryption key per enrollment.

### Decision

On Android, the app provisions a dedicated app-level AES key in `Android Keystore`, requesting `StrongBox` when
available, and uses that key to seal long-lived enrollment values into JSON ciphertext envelopes stored in AsyncStorage.
The per-enrollment EC P-256 signing keys remain dedicated to signatures only. Other platforms continue to use their
existing secure-storage fallback until parity is implemented.

### Alternatives Considered

- **Keep using only Keychain-style secure-storage wrappers everywhere.** Rejected — weaker alignment with the Android-first target architecture and less explicit control over at-rest ciphertext storage.
- **Use one extra encryption key per enrollment.** Rejected — higher complexity and more key-slot pressure without enough practical value for the current app.
- **Use the per-enrollment signing key itself to wrap all other secrets.** Rejected — conflates signing and encryption roles and complicates multi-enrollment storage unnecessarily.

### Consequences

- **Positive.** Better Android at-rest hardening, explicit separation of signing and encryption roles, simpler multi-enrollment model.
- **Negative.** Android-specific native crypto surface grows and needs targeted validation.
- **Boundary.** This is local storage hardening only. It does not add backend attestation or server-side proof of StrongBox usage.

### Impact

- Enrollment persistence, Pending Auth rehydration, Android verification scripts, and mobile documentation.
- Reference: [`../../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md`](../../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md).

## ADR-MOB-0005 — iOS native layer rebuilt from clean scaffold, existing code discarded

### Metadata

- **ID:** ADR-MOB-0005.
- **Date:** 2026-05-24.
- **Status:** accepted.
- **Scope:** component:mobile, platform:iOS.

### Context

The `ezkey_mobile/ios/` directory contains an embryonic iOS native project created before all major Android refactoring cycles. It has received no meaningful attention, is not verified to build, and does not reflect the current crypto contract, enrollment model, or module structure of the Android reference implementation.

### Decision

The existing iOS-specific native code in `ezkey_mobile/ios/` is treated as discarded. The iOS rebuild starts from a clean React Native iOS scaffold. The shared React Native surface (TypeScript, navigation, screens, generated DTOs, flow code) is reused as-is. Only the iOS native layer (crypto bridge, Keychain integration, QR native module) is rebuilt from scratch.

### Alternatives Considered

- **Audit and incrementally fix the existing iOS code.** Rejected — the code predates too many refactoring cycles and the audit cost exceeds the value of any reusable component.
- **Full app rewrite including the shared RN surface.** Rejected — the shared TypeScript and screen layer is platform-agnostic and already tested on Android.

### Consequences

- **Positive.** Clean starting point aligned with the current contract; no hidden coupling to stale iOS patterns.
- **Negative.** Any non-trivial iOS-specific bridge code must be reimplemented; acceptable given the embryonic state of the existing code.

### Impact

- `ezkey_mobile/ios/` — existing native-specific content deleted before Phase 2 begins.
- Phases 2–7 of the iOS rebuild plan proceed from clean scaffold.
- Reference: [`../../../product-docs/global/legacy-retrofit/R-2026-0003-mobile-ios-implementation-plans.md`](../../../product-docs/global/legacy-retrofit/R-2026-0003-mobile-ios-implementation-plans.md).
