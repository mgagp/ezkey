# Handoff — MOB-001 Local protected approval binding

**Status:** ready for cold agent after operator Go  
**Finding:** MOB-001 (P1)  
**Assessment:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`](../../hygiene/mobile-protocol-security/2026-07-16-pass-1.md)

---

## Context

Protected approval today shows `BiometricPrompt`, then signs with a Keystore key created with
`setUserAuthenticationRequired(false)` and **without** `BiometricPrompt.CryptoObject`. Local
confirmation is honest-client UX, not Keystore-enforced authentication intent.

## In scope (choose one track — confirm with operator)

### Track A — Wording / claim lockdown (hygiene, default if Go-wording)

1. Audit mobile copy (`SecurityScreen`, i18n), `MOBILE_CRYPTO_REFERENCE.md`, PRD/README StrongBox /
   confirmation phrasing.
2. Ensure no claim of server-verified local auth or Keystore-bound biometric signing.
3. Align Admin UI tier/confirmation language if it overclaims.
4. Unit/docs only; no native key-model change.

### Track B — CryptoObject / auth-bound keys (program)

1. Design auth-per-use or time-bound Keystore parameters for protected enrollments / preference.
2. Wire `signWithAuthentication` through `BiometricPrompt.authenticate(CryptoObject)`.
3. Handle biometric enrollment invalidation / re-enroll UX (`EZK_KEY_INVALIDATED` if applicable).
4. Promote `I-*` + `TB-*` before large native change; validate on **physical phone**.

## Out of scope

- Server-side attestation of local auth
- iOS parity
- Certificate pinning (MOB-005)

## Read first

- `ezkey_mobile/AGENTS.md`
- `ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt`
- `ezkey_mobile/app/services/crypto/cryptoService.ts`
- `ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md` (§ Local device confirmation / declarative guarantee)
- `docs/MOBILE_DEVELOPER_GUIDE.md` (trust model boundary)

## Validation

| Track | Tests |
| --- | --- |
| A | Docs review; optional Jest on copy helpers; no device required |
| B | Instrumentation + **physical StrongBox-capable phone** for prompt + failed-auth / cancel paths |

## Acceptance

- Track A: no remaining “biometric-bound signature” overclaim in living docs/UI.
- Track B: Keystore rejects sign without successful user auth; Maestro/manual evidence attached.

## Suggested session opening message (copy-paste)

```
Implement MOB-001 from docs/security/mobile-protocol-crypto-assessment-2026-07.md.

Operator decision: <Track A wording | Track B CryptoObject program>.

Read product-docs/global/backlog/handoffs/HANDOFF-mob-001-local-auth-binding.md and
ezkey_mobile/AGENTS.md. Do not expand into pinning or attestation. Keep Android-first scope.
```
