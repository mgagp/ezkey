<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# #844 — Unsigned respond errors vs mobile `NoLongerActive`

**Status:** **decided and implemented** (Marc, 2026-10-15) on PR [#833](https://github.com/mgagp/ezkey/pull/833).  
**Triggers:** QA Sphere run EZK/14, case MOB-AUTH-007 on PR #833 @ `d8812820` / Pixel 7 Pro / Rootbeer.  
**Issue:** [#844](https://github.com/mgagp/ezkey/issues/844).  
**Review:** Patrick craft critique (2026-10-15) — factual correction below; Christophe guardrails folded into Marc's decision.  
**Vocabulary:** states and terms from `ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md` (PR [#835](https://github.com/mgagp/ezkey/pull/835)) — especially `Ready`, `Responding`, `GlobalError`, `NoLongerActive`, `UncertainDecision`, `Completed`.

---

## Final decision (Marc, 2026-10-15)

**Rule:** only decisions and final statuses are integration-signed. The server never emits an unsigned 200. Any unsigned response leads the app to a neutral Back-only state (no enrollment/key wipe, no automatic deny or approve retry). Local catalog strings only (EN/FR by `type`); never raw problem+json `detail`.

### Backend (`AuthAttemptRespondService`)

| Condition | HTTP | Catalog type |
| --- | --- | --- |
| Terminal (`ACCEPTED`, `REJECTED`/`DENIED`, `EXPIRED`, `INVALID`) | **409** | `auth-attempt-state-conflict` |
| Still `PENDING` (respond before claim) — protocol violation | **400** | `auth-attempt-respond-before-read` (kept distinct from 409 for diagnosis) |
| Attempt or enrollment not found | **404** | `resource-not-found` |
| Missing/blank integration private key (even for APPROVED/DENIED/FAILED) | **503** | `integration-signing-unavailable` — refuse to start an unsigned body (same fail-closed posture as encryption `required=true` refusing JVM start without a usable keyset) |
| Legitimate FAILED (invalid device signature, wrong challenge) | **200** signed `FAILED` | — non-regression tests required |

Not-found / PENDING / terminal / signing-unavailable must **not** be swallowed by the `AuthAttemptRequestFailedException` catch that builds domain FAILED.

### Mobile

- Unsigned or badly signed HTTP **200** → non-retryable → neutral Back-only (`requestNoLongerActive`).
- Problem+json: map by `type` to local catalog; never show server `detail`. A **409 of another type** stays retryable.
- Comment: `HTTP OK != decision committed`.

### Patrick factual correction

Inventory §2 (pre-fix) incorrectly said attempt not-found → **400** problem+json. **Wrong:** not-found was thrown as `AuthAttemptRequestFailedException` **inside** the swallowing try/catch → **unsigned 200 FAILED** (same bug class as cancel→EXPIRED). Fix: **404** problem, out of the catch.

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

## 2. Inventory (post-fix)

| Server outcome | Wire | Signed? | Mobile |
| --- | --- | --- | --- |
| Approve/deny success | 200 APPROVED/DENIED | **Yes** | Verify → Completed |
| Domain FAILED (bad device sig / challenge) | 200 FAILED | **Yes** | Verify → recent failed |
| Terminal / cancel / supersession / TTL | **409** state-conflict | problem+json | `NoLongerActive` |
| PENDING respond | **400** respond-before-read | problem+json | local catalog, retryable |
| Not found | **404** | problem+json | local catalog, retryable |
| Missing integration key | **503** | problem+json | local catalog, retryable |
| Unsigned / bad-sig 200 (MITM / bug) | 200 without valid sig | N/A | neutral Back-only, non-retryable |

---

## 3. Threat model (unchanged conclusion)

Forging unsigned errors cannot force APPROVED/DENIED (those require integration signatures). Worst case ≈ DoS already possible by cutting traffic. See `docs/SECURITY_POSTURE.md` (#844 bullet).

---

## 4. Options considered (historical)

| # | Option | Outcome |
| --- | --- | --- |
| A (broad: all non-READ → 409) | Too broad for PENDING — Marc: PENDING → 400 distinct type |
| B (sign FAILED for cancel) | Rejected — wrong semantics for MOB-AUTH-007 |
| C alone (mobile-only) | Insufficient as primary fix |
| E (sign problem+json) | Rejected — disproportionate |
| **Marc decision** | Terminal → 409; PENDING → 400; not-found → 404; missing key → 503; signed FAILED kept; mobile unsigned 200 → Back-only; local catalog only | **Implemented** |

---

## References

| Artifact | Role |
| --- | --- |
| [#844](https://github.com/mgagp/ezkey/issues/844) | Smoke failure MOB-AUTH-007 |
| [#833](https://github.com/mgagp/ezkey/pull/833) | Delivery PR (passwordless cancel + this fix) |
| `AuthAttemptRespondService.java` | Status gates + mandatory signing |
| `AuthApiProblemCatalog.java` | New types |
| `authApiProblem.ts` / `usePendingAuth.ts` | Local catalog; unsigned 200 → Back-only |
| `docs/ENDPOINT.md` / `docs/SECURITY_POSTURE.md` | Contract + posture memory |
