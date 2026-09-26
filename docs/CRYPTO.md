# Cryptographic guide — Ezkey

## Overview

Ezkey uses **two** distinct signing contexts:

| Role | Algorithm | Wire / storage | Backend |
|------|-----------|----------------|---------|
| **Device** (per enrollment, platform-keystore-backed) | **EC P-256 (secp256r1)**, **ECDSA-SHA256** | PKCS#8 private, X.509 SPKI public (standard Base64); signatures **ASN.1 DER** (standard Base64) | JDK `java.security` |
| **Integration** (per enrollment, server-held) | **Ed25519** | PKCS#8 private (standard Base64); public key **raw 32 bytes**; signature **raw 64 bytes**; JSON fields use **Base64URL without padding** for public key and signatures | JDK `java.security` only (no Bouncy Castle) |

The mobile app verifies **integration** signatures with **Ed25519** (`IntegrationKeyVerifier` on Android uses JCA `Signature.getInstance("Ed25519")` with **Conscrypt** registered for consistent behaviour across API levels). **Device** signing and verification remain **EC P-256** via Android Keystore / platform APIs.

**Decision record:** [ADR-0006](../product-docs/global/architecture-decisions.md#adr-0006-dual-signing-algorithms-device-ec-p256-integration-ed25519) (why device and integration use different algorithms).

**Post-quantum posture (orientation, not a wire-format change):** both device EC P-256 and integration Ed25519 are pre-quantum (Shor). Symmetric pieces (AES-256-GCM at rest, HMAC-SHA256, 256-bit proof tokens) are already sized for a Grover-adjusted world. The analysis-design map — attack angles, what is already adequate, window management vs hybrid signatures, why per-auth device-key rotation is the wrong TLS analogue — lives in [`product-docs/global/vision/V-2026-09-15-post-quantum-crypto-posture.md`](../product-docs/global/vision/V-2026-09-15-post-quantum-crypto-posture.md). Do not read this guide as a post-quantum resistance claim.

## Device: EC P-256 (secp256r1)

### Key generation

- **Curve**: NIST P-256 (`secp256r1`).
- **Private key**: PKCS#8 DER, standard Base64.
- **Public key**: X.509 SubjectPublicKeyInfo (SPKI), standard Base64.
- **Signature**: `SHA256withECDSA`, ASN.1 DER, standard Base64.
- **Mobile**: In the current Android implementation, keys live in Android Keystore with StrongBox requested when available. Private key material is not exposed to application code. iOS secure-hardware integration should be documented conservatively until the native path reaches feature parity.

**`devicePrivateKeyStorageTier` (enrollment verify):** The wire value `NONE` / `STANDARD` / `STRONG` is **client-reported metadata**. The Auth API does **not** validate Android Key Attestation or any hardware attestation chain; the backend stores what the app sends. A well-behaved Android client derives the tier from `KeyInfo` (and requested StrongBox at generation time) per `docs/MOBILE_DEVELOPER_GUIDE.md` — but that remains **trust in the client**, not a server-proven property. Do not describe Admin-visible tier as “verified StrongBox” unless a future protocol adds attestation or equivalent verification.

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
- **Bind response**: `integrationKeyAlgorithm` is **required** on `POST /api/v1/enrollments/bind` (see `docs/ENDPOINT.md`). Phase 1 only supports **`ed25519`** (exact string). Integrators and mobile clients should **validate** this field before interpreting `integrationPublicKey` or verifying the integration signature; if it is not exactly `ed25519`, **stop** the enrollment flow (fail closed). The reference app implements this check (see `ezkey_mobile/app/utils/integrationKeyAlgorithm.ts`).

### Pitfalls

- Do **not** use EC P-256 / ECDSA for **integration** pending/respond signatures; those are **Ed25519**.
- Do **not** use Ed25519 for **device** enrollment or auth-attempt **respond** signing; those stay **EC P-256**.
- Use **UTF-8** for payload strings; apply **NFC** to user-controlled fields per `AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`.

## Proof tokens

- **Enrollment proof token**: URL-safe Base64 (no padding), structure `randomPart.saltPart` (see `SignatureService.generateProofToken()` in Java: 32 random bytes + 16 salt bytes, URL-safe Base64 without padding, dot-separated).
- **Device proof token** (e.g. `deviceProofToken` on `POST /api/v1/auth-attempts/pending`): **same algorithm and wire format** as enrollment proof tokens — `SignatureService.generateProofToken()` — not timestamps or other predictable strings.

**Mobile (reference app `ezkey_mobile`):** call [`generateProofToken()`](../ezkey_mobile/app/utils/generateProofToken.ts), which delegates to [`EzkeyCryptoModule.generateProofToken`](../ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt) (Android `SecureRandom` / iOS `SecRandomCopyBytes`). This avoids a separate `RNGetRandomValues` native module; random bytes are not produced inside the JavaScript engine.

## Implementation references

- Backend: [`SignatureService.java`](../ezkey-core/src/main/java/org/ezkey/signature/SignatureService.java), [`Ed25519SpkiBytes.java`](../ezkey-core/src/main/java/org/ezkey/signature/Ed25519SpkiBytes.java), [`EcdsaDerCodec.java`](../ezkey-core/src/main/java/org/ezkey/signature/EcdsaDerCodec.java)
- Mobile proof token (TS): [`generateProofToken.ts`](../ezkey_mobile/app/utils/generateProofToken.ts)
- Android: [`EzkeyCryptoModule.kt`](../ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt), [`IntegrationKeyVerifier.kt`](../ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/IntegrationKeyVerifier.kt)
- Payload format: [`AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)

## Compatibility tests

- Java: `SignatureServiceTest`, auth/enrollment service tests; `ezkey-demo-device` `SignatureCompatibilityTest`.
- Android JVM: `IntegrationKeyVerifierTest` (optional golden files under `ezkey_mobile/android/app/src/test/resources/fixtures/`).

## Validation and debugging (mobile)

1. Open **Diagnostics** from the home header; run **Run self-test** to sign a diagnostic string with the **device** key and inspect Base64 SPKI public key and DER signature.
2. For **integration** signature issues, compare canonical payload strings and Ed25519 wire lengths (32 / 64 bytes after decode) with `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`.

---

This document should be updated when cryptographic contracts or wire formats change.
