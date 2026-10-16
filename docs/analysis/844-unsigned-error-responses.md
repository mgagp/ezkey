<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# #844 — Unsigned respond errors vs mobile `NoLongerActive`

**Status:** **decided and implemented** (Marc, 2026-10-15; **m1 unified 409** 2026-10-16) on PR [#833](https://github.com/mgagp/ezkey/pull/833).  
**Triggers:** QA Sphere run EZK/14, case MOB-AUTH-007 on PR #833 @ `d8812820` / Pixel 7 Pro / Rootbeer.  
**Issue:** [#844](https://github.com/mgagp/ezkey/issues/844).  
**Review:** Patrick craft critique (2026-10-15) — factual correction below; Christophe guardrails folded into Marc's decision; Marc m1 (with Christophe) unifies client 409.  
**Vocabulary:** states and terms from `ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md` (PR [#835](https://github.com/mgagp/ezkey/pull/835)) — especially `Ready`, `Responding`, `GlobalError`, `NoLongerActive`, `UncertainDecision`, `Completed`.

---

## Final decision (Marc, 2026-10-15; m1 2026-10-16)

**Rule:** only decisions and final statuses are integration-signed. The server never emits an unsigned 200. Any unsigned response leads the app to a neutral Back-only state (no enrollment/key wipe, no automatic deny or approve retry). Local catalog strings only (EN/FR by `type`); never raw problem+json `detail`.

### Backend (`AuthAttemptRespondService`)

| Condition | HTTP | Catalog type |
| --- | --- | --- |
| Attempt or enrollment not found | **409** | `auth-attempt-state-conflict` |
| Still `PENDING` (respond before claim) | **409** | `auth-attempt-state-conflict` |
| Terminal (`ACCEPTED`, `REJECTED`/`DENIED`, `EXPIRED`, `INVALID`) / cancel / superseded / TTL | **409** | `auth-attempt-state-conflict` |
| Missing/blank **per-enrollment** integration private key (even for APPROVED/DENIED/FAILED) | **503** | `integration-signing-unavailable` — runtime only: throw instead of unsigned 200; outer respond `@Transactional` rolls back when the status flip was in the same transaction (no JVM startup gate for integration keys; they are not instance-global) |
| Legitimate FAILED (invalid device signature, wrong challenge) | **200** signed `FAILED` | — non-regression tests required |

**Marc m1 (with Christophe):** Clients see one **409** for not-found / PENDING / terminal so sequential auth-attempt ids do not reveal existence or state before caller verification. Server logs keep the distinction (`Respond refused: … not found` / `still PENDING` / `status …`). No client **404** or **400** `auth-attempt-respond-before-read` on this path.

**Honesty (Christophe m2):** There is **no** “refuse to start without an integration signing key” check. Integration Ed25519 keys live on each enrollment row. The protection is **runtime 503** + transaction rollback when signing cannot run.

**`markAsInvalid` before 503 (Patrick nit):** Device-signature / challenge failures call `AuthAttemptTxHelper.markAsInvalid` with `REQUIRES_NEW` **before** building the signed FAILED body. If signing then fails (blank integration key) → **503**, but **INVALID is already committed**. A client retry therefore sees terminal status → **409** `auth-attempt-state-conflict`, not another 503.

Not-found / PENDING / terminal / signing-unavailable must **not** be swallowed by the `AuthAttemptRequestFailedException` catch that builds domain FAILED.

### Mobile

- Unsigned or badly signed HTTP **200** (or missing local integration public key) → non-retryable Back-only with catalog **`resultNotConfirmed`** (« Résultat non confirmé » / EN equivalent) — not `requestNoLongerActive`, because the server may already have recorded the decision (Patrick).
- Problem+json: map by `type` to local catalog; never show server `detail`. Unified **409** `auth-attempt-state-conflict` → `requestNoLongerActive` (Back only). A **409 of another type** stays retryable. No distinct client keys for respond-before-read or respond not-found.
- Comment: `HTTP OK != decision committed`.

### Patrick factual correction

Inventory §2 (pre-fix) incorrectly said attempt not-found → **400** problem+json. **Wrong:** not-found was thrown as `AuthAttemptRequestFailedException` **inside** the swallowing try/catch → **unsigned 200 FAILED** (same bug class as cancel→EXPIRED). Fix path: out of the catch; **m1** maps not-found to unified **409** (not a client-visible 404).

Also: the comment at the catch (`Domain FAILED response (integration-signed)`) was false for the cancel / not-found paths where `authAttempt` stayed null — corrected.

---

## 1. Root cause (pre-fix)

### Observed symptom

After Admin UI **Annuler** while the phone is on `PendingAuthScreen` in `Ready`, Approve showed FR « Signature d'intégration manquante… » + **Réessayer** (`GlobalError` retryable) instead of **`NoLongerActive`** (Back only).

### Causal chain

1. Admin cancel → attempt `EXPIRED`.
2. Phone Approve → respond.
3. Status gate threw `AuthAttemptRequestFailedException` for non-READ **inside** `validateAndGetAttempt` before assignment → `authAttempt` stayed `null` in `respond`.
4. Catch → `buildFailedResponse` with null attempt → **unsigned HTTP 200 FAILED**.
5. Mobile signature gate → retryable `GlobalError`; 409 → `NoLongerActive` never reached.

---

## 2. Inventory (post-fix, after Marc m1)

| Server outcome | Wire | Signed? | Mobile |
| --- | --- | --- | --- |
| Approve/deny success | 200 APPROVED/DENIED | **Yes** | Verify → Completed |
| Domain FAILED (bad device sig / challenge) | 200 FAILED | **Yes** | Verify → recent failed |
| Not found / PENDING / terminal / cancel / supersession / TTL | **409** state-conflict | problem+json | `NoLongerActive` (Back only) |
| Missing integration key | **503** | problem+json | local catalog, retryable |
| Unsigned / bad-sig 200 (MITM / bug) | 200 without valid sig | N/A | neutral Back-only, non-retryable |

---

## 3. Threat model (unchanged conclusion)

Forging unsigned errors cannot force APPROVED/DENIED (those require integration signatures). Worst case ≈ DoS already possible by cutting traffic. Unified 409 additionally avoids revealing existence/state of sequential attempt ids before verification. See `docs/SECURITY_POSTURE.md` (#844 bullets).

---

## 4. Options considered (historical)

| # | Option | Outcome |
| --- | --- | --- |
| A (broad: all non-READ → 409) | Initially too broad for PENDING — first Marc decision kept PENDING → 400 distinct type |
| B (sign FAILED for cancel) | Rejected — wrong semantics for MOB-AUTH-007 |
| C alone (mobile-only) | Insufficient as primary fix |
| E (sign problem+json) | Rejected — disproportionate |
| **Marc 2026-10-15** | Terminal → 409; PENDING → 400; not-found → 404; missing key → 503; signed FAILED kept; mobile unsigned 200 → Back-only; local catalog only | Superseded for wire codes by m1 |
| **Marc m1 (2026-10-16)** | Not-found / PENDING / terminal → single client **409**; 503 key missing unchanged; logs keep distinction | **Current** |

---

## References

| Artifact | Role |
| --- | --- |
| [#844](https://github.com/mgagp/ezkey/issues/844) | Smoke failure MOB-AUTH-007 |
| [#833](https://github.com/mgagp/ezkey/pull/833) | Delivery PR (passwordless cancel + this fix) |
| `AuthAttemptRespondService.java` | Status gates + mandatory signing; unified 409 (m1) |
| `AuthApiProblemCatalog.java` | `auth-attempt-state-conflict` + `integration-signing-unavailable` |
| `authApiProblem.ts` / `usePendingAuth.ts` | Local catalog; unsigned 200 → Back-only |
| `docs/ENDPOINT.md` / `docs/SECURITY_POSTURE.md` | Contract + posture memory |
