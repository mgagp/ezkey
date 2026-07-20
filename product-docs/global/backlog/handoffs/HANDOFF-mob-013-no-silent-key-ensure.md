# Handoff — MOB-013 no silent key replacement on pending/respond

**Status:** `open` — **fix authorized in principle**; implement **after** MOB-011 then MOB-012  
**Lane:** Mobile protocol security hygiene (pass-2)  
**Finding:** MOB-013 (P1 with MOB-011/012 context / P2 in isolation, Confirmed)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)  
**Assessment register:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §14.2 MOB-013

Use this prompt to start a **new Cursor session** that **implements** MOB-013 only when MOB-011 and MOB-012 are already merged (or clearly complete on the branch).  
If those predecessors are not done, stop and tell the operator — do not invent a temporary identity model or unlocked-device gate inside this PR.

---

## Operator decisions (already made)

1. **Disposition:** **fix** (hygiene).
2. **Implementation order (mandatory):** **MOB-011 → MOB-012 → MOB-013**.
3. HITL briefing for MOB-013 was accepted 2026-07-19 (silent `ensure` on pending/respond is wrong once the server is bound to a public key).

---

## One-sentence problem

After enrollment verify, the Auth API is permanently bound to the device public key, but `claimPendingAttempt` and `usePendingAuth.handleRespond` call `ensureEnrollmentKeyPair`, which **generates a new Keystore key** when the alias is missing — a silent replacement that cannot satisfy the server and hides real key-loss / identity issues.

---

## Scenario that led to the observation (preserve this narrative)

Not a remote exploit. White-box read of the post–Lot A pending/respond path during Pass-2.

1. Enrollment verified → server stores public key P₁; Keystore holds the matching private key under the enrollment alias.
2. Local key later missing (partial wipe, MOB-012 platform deletion, etc.).
3. User opens pending claim or respond.
4. `ensureEnrollmentKeyPair` sees missing alias → **creates P₂**.
5. Client signs with P₂; server verifies with P₁ → opaque protocol failure instead of “local key missing / re-enroll.”

With pre-fix MOB-011, the same `ensure` could also **reuse the wrong installation’s alias** for the same numeric id. After MOB-011, aliases should already be installation-scoped; MOB-013 still must not auto-create on pending/respond.

**Observation label:** *Pending/respond treat the enrolled signing key as infra to “ensure,” not as irreversible protocol continuity material.*

**Non-claim:** not a remote MFA bypass — a replacement key cannot forge a valid respond against P₁.

---

## Prerequisites (do not skip)

| Predecessor | Why required before MOB-013 code |
| --- | --- |
| **MOB-011** | Key / storage identity must be installation-scoped so “require existing key” checks the correct alias. |
| **MOB-012** | Unlocked-device gate should be corrected so platform key loss is less likely; MOB-013 then fail-closes cleanly when a key is still missing. |

Confirm on the working branch (or `main` if already merged):

- Installation-scoped local ids / aliases / seal keys (MOB-011).
- `setUnlockedDeviceRequired` gated per MOB-012 decision (typically API 35+ for non–user-auth keys).

---

## Intended fix shape

### Behavioral contract

| Path | Allowed | Forbidden |
| --- | --- | --- |
| Enrollment verify (`finalizeEnrollment` / wizard) | **Create** key if needed (existing ensure/generate OK here) | — |
| Pending (`claimPendingAttempt`) | **Require** existing key; fail closed if absent | Generate / rotate |
| Respond (`usePendingAuth.handleRespond`) | **Require** existing key; fail closed if absent | Generate / rotate |

Never auto-rotate a device key without a backend protocol that registers a new public key (none today).

### Suggested implementation sketch

1. Split API in `cryptoService` (names illustrative):
   - `createEnrollmentKeyPair` / keep generate for wizard only, **or**
   - `requireEnrollmentKeyPair` / `assertEnrollmentKeyPairExists` that **does not** call generate.
