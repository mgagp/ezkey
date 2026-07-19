# Ezkey Mobile Crypto Reference

This document records the current mobile crypto direction for Ezkey and should be read together with [`docs/CRYPTO.md`](../../docs/CRYPTO.md), which remains the canonical source for wording and guarantees.

## Current Position

- **Device signing algorithm**: EC P-256 (`secp256r1`) with `ECDSA-SHA256`
- **Current Android implementation**: `Android Keystore`
- **ECDSA wire form**: ASN.1 DER, standard Base64, **low-S only** (Auth API SEC-012; same as Demo
  Device / `SignatureService`). Android Keystore raw `SHA256withECDSA` can emit high-S ~50% of the
  time; `EzkeyCryptoModule` normalizes before returning signatures.
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

## Local device confirmation posture

The current Android reference app uses Android local-auth prompts for two distinct cases:

- approving or denying an authentication request when local protected confirmation is active
- downgrading the local security preference from protected mode back to standard mode

For both cases, the current posture is:

- `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`
- meaning Android may accept either a strong biometric or the device credential (PIN, pattern, or password)

Why this posture is used now:

- it is stronger and more realistic than relying on weak biometrics
- it avoids making the feature unavailable on phones where strong biometric is not configured but a device credential is present
- it keeps the UX practical for EXP1 while still treating both approval confirmation and security-setting downgrade as security-sensitive actions

Documentation must therefore avoid claiming `biometric-only` behavior unless a future implementation explicitly removes the device-credential fallback.

## App-enforced guarantee boundary

The current local confirmation posture must be described honestly as app-enforced at the protocol
boundary.

What is true today:

- the Android app can require local device confirmation before `respond`
- the app then signs with the enrollment key from `Android Keystore`
- this provides meaningful local UX protection on an honest client

What is not yet true today:

- the enrollment key does not require user authentication for each use
- the confirmation prompt and signing operation are sequential app actions, not one
  `BiometricPrompt.CryptoObject` operation
- the backend does not receive a cryptographic proof that the local authentication step was inseparably bound to the signature operation itself
- the current `respond` contract does not provide server-verified attestation of that local-auth condition

That means the current feature should be described as:

- meaningful honest-client UX protection
- honest client-enforced behavior
- useful future audit context candidate

and not as:

- a server-verified local-auth proof
- an attested assurance level for the response signature
- an end-to-end cryptographic guarantee equivalent to FIDO2/WebAuthn ceremony semantics

This wording boundary is intentional and should remain explicit in both product copy and technical documentation until a future protocol and attestation design changes that fact.

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
