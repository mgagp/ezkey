# Ezkey Mobile Crypto Reference

This document records the current mobile crypto direction for Ezkey and should be read together with [`docs/CRYPTO.md`](../../docs/CRYPTO.md), which remains the canonical source for wording and guarantees.

## Current Position

- **Device signing algorithm**: EC P-256 (`secp256r1`) with `ECDSA-SHA256`
- **Current Android implementation**: `Android Keystore`
- **StrongBox posture**: requested when available, not guaranteed on all devices
- **Current iOS posture**: native secure-hardware-backed parity is still in progress and must be described conservatively
- **Private key wording**: private key material is not exposed to application code

## Device proof tokens (pending auth and similar)

Strings such as `deviceProofToken` sent to the Auth API must use the same **CSPRNG-backed** format as backend `SignatureService.generateProofToken()` (32 random bytes + 16-byte salt, URL-safe Base64 without padding, `randomPart.saltPart`). [`app/utils/generateProofToken.ts`](../app/utils/generateProofToken.ts) delegates to **`EzkeyCryptoModule.generateProofToken`** (Android `SecureRandom`, iOS `SecRandomCopyBytes`).

- **Not** Android Keystore–backed for the random bytes (only the signing key uses Keystore); the CSPRNG is the platform secure random API, not `react-native-get-random-values` / `RNGetRandomValues`.
- **Do not** substitute timestamps or ad hoc strings; that weakens unpredictability and drifts from `docs/CRYPTO.md`.

Device **signing** of that string (EC P-256) remains in the native module as elsewhere.

## Why EC P-256

Ezkey uses EC P-256 for device-side signing because it maps to native mobile platform APIs more naturally than Ed25519 for the current architecture.

Key reasons:

- Android supports EC P-256 through `Android Keystore`
- The wire format fits the existing backend contract for device signatures
- It keeps the mobile signing path straightforward and auditable

## Wording Guardrails

Use these phrases in mobile documentation:

- `Android Keystore`
- `StrongBox when available`
- `private key material is not exposed to application code`
- `current Android implementation`
- `iOS native secure-hardware-backed parity is still in progress`

Avoid these phrases unless a future implementation and verification justify them:

- `hardware-backed everywhere`
- `Secure Enclave parity`
- `guaranteed StrongBox`
- `server-verified StrongBox` or `backend-proven hardware tier` (tier is client-reported until attestation exists)
- `passkey-equivalent security`
- `FIDO2/WebAuthn-level guarantees`

## Android Summary

The current Android implementation:

- generates one EC P-256 key pair per enrollment
- uses `Android Keystore`
- requests `StrongBox` on supported devices
- exports only the public key material needed by the protocol

This is a useful platform security benefit, but it should not be described as full attestation or FIDO2/WebAuthn equivalence.

## Secure Storage vs `Android Keystore` / StrongBox

This distinction is easy to blur, so document it explicitly:

- `Android Keystore` / `StrongBox` primarily protect **cryptographic keys** and key operations
- on Android, the app now uses a dedicated app-level AES key from `Android Keystore` to seal small persisted secret values before storing ciphertext envelopes in AsyncStorage
- on other platforms, the secure-storage delegate still protects those small persisted secret values directly
- these are complementary layers, not the same thing

In the current Ezkey Android reference app:

- each enrollment has its own EC P-256 private signing key in `Android Keystore`
- `StrongBox` is requested when the device can satisfy it
- the app also provisions one app-level AES seal key in `Android Keystore` for long-lived local secrets such as `enrollmentProofToken` and `integrationPublicKey`
- AsyncStorage stores only sealed envelopes for those Android secrets; plaintext is rehydrated on demand
- the current implementation does **not** use the enrollment private key itself as a master unsealing key for all
  other mobile secrets

This sealed-secrets model is scoped to the Android reference path. It should still be described conservatively: it is a
local at-rest hardening layer, not server-side attestation or FIDO2/WebAuthn equivalence.

## Device private key storage tier (`verify`)

The Auth API accepts optional `devicePrivateKeyStorageTier` on enrollment verify. **Semantics for operators and documentation:**

- The **backend does not prove** StrongBox or any tier; it persists the string the mobile app sends. There is **no** Key Attestation validation on the server in the current product.
- The **reference Android app** derives `NONE` / `STANDARD` / `STRONG` from `KeyInfo` after key creation (StrongBox signaled via `isStrongBoxBacked` and/or API 31+ `getSecurityLevel()` as StrongBox), and requests StrongBox at generation when supported. That is honest **client-side** classification using Android APIs; it does **not** create cryptographic proof to Ezkey servers.
- Do **not** market or document Admin-visible tier as “server-verified hardware” or “cryptographically attested” until a future protocol adds verification (e.g. Key Attestation with server-side chain validation).

See **`docs/MOBILE_DEVELOPER_GUIDE.md`** (Device private key storage tier — trust model and proof boundary) and **`docs/CRYPTO.md`**.

## Integration signature verification note

When the mobile app verifies integration-signed bind, verify-result, pending, or respond-result payloads, the
stored `integrationPublicKey` should be treated as canonical verification input. Avoid verification paths that
parse a key and then re-serialize it into a different byte representation before verification. The practical rule is
simple: validate the algorithm tag, store the canonical key material received from the trusted flow, and verify later
signatures against that canonical material rather than against a transformed encoding.

## iOS Summary

iOS remains a planned or partial path depending on the feature area being discussed. Documentation should not imply that iOS currently offers the same secure-hardware-backed enrollment path as Android unless the implementation has reached parity and has been verified.

## References

- [`docs/CRYPTO.md`](../../docs/CRYPTO.md)
- [`docs/NATIVE_MODULES.md`](NATIVE_MODULES.md)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android Key Attestation](https://developer.android.com/privacy-and-security/security-key-attestation)
