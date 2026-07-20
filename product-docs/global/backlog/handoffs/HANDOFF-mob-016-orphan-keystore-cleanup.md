# Handoff — MOB-016 orphan Keystore cleanup after failed enrollment verify

**Status:** `superseded` — **defer (absorbed)** into MOB-011 program activity 2 (2026-07-20 Grill Me)  
**Lane:** Mobile protocol security hygiene (pass-2) → program  
**Finding:** MOB-016 (P2, Confirmed)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)  
**Assessment register:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §14.2 MOB-016

**Canonical implementation:** [`I-2026-07-20-mobile-installation-scoped-enrollment-identity`](../ideas/I-2026-07-20-mobile-installation-scoped-enrollment-identity.md) /
[`TB-2026-07-20-mobile-installation-scoped-enrollment-identity`](../TB-2026-07-20-mobile-installation-scoped-enrollment-identity.md)
(after trust-zone canon TB). Do **not** open a standalone MOB-016 hygiene PR.

This file is retained as an evidence / scenario pointer only.

---

## Operator decisions (revised 2026-07-20)

1. **Disposition:** **defer (absorbed)** — orphan cleanup is part of the first-principles identity/crypto design (keys still created before verify; cleanup on failure is mandatory in that slice).
2. HITL briefing accepted 2026-07-19: Keystore key created before verify/save leaves orphans on failure; cleanup required when persistence never succeeded.

---

## One-sentence problem

`finalizeEnrollment` calls `ensureEnrollmentKeyPair` before Auth API verify and before `saveEnrollment`; on verify failure, verify-result signature failure, or save failure, the Keystore alias remains with no `StoredEnrollment`, and native `containsAlias` makes that orphan sticky on retry.

---

## Scenario that led to the observation (preserve this narrative)

Not a remote MFA bypass. White-box read of the enrollment wizard verify path during Pass-2.

1. User submits challenge → `ensureEnrollmentKeyPair` creates EC key under enrollment alias.
2. Verify API fails, **or** verify-result Ed25519 fails (`return` without delete), **or** `saveEnrollment` throws.
3. No Home row; Keystore alias remains.
4. Later retry / other flows see `containsAlias` → reuse same key (sticky orphan). Pre-fix MOB-011 made cross-installation alias reuse worse; post-MOB-013 pending/respond must not create replacements — wizard create + cleanup becomes the critical lifecycle pair.

**Observation label:** *Key created too early relative to successful local persistence; no best-effort delete on failure paths.*

**Non-claim:** does not forge respond. Impact is Keystore lifecycle / local–server divergence hygiene.

---

## Intended fix shape

### Behavioral contract

| Step | On success | On failure before successful save |
| --- | --- | --- |
| Create key (wizard only) | Keep | — |
| Verify API / result verify / save | Persist enrollment | **Best-effort `deleteKeyPair`** for the alias just created/used when no `StoredEnrollment` was saved |
| Retry enrollment | May create fresh key after cleanup | — |

Notes:

- Fail-open delete is OK (same spirit as MOB-002 wipe): log if delete fails; still show the enrollment error to the user.
- If verify **succeeded on server** but local save failed, deleting the local key may diverge from a server that already stored `devicePublicKey` — call this out in implementation comments and prefer: delete only when verify never reached success **or** document that save failure after verify may require re-enroll / admin revoke. Grill lightly if ambiguous; default pragmatic rule:
  - **Delete** when failure is before successful verify-result acceptance (challenge/API/result sig fail).
  - **Delete** when save fails after verify-result OK only if product accepts “re-enroll required” (server may already have the pubkey) — confirm with operator if unclear; otherwise still delete local orphan and surface “enrollment may need admin reset / new enrollment” carefully.
- Do not add pending/respond generate back.

### Suggested implementation sketch

1. `useEnrollmentWizard.ts` `finalizeEnrollment`:
   - Track whether a key was ensured/created in this attempt and whether save completed.
   - On early returns / catch before successful save → `deleteKeyPair` best-effort (via `cryptoService` / `nativeCrypto`).
2. Keep create in wizard; do not move create after verify unless Grill Me proves wire needs pubkey before verify (it does — pubkey is in verify body). So **create-before-verify stays**; cleanup is the fix, not reorder without redesign.
3. Unit/hook tests: mock failed verify → assert delete called; mock failed verify-result → delete called; mock failed save → delete called (per agreed rule); success path → delete **not** called.
4. No UI redesign required beyond existing challenge errors unless save-after-verify needs a clearer message.

### Tests (minimum)

- `useEnrollmentWizard` tests for the three failure branches + success no-delete.
- Mock `deleteKeyPair` / native delete.

---

## Evidence map (read these first)

- `ezkey_mobile/app/hooks/useEnrollmentWizard.ts` — `finalizeEnrollment` order; early return on `!verifyResultOk`; catch
- `ezkey_mobile/app/services/crypto/cryptoService.ts` / `nativeCrypto` — delete API (post–MOB-002)
- `EzkeyCryptoModule.kt` — `generateEnrollmentKeyPair` `containsAlias` short-circuit; `deleteKeyPair`
- Assessment §14.2 MOB-016; related MOB-011, MOB-013

---

## Product / methodology constraints

- Lane D hygiene; no `I-*` / `TB-*`.
- No Auth API / OpenAPI changes.
- Respect implementation order **011 → 012 → 013** then this item when Keystore identity/ensure work is in flight; MOB-016 may follow 013 on the same hygiene train.
- Update campaign note + assessment disposition when PR lands; delete this handoff on closeout.

---

## Optional one-question Grill Me (only if save-after-verify is unclear)

If the implementing agent is unsure about save failure after a successful verify-result:

> If Auth API verify already accepted the device public key but `saveEnrollment` fails locally, should we still best-effort delete the Keystore key (forcing a new key + likely failed re-verify against an already-bound enrollment), or keep the orphan key to maximize retry-save chance?

Wait for operator answer before choosing. Default if operator is unavailable and risk is low: **keep key on save-only failure after verify OK** (retry save / retry finalize without recreate); **delete on all pre-verify-OK failures**. Document the choice in the PR.

---

## Suggested first agent turns

1. Confirm MOB-011 (and preferably MOB-013) on branch.
2. Read this handoff + `finalizeEnrollment` + deleteKeyPair API.
3. Implement cleanup per contract + optional save-after-verify rule above.
4. Add wizard unit tests; run targeted mobile unit tests.
5. Do not commit until the operator asks.

---

## Out of scope

- MOB-015 broken-list UI
- MOB-014 pending malformed
- Moving keygen after server verify without product redesign
- CryptoObject / user-auth keys (MOB-001 Track B)

---

## Paste-ready starter message (for the implementing agent)

```text
Read product-docs/global/backlog/handoffs/HANDOFF-mob-016-orphan-keystore-cleanup.md end-to-end.
Confirm MOB-011 is on this branch (and MOB-013 if possible); if not, stop and report.

Task: implement MOB-016 only — on enrollment finalize failures before a successful local save, best-effort delete the Keystore enrollment key so orphans do not stick via containsAlias. Keep create-before-verify (pubkey required on verify). For save failure after verify-result OK, follow the handoff default or ask me the one Grill Me question. Add wizard unit tests. Do not bundle 012/014/015. Do not create I-*/TB-*. Do not commit until I ask.
```