2. Native side: prefer a dedicated method (e.g. `hasEnrollmentKeyPair` / `requireEnrollmentKeyPair`) that resolves false or rejects if alias missing — **do not** create. Alternatively, JS checks existence if a safe existence API already exists; do not rely on generate’s create branch.
3. Replace `ensureEnrollmentKeyPair` in:
   - `ezkey_mobile/app/services/pendingAuth/claimPendingAttempt.ts`
   - `ezkey_mobile/app/hooks/usePendingAuth.ts` (`handleRespond`)
4. Map missing-key failures to clear UX / result kind (re-enroll signal). Prefer extending `ClaimPendingResult` fail_closed reasons rather than opaque network errors. Align respond path errors with the same copy theme.
5. Leave wizard `finalizeEnrollment` create behavior intact (MOB-016 may later add orphan cleanup on failed verify — **out of scope** here unless trivial and operator expands).

### Tests (minimum)

- Unit: `claimPendingAttempt` — missing key → **no** `generateEnrollmentKeyPair`; returns fail_closed (or equivalent) with a dedicated reason.
- Unit: `usePendingAuth` respond path — same.
- Update existing mocks that assumed ensure-always-succeeds via generate.
- Optional instrumentation: absent alias → fail closed (emulator).

---

## Evidence map (read these first)

- `claimPendingAttempt.ts` — `ensureEnrollmentKeyPair` before proof-token sign
- `usePendingAuth.ts` — `ensureEnrollmentKeyPair` before `signForRespond`
- `cryptoService.ts` — `ensureEnrollmentKeyPair` → `nativeCrypto.generateEnrollmentKeyPair`
- `EzkeyCryptoModule.kt` — `generateEnrollmentKeyPair`: `containsAlias` → return; else create
- Assessment §14.2 MOB-013; related coupling MOB-011 / MOB-012 / MOB-016

---

## Product / methodology constraints

- Lane D hygiene; dedicated small PR after 011 and 012.
- Do not create `I-*` / `TB-*` for this item.
- Do not change Auth API / OpenAPI.
- Do not implement MOB-014/015/016 in the same PR unless the operator explicitly expands scope.
- iOS: only touch shared TS contracts if needed; no iOS native rewrite.
- Update campaign note decision row + assessment disposition when the PR lands; delete this handoff on closeout (pass-1 pattern).

---

## Mini Grill Me — only if something is still ambiguous

Usually skip if 011/012 are done. If needed:

1. Exact native API: new `hasKey` vs reject-on-missing require method?
2. UX copy: single “re-enroll” string vs distinct pending vs respond messages?
3. Should `ensureEnrollmentKeyPair` be removed entirely or kept as a wizard-only alias of create?

---

## Suggested first agent turns (implementation session)

1. Verify MOB-011 and MOB-012 are present on the branch.
2. Read this handoff + current `claimPendingAttempt` / `usePendingAuth` / `cryptoService` / native generate.
3. Implement require-vs-create split; update call sites; add unit tests.
4. Run mobile unit tests for touched areas; do not run unrelated stacks unless asked.
5. Stop for commit/PR until the operator asks (Windows: `scripts/git-commit.sh` / `scripts/git-pr.sh`).

---

## Out of scope

- MOB-011 identity migration / MOB-012 unlocked-device gate (must already be done)
- MOB-016 orphan cleanup on failed verify (follow-up)
- CryptoObject / `setUserAuthenticationRequired` (MOB-001 Track B)
- Changing verify payload or server key binding

---

## Paste-ready starter message (for the implementing agent)

```text
Read product-docs/global/backlog/handoffs/HANDOFF-mob-013-no-silent-key-ensure.md end-to-end.
Confirm MOB-011 and MOB-012 are already implemented on this branch; if not, stop and report.

Task: implement MOB-013 only — pending/respond must require an existing enrollment Keystore key and fail closed if missing; never call generate/ensure that creates a replacement key. Wizard verify may still create keys. Preserve the silent-regen scenario narrative from the handoff. Add unit tests. Do not fix MOB-014/015/016 in this PR. Do not create I-*/TB-*. Do not commit until I ask.
```
