# Hardware-Backed Key Derivation Analysis

> Historical analysis document retained for design history. It does not define current Ezkey guarantees.

## Context

This file previously captured an investigation into deriving per-enrollment signing material from a protected root secret. That direction was abandoned because it added complexity and did not fit the current native-platform path cleanly.

## Outcome

The current direction is simpler:
- use one EC P-256 key pair per enrollment
- use the native platform keystore path
- on Android, use `Android Keystore` and request `StrongBox` when available
- describe iOS conservatively until native secure-hardware-backed parity is implemented and verified

## Why The Earlier Approach Was Rejected

The earlier derivation model created unnecessary complexity around:
- key derivation
- encrypted seed handling
- runtime memory exposure trade-offs
- platform-specific keystore limitations

That complexity was not justified for the current product direction.

## Documentation Rule

Do not use this file to justify claims such as:
- guaranteed hardware backing on every device
- Secure Enclave parity
- passkey-equivalent assurances
- FIDO2/WebAuthn-style properties

For current wording, refer to [`docs/CRYPTO.md`](../../docs/CRYPTO.md) and [`docs/MOBILE_CRYPTO_REFERENCE.md`](MOBILE_CRYPTO_REFERENCE.md).
