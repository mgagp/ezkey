# Handoff — MOB-007 Deduplicate pending claim path

**Status:** implemented — PR [#384](https://github.com/mgagp/ezkey/pull/384)  
**Finding:** MOB-007 (P2)  
**Assessment:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`](../../hygiene/mobile-protocol-security/2026-07-16-pass-1.md)

---

## Context

`EnrollmentDetailScreen.handleCheckPending` reimplements device proof token generation, pending
API call, and Ed25519 pending-signature verification already present in
`usePendingAuth.loadPendingAttempt`. Security fixes can drift between paths.

## In scope

1. Extract a shared pending-claim helper (hook or service) used by:
   - Enrollment Detail “Check pending” → navigate with `initialAttempt`, and
   - Pending Auth screen auto-load when no `initialAttempt`.
2. Preserve user-initiated pull model (no background polling).
3. Preserve fail-closed behavior on missing integration key / invalid signature.
4. Move/extend unit tests from `usePendingAuth.test.ts` to cover the shared helper; keep Detail
   screen thin.

## Out of scope

- Changing Auth API contract
- Maestro flow expansion beyond smoke if helper behavior unchanged
- Biometric / CryptoObject work (MOB-001)

## Read first

- `ezkey_mobile/app/screens/EnrollmentDetail/EnrollmentDetailScreen.tsx`
- `ezkey_mobile/app/hooks/usePendingAuth.ts`
- `ezkey_mobile/app/services/crypto/authAttemptPayload.ts`
- `ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md`

## Validation

| Layer | Required? |
| --- | --- |
| Unit (shared helper + hook) | **Yes** |
| Physical phone | No (unless behavior changes) |

## Acceptance

- One implementation owns pending claim + signature verify.
- Detail and Pending screens both fail closed on invalid integration signature.
- Existing pending Jest scenarios still pass.

## Suggested session opening message (copy-paste)

```
Implement MOB-007 from docs/security/mobile-protocol-crypto-assessment-2026-07.md.

Deduplicate EnrollmentDetail handleCheckPending and usePendingAuth.loadPendingAttempt into one
shared helper. Keep user-initiated pull and fail-closed verify. Read
product-docs/global/backlog/handoffs/HANDOFF-mob-007-dedupe-pending-path.md and ezkey_mobile/AGENTS.md.
```
