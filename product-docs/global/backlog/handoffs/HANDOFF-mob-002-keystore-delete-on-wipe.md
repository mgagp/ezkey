# Handoff — MOB-002 Keystore delete on enrollment wipe

**Status:** ready for cold agent after operator Go  
**Finding:** MOB-002 (P1)  
**Assessment:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`](../../hygiene/mobile-protocol-security/2026-07-16-pass-1.md)

---

## Context

`EzkeyCryptoModule.deleteKeyPair` / `nativeCrypto.deleteKeyPair` exist but are never called from
`enrollmentStorage.deleteEnrollment`, `clearAll`, or Danger Zone. Local wipe removes metadata and
sealed secrets, leaving `ezkey_enrollment_{id}` in Android Keystore.

## In scope

1. Call `nativeCrypto.deleteKeyPair(enrollmentId)` (best-effort) from single-enrollment delete and
   clear-all paths.
2. Ensure failures are logged but do not block metadata cleanup (or fail closed — confirm with
   operator; default: best-effort delete, still clear storage).
3. Add Jest/hook tests asserting delete is invoked with the correct id(s).
4. Optionally add a short note in `MOBILE_DATA_MODEL.md` lifecycle table.

## Out of scope

- Server-side enrollment revoke (already API-side)
- Rotating / migrating existing orphaned aliases in the wild (optional follow-up cleanup utility)
- iOS delete parity beyond not breaking the bridge

## Read first

- `ezkey_mobile/app/services/storage/enrollmentStorage.ts`
- `ezkey_mobile/app/hooks/useEnrollments.ts`
- `ezkey_mobile/app/screens/DangerZone/DangerZoneScreen.tsx`
- `ezkey_mobile/app/services/crypto/nativeCrypto.ts`
- `ezkey_mobile/android/.../EzkeyCryptoModule.kt` (`deleteKeyPair`)

## Validation

| Layer | Required? |
| --- | --- |
| Unit (mock `deleteKeyPair` called) | **Yes** |
| Emulator/instrumentation (alias removed) | Recommended |
| Physical phone | Optional |

## Acceptance

- Delete one enrollment → native delete called for that id.
- Clear all → native delete called for each known enrollment id.
- Existing storage tests still pass; no cleartext secret regression.

## Suggested session opening message (copy-paste)

```
Implement MOB-002 from docs/security/mobile-protocol-crypto-assessment-2026-07.md.

Wire nativeCrypto.deleteKeyPair into enrollment delete and clear-all. Add unit tests that assert
the native delete call. Read product-docs/global/backlog/handoffs/HANDOFF-mob-002-keystore-delete-on-wipe.md
and ezkey_mobile/AGENTS.md. Do not expand into CryptoObject or pinning.
```
