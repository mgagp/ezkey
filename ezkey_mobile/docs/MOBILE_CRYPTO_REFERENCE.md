# Ezkey Mobile Crypto Reference

This document records the current mobile crypto direction for Ezkey and should be read together with [`docs/CRYPTO.md`](../../docs/CRYPTO.md), which remains the canonical source for wording and guarantees.

## Current Position

- **Device signing algorithm**: EC P-256 (`secp256r1`) with `ECDSA-SHA256`
- **Current Android implementation**: `Android Keystore`
- **StrongBox posture**: requested when available, not guaranteed on all devices
- **Current iOS posture**: native secure-hardware-backed parity is still in progress and must be described conservatively
- **Private key wording**: private key material is not exposed to application code

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
- `passkey-equivalent security`
- `FIDO2/WebAuthn-level guarantees`

## Android Summary

The current Android implementation:
- generates one EC P-256 key pair per enrollment
- uses `Android Keystore`
- requests `StrongBox` on supported devices
- exports only the public key material needed by the protocol

This is a useful platform security benefit, but it should not be described as full attestation or FIDO2/WebAuthn equivalence.

## iOS Summary

iOS remains a planned or partial path depending on the feature area being discussed. Documentation should not imply that iOS currently offers the same secure-hardware-backed enrollment path as Android unless the implementation has reached parity and has been verified.

## References

- [`docs/CRYPTO.md`](../../docs/CRYPTO.md)
- [`docs/NATIVE_MODULES.md`](NATIVE_MODULES.md)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android Key Attestation](https://developer.android.com/privacy-and-security/security-key-attestation)
