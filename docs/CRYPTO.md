# Cryptographic guide — Ezkey

## Overview

Ezkey uses **two** distinct signing contexts:

| Role | Algorithm | Wire / storage | Backend |
|------|-----------|----------------|---------|
| **Device** (per enrollment, platform-keystore-backed) | **EC P-256 (secp256r1)**, **ECDSA-SHA256** | PKCS#8 private, X.509 SPKI public (standard Base64); signatures **ASN.1 DER** (standard Base64) | JDK `java.security` |
| **Integration** (per enrollment, server-held) | **Ed25519** | PKCS#8 private (standard Base64); public key **raw 32 bytes**; signature **raw 64 bytes**; JSON fields use **Base64URL without padding** for public key and signatures | JDK `java.security` only (no Bouncy Castle) |

The mobile app verifies **integration** signatures with **Ed25519** (`IntegrationKeyVerifier` on Android uses JCA `Signature.getInstance("Ed25519")` with **Conscrypt** registered for consistent behaviour across API levels). **Device** signing and verification remain **EC P-256** via Android Keystore / platform APIs.

## Device: EC P-256 (secp256r1)

### Key generation

- **Curve**: NIST P-256 (`secp256r1`).
- **Private key**: PKCS#8 DER, standard Base64.
- **Public key**: X.509 SubjectPublicKeyInfo (SPKI), standard Base64.
- **Signature**: `SHA256withECDSA`, ASN.1 DER, standard Base64.
- **Mobile**: In the current Android implementation, keys live in Android Keystore with StrongBox requested when available. Private key material is not exposed to application code. iOS secure-hardware integration should be documented conservatively until the native path reaches feature parity.

### Signing and verification (device)

- Backend verifies device material with `SignatureService.validateSignature` (JCA `SHA256withECDSA`).
- When the server signs ECDSA for diagnostics or crypto-api helpers, signatures use **low-S** normalization where applicable for **Conscrypt/Android** parity.

## Integration: Ed25519 (minimal wire)

### Key generation (backend)

- `KeyPairGenerator` with `NamedParameterSpec.ED25519`.
- **Private**: PKCS#8, standard Base64 (storage).
- **Public (wire)**: last 32 bytes of SPKI / raw public key, **Base64URL without padding** (43 characters typical).

### Signing and verification (integration)

- Algorithm: `Signature.getInstance("Ed25519")` over **exact UTF-8** payload bytes (no pre-hash API; the provider hashes internally per Ed25519).
- **Wire**: raw 64-byte signature as **Base64URL without padding**; decoders also accept standard Base64 where the API emits it.
- **Normalize**: `SignatureService.normalizeIntegrationPublicKeyToBase64` canonicalizes stored keys to Base64URL (32 bytes after decode).
- Enrollment bind may include `integrationKeyAlgorithm`: `"ed25519"`.

### Pitfalls

- Do **not** use EC P-256 / ECDSA for **integration** pending/respond signatures; those are **Ed25519**.
- Do **not** use Ed25519 for **device** enrollment or auth-attempt **respond** signing; those stay **EC P-256**.
- Use **UTF-8** for payload strings; apply **NFC** to user-controlled fields per `AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`.

## Proof tokens

- **Enrollment proof token**: URL-safe Base64 (no padding), structure `randomBytes.salt` (see `SignatureService.generateProofToken`).
- **Device proof token**: URL-safe Base64, 32 random bytes (usage in auth flows).

## Implementation references

- Backend: [`SignatureService.java`](../ezkey-core/src/main/java/org/ezkey/signature/SignatureService.java), [`Ed25519SpkiBytes.java`](../ezkey-core/src/main/java/org/ezkey/signature/Ed25519SpkiBytes.java), [`EcdsaDerCodec.java`](../ezkey-core/src/main/java/org/ezkey/signature/EcdsaDerCodec.java)
- Android: [`EzkeyCryptoModule.kt`](../ezkey_mobile/android/app/src/main/java/com/ezkeymobile/crypto/EzkeyCryptoModule.kt), [`IntegrationKeyVerifier.kt`](../ezkey_mobile/android/app/src/main/java/com/ezkeymobile/crypto/IntegrationKeyVerifier.kt)
- Payload format: [`AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)

## Compatibility tests

- Java: `SignatureServiceTest`, auth/enrollment service tests; `ezkey-demo-device` `SignatureCompatibilityTest`.
- Android JVM: `IntegrationKeyVerifierTest` (optional golden files under `ezkey_mobile/android/app/src/test/resources/fixtures/`).

## Validation and debugging (mobile)

1. Open **Diagnostics** from the home header; run **Run self-test** to sign a diagnostic string with the **device** key and inspect Base64 SPKI public key and DER signature.
2. For **integration** signature issues, compare canonical payload strings and Ed25519 wire lengths (32 / 64 bytes after decode) with `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`.

---

This document should be updated when cryptographic contracts or wire formats change.
