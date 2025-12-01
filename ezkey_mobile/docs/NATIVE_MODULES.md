<!--
  Ezkey - Open Source MFA/Passkey Alternative
  Copyright (c) 2025 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Native Modules Overview

> Reference guide for Ezkey Mobile native integrations and their interaction with the JavaScript runtime.

## Modules at a Glance

| Module | Platform | Responsibility | Entry Point |
|--------|----------|----------------|-------------|
| `EzkeyCryptoModule` | Android (Kotlin) | Ed25519 key management with HKDF derivation, root key management, signing | `android/app/src/main/java/com/ezkeymobile/crypto/EzkeyCryptoModule.kt` |
| `EzkeyCryptoModule` | iOS (Swift) | Secure Enclave/Keychain-backed Ed25519 operations (to be implemented) | `ios/EzkeyMobile/Crypto/EzkeyCryptoModule.swift` |
| `EzkeyCryptoPackage` | Android (Kotlin) | Registers crypto module with React Native | `android/app/src/main/java/com/ezkeymobile/crypto/EzkeyCryptoPackage.kt` |
| `EzkeyQrFrameProcessorPlugin` | Android (Kotlin) | Decodes QR payloads via Vision Camera frame processors | `android/app/src/main/java/com/ezkeymobile/qr/EzkeyQrFrameProcessorPlugin.kt` |
| `EzkeyCryptoModuleBridge` | iOS (Objective-C) | Exposes Swift crypto module to React Native bridge | `ios/EzkeyMobile/Crypto/EzkeyCryptoModuleBridge.m` |

## Communication Flow

```mermaid
sequenceDiagram
    participant JS as React Native (TypeScript)
    participant Android as Android Native
    participant iOS as iOS Native
    JS->>Android: NativeModules.EzkeyCryptoModule.generateRootKey()
    Android-->>JS: Promise resolved with success flag
    JS->>Android: NativeModules.EzkeyCryptoModule.getEd25519PublicKey(enrollmentId)
    Android-->>JS: Promise resolved with Base64 public key (32 bytes)
    JS->>Android: NativeModules.EzkeyCryptoModule.signEd25519(enrollmentId, payload)
    Android-->>JS: Promise resolved with Base64 signature (64 bytes)
```

- JavaScript calls are issued through `app/services/crypto/nativeCrypto.ts`.
- Android uses Android Keystore (`AndroidKeyStore`) with StrongBox when supported for root key storage; Ed25519 keys are derived via HKDF-SHA-256 per enrollment.
- iOS implementation deferred to 2026; will use Secure Enclave with CryptoKit for Ed25519 operations.
- QR scanning is Android-only today; iOS uses JS-based fallbacks until a Swift counterpart is implemented.

## Security Alignment

- Ed25519 configuration matches [`docs/CRYPTO.md`](../../docs/CRYPTO.md): 32-byte keys, 64-byte signatures, HKDF-SHA-256 derivation, Base64 transport.
- Root key (256-bit AES) stored in hardware-backed storage (StrongBox/Secure Enclave), non-extractable.
- Enrollment-specific Ed25519 keys derived deterministically via HKDF: `seed = HKDF(root_key, info = "enrollment:<id>:signing")`.
- Enrollment and authentication payloads follow [`docs/features/AUTH_SECURITY.md`](../../docs/features/AUTH_SECURITY.md) to prevent proof-token reuse and enumeration.
- Native modules never persist sensitive payloads; root key remains in hardware-backed storage, Ed25519 seeds derived on-demand.
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

