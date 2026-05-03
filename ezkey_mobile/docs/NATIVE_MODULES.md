<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2025 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Native Modules Overview

> Reference guide for Ezkey Mobile native integrations and their interaction with the JavaScript runtime.

## Modules at a Glance

| Module | Platform | Responsibility | Entry Point |
|--------|----------|----------------|-------------|
| `EzkeyCryptoModule` | Android (Kotlin) | EC P-256 key management via `Android Keystore`, with `StrongBox` requested when available | `android/app/src/main/java/com/ezkeymobile/crypto/EzkeyCryptoModule.kt` |
| `EzkeyCryptoModule` | iOS (Swift) | Native secure-hardware-backed EC P-256 parity is not yet complete | `ios/EzkeyMobile/Crypto/EzkeyCryptoModule.swift` |
| `EzkeyCryptoPackage` | Android (Kotlin) | Registers crypto module with React Native | `android/app/src/main/java/com/ezkeymobile/crypto/EzkeyCryptoPackage.kt` |
| `EzkeyQrFrameProcessorPlugin` | Android (Kotlin) | Decodes QR payloads via Vision Camera frame processors | `android/app/src/main/java/com/ezkeymobile/qr/EzkeyQrFrameProcessorPlugin.kt` |
| `EzkeyCryptoModuleBridge` | iOS (Objective-C) | Exposes Swift crypto module to React Native bridge | `ios/EzkeyMobile/Crypto/EzkeyCryptoModuleBridge.m` |

## Communication Flow

```mermaid
sequenceDiagram
    participant JS as React Native (TypeScript)
    participant Android as Android Native
    participant iOS as iOS Native
    JS->>Android: NativeModules.EzkeyCryptoModule.generateEnrollmentKeyPair(enrollmentId)
    Android-->>JS: Promise resolved with true (key pair created in Keystore)
    JS->>Android: NativeModules.EzkeyCryptoModule.getPublicKey(enrollmentId)
    Android-->>JS: Promise resolved with Base64 X.509 public key (~88 chars)
    JS->>Android: NativeModules.EzkeyCryptoModule.sign(enrollmentId, payload)
    Android-->>JS: Promise resolved with Base64 ECDSA signature (~96 chars)
```

- JavaScript calls are issued through `app/services/crypto/nativeCrypto.ts`.
- Android uses `Android Keystore` (`AndroidKeyStore`) and requests `StrongBox` when available; one EC P-256 key pair is generated per enrollment, and private key material is not exposed to application code.
- iOS native secure-hardware-backed EC P-256 support is not yet at parity with the Android path and should be described as planned / in progress rather than assumed.
- QR scanning is Android-only today; iOS uses JS-based fallbacks until a Swift counterpart is implemented.

Important boundary: `EzkeyCryptoModule` is a **signing and verification** bridge. It does not currently expose a
general encrypt/decrypt or wrap/unwrap API for all local mobile secrets. In the present app, `enrollmentProofToken`
uses the secure-storage delegate, while the per-enrollment private signing key remains in the keystore path.

## Security Alignment

- EC P-256 key format matches [`docs/CRYPTO.md`](../../docs/CRYPTO.md): PKCS#8 private key, X.509 public key, ECDSA-SHA256 signatures in ASN.1 DER format, Base64 transport.
- In the current Android implementation, per-enrollment EC P-256 key pairs are generated through `Android Keystore`; `StrongBox` remains conditional on device support.
- Enrollment key pairs generated natively by platform keystore (no manual derivation needed).
- Enrollment and authentication payloads follow [`docs/features/AUTH_SECURITY.md`](../../docs/features/AUTH_SECURITY.md) to prevent proof-token reuse and enumeration.
- Native modules never persist sensitive payloads; in the current Android implementation, EC P-256 private key material remains inside the platform keystore throughout its lifecycle.
- Error messages are intentionally generic in production to avoid leaking device state; detailed logs are limited to development builds.

## iOS Documentation Guidance

- Adopt Swift Quick Help comments (`///`) for public symbols, referencing the same docs noted above.
- Include `@since 2025` and mention security constraints wherever signatures or tokens are manipulated.
- Objective-C bridge files should contain header comments that mirror the Kotlin equivalents for parity.

## Testing Notes

- Android modules can be unit-tested with Robolectric or instrumentation tests targeting the Keystore API.
- iOS modules should include XCTests verifying key generation and signing round trips using the Bridge module.
- Use Detox or end-to-end tests to validate the JavaScript ↔ native contract during enrollment and pending-auth flows.

@since 2025

