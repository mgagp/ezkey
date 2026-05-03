# Mobile — Design Decisions

## Purpose

This document records architecture and design decisions **scoped to the mobile app**. Global decisions live in [`../../global/architecture-decisions.md`](../../global/architecture-decisions.md). Each entry follows the [architecture decision template](../../templates/architecture-decision.template.md).

## Index

| ID | Title | Status | Date |
|----|-------|--------|------|
| [ADR-MOB-0001](#adr-mob-0001-no-background-polling) | No background polling for authentication attempts | accepted | 2025-06-15 |
| [ADR-MOB-0002](#adr-mob-0002-ec-p256-keys-on-native-keystore) | EC P-256 keys generated and stored on the native keystore | accepted | 2025-07-20 |
| [ADR-MOB-0003](#adr-mob-0003-fail-closed-on-signature-checks) | Fail-closed on signature and algorithm checks | accepted | 2025-08-05 |

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
