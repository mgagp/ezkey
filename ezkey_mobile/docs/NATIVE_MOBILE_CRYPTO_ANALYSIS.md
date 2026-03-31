# Native Mobile Crypto Analysis

> Historical analysis document retained for context. Do not use this file as the source of truth for public wording.

The outcome of the native crypto analysis is straightforward:

- Ezkey uses **EC P-256** for device-side signing
- the current Android path uses **Android Keystore**
- **StrongBox** is requested when available
- iOS secure-hardware-backed parity is still in progress

## What This Document Is For

This file exists to capture the reasoning that led Ezkey away from more complex mobile crypto options and toward a simpler native-platform approach.

It is useful for:
- internal design history
- understanding why Ed25519 was not used for device-side native key storage
- understanding why HKDF- or seed-based derivation was rejected for the current path

It is not useful for:
- public security claims
- wording about current guarantees
- implying passkey-equivalent or FIDO2/WebAuthn-style assurances

## Final Takeaway

Ezkey's mobile crypto direction favors:
- simpler native platform integration
- clearer operational behavior
- conservative wording about what is implemented today

For current wording, always defer to [`docs/CRYPTO.md`](../../docs/CRYPTO.md) and [`docs/MOBILE_CRYPTO_REFERENCE.md`](MOBILE_CRYPTO_REFERENCE.md).
