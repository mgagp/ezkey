<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# #844 — Unsigned respond errors vs mobile `NoLongerActive` (analysis)

**Status:** analysis only — **no code fix in this change set** (Marc, 2026-10-15).  
**Triggers:** QA Sphere run EZK/14, case MOB-AUTH-007 on PR [#833](https://github.com/mgagp/ezkey/pull/833) @ `d8812820` / Pixel 7 Pro / Rootbeer.  
**Issue:** [#844](https://github.com/mgagp/ezkey/issues/844).  
**Vocabulary:** states and terms from `ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md` on branch `cursor/mobile-app-state-machine-docs-a4a8` (PR [#835](https://github.com/mgagp/ezkey/pull/835)) — especially `Ready`, `Responding`, `GlobalError`, `NoLongerActive`, `UncertainDecision`, `Completed`, and refs `serverDecisionAttemptIdRef` / `respondInFlightRef`.

---

## 1. Root cause (file:line)

### Observed symptom

After Admin UI **Annuler** (passwordless cancel) while the phone is on `PendingAuthScreen` in `Ready`, Approve shows:

- FR: « Signature d'intégration manquante sur la réponse respond » + **Réessayer**
- i.e. mobile UI state **`GlobalError`** (retryable), not the #833 acceptance state **`NoLongerActive`** (Back only).

### Causal chain

1. **Admin cancel** marks the attempt `EXPIRED` via conditional update (`AuthAttemptService.cancel` → `expireIfPendingOrRead(PENDING|READ → EXPIRED)` — see `AuthAttemptService.java` ~492–503). Phone still holds a `PendingAttempt` in memory (`Ready`).

2. **Phone Approve** → `handleRespond(true)` → `POST /api/v1/auth-attempts/respond` (`Responding`).

3. **Auth API status gate uses the wrong exception type for “already cancelled”:**

```160:162:ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java
    if (authAttempt.getAuthAttemptStatus() != AuthAttemptStatus.READ) {
      throw new AuthAttemptRequestFailedException("Auth attempt not read by device");
    }
```

   Contrast: TTL wall-clock expiry in the **same method** already throws `AuthAttemptStateConflictException` (~178–179), and cancel-win on `updateStatusIfCurrent` also throws state conflict. Status `EXPIRED` from Admin cancel is inconsistently classified as “request failed.”

4. **Assignment never completes, so the FAILED body is unsigned:**

```115:138:ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java
  public AuthAttemptRespondResponse respond(AuthAttemptRespondRequest request) {
    AuthAttempt authAttempt = null;
    try {
      authAttempt = validateAndGetAttempt(request);
      // ...
    } catch (AuthAttemptRequestFailedException ignored) {
      return buildFailedResponse(request, authAttempt);
    }
  }
```

   The throw is **inside** `validateAndGetAttempt`, before `return authAttempt`. In Java the left-hand assignment does not run → `authAttempt` stays `null` in `respond`.

5. **`buildFailedResponse` with null attempt skips signing** (`AuthAttemptRespondService.java` ~355–372): sets `authAttemptId` from the request only; no enrollment load → no `attachRespondResultSignature`. Controller returns **HTTP 200** with `authAttemptResult=FAILED` and **empty** `authAttemptProofTokenResultSignedByIntegration`.

   Note: `AuthAttemptRequestFailedException` *would* map to RFC 9457 **400** `problem+json` if it escaped the service (`GlobalExceptionHandler.java` ~171–180). Respond **swallows** it into a domain FAILED 200 instead. Pending paths that throw the same exception do reach 400 problem+json.

6. **Mobile treats HTTP 200 as a completed respond exchange**, then fails the result-signature gate before any problem-type mapping:

   - Sets `serverDecisionAttemptIdRef` on HTTP 200 (`usePendingAuth.ts` ~489–490) — “HTTP layer completed,” not “APPROVED committed” (FSM note on `serverDecisionSentRef` / attempt-id lock).
   - Empty signature → `t('pendingAuth.missingRespondSignature')` → **`GlobalError`** with `globalErrorRetryable=true` (~501–510).
   - The **`NoLongerActive`** path (`resolveAuthApiUserFacingOutcome` for `TYPE_AUTH_ATTEMPT_STATE_CONFLICT`) only runs in the **`catch`** of a non-2xx problem body — never reached for this 200.

**One-line root cause:** Admin cancel → `EXPIRED` → respond throws `AuthAttemptRequestFailedException` before assignment → unsigned HTTP 200 `FAILED` → mobile signature check → retryable `GlobalError` instead of 409 → `NoLongerActive`.

---

## 2. Inventory — signed vs unsigned outcomes (Auth API ↔ mobile)

Terms: **signed** = integration Ed25519 over the canonical pending or respond-result payload. **Unsigned** = no integration signature field (or empty). Problem+json bodies are never signed today.

### `POST /api/v1/auth-attempts/pending` (claim)

| Server outcome | Wire | Signed? | Mobile effect (FSM) |
| --- | --- | --- | --- |
| Claim success (`PENDING`→`READ`) | 200 + pending DTO | **Yes** (`authAttemptProofTokenSignedByIntegration`) | Verify → `{kind:'attempt'}` → navigate / `Ready` |
| No pending | 204 / empty | N/A | `{kind:'none'}` → Detail feedback / `Empty` |
| Missing/invalid device sig, bad enrollment, etc. | Often `AuthAttemptRequestFailedException` → **400** `problem+json` (`TYPE_AUTH_ATTEMPT_BINDING_FAILED`) | **Unsigned** | `fail_closed` / `GlobalError` / Detail feedback via `userFacingAuthApiError` (retryable unless mapped) |
| Malformed / missing integration key on client | Client-side | N/A | `fail_closed` (never enter `Ready`) |

Pending success is fail-closed on bad signature (`claimPendingAttempt.ts`). Unsigned 4xx problems are display/attribution only — they do not create a local `PendingAttempt`.

### `POST /api/v1/auth-attempts/respond` (approve / deny / leave-as-deny)

| Server outcome | Wire | Signed? | Mobile effect (FSM) |
| --- | --- | --- | --- |
| Approve/deny success | 200 `APPROVED` / `DENIED` | **Yes** (respond-result payload) | Verify → `Completed` + `setRecentAuthResult` + navigate |
| Domain validation fail after attempt loaded (bad device sig, challenge mismatch, …) | 200 `FAILED` via `buildFailedResponse` with non-null attempt | **Yes** (when enrollment + integration private key present) | Verify → treat as terminal business `FAILED` → recent “failed” + navigate (`Completed`-like) |
| **Non-READ status (cancel → `EXPIRED`, already final, …)** — **today** | 200 `FAILED` via catch with **`authAttempt == null`** | **No** (unsigned) | Missing signature → **`GlobalError`** + Try again (**MOB-AUTH-007 / #844**) |
| Concurrent cancel after READ validated (`updateStatusIfCurrent` 0 rows) | **409** `TYPE_AUTH_ATTEMPT_STATE_CONFLICT` | **Unsigned** problem+json | `catch` → **`NoLongerActive`** (Back only) on #833 tip |
| Superseded / wall-clock `expiresAt` past | **409** state conflict | **Unsigned** problem+json | Same → `NoLongerActive` |
| Not found / other escaped `AuthAttemptRequestFailedException` | **400** problem+json (handler) | **Unsigned** | Retryable `GlobalError` (generic / catalog detail) |
| Other 4xx/5xx / network | problem+json or transport error | **Unsigned** | `UncertainDecision` / `GlobalError`; may retry → often 409 → `NoLongerActive` |

### Ordering note (mobile)

On HTTP **200**, mobile **always** runs result-signature checks before interpreting `APPROVED`/`DENIED`/`FAILED`. Problem-type mapping (`authApiProblem.ts`) applies only to **thrown** HTTP errors. So an unsigned 200 can never become `NoLongerActive` with the current ordering — by design for real decisions, accidentally harmful for this cancel path.

---

## 3. Threat model — unsigned error as display surface

Assumption: attacker can MITM or strip/forge **unsigned** HTTP bodies between Auth API and phone (TLS compromise, hostile proxy, buggy CDN). Device keys and integration verify for **signed** pending/respond-result bodies remain intact.

| Forged / stripped unsigned body | What phone can display or do | What it **cannot** do |
| --- | --- | --- |
| Unsigned 200 `FAILED` (empty sig) | `GlobalError` “missing signature” + Try again; may set attempt-id decision lock after HTTP 200 | Cannot show trusted `Completed` APPROVED/DENIED without valid respond-result signature |
| Forged **409** `auth-attempt-state-conflict` | `NoLongerActive` (neutral Back only); user leaves without another respond | Cannot flip server decision; if attempt still `READ`, Admin wait continues until TTL / leave-deny — **availability / honesty**, not auth bypass |
| Forged 400 problem detail | Retryable `GlobalError` with catalog or fallback copy | Same — no signed decision |
| Strip a **real** signed 200 APPROVED | Missing/invalid sig → `GlobalError`; lock may already be set | User does not get a false “approved” card; server may already be `ACCEPTED` (`UncertainDecision` class) |

**Server already refuses a second decision** (`updateStatusIfCurrent` / final statuses). So the integrity risk of unsigned *errors* is primarily:

1. **UX honesty** — user believes the request is inactive or “broken signature” when the server story is “cancelled” or still `READ`.
2. **Liveness** — forging `NoLongerActive` could discourage a leave-as-deny while the attempt is still live (Admin waits until TTL) — same class as killing the app mid-screen.
3. **Not** “attacker forces APPROVED on phone or server” — that still requires integration (or device) signatures on the success path.

Signing every error would shrink (1)–(2) at the cost of a new signed surface and key-availability requirements on failure paths. Treating unsigned errors as **display-only, never as a decision** is already true for problem+json; the bug is that cancel is mis-shaped as an unsigned **200 decision body**.

---

## 4. Options (proportionate effort)

| # | Option | Effort | Pros | Cons |
| --- | --- | --- | --- | --- |
| **A** | **API: non-READ → `AuthAttemptStateConflictException` (409)** so cancel/finalized match TTL/supersession/update race | Small (service + unit/controller tests) | Lands in existing mobile `NoLongerActive` mapping; no crypto change; aligns exception taxonomy | Does not by itself fix other unsigned-200 FAILED edge cases if `authAttempt` stays null |
| **B** | **API: re-load attempt in `buildFailedResponse` when null** so swallowed `RequestFailed` is signed FAILED 200 | Small–medium | Consistent signed FAILED for true validation errors | Cancel would still look like business `FAILED` + navigate “failed”, **not** neutral `NoLongerActive`; wrong product semantics for MOB-AUTH-007 |
| **C** | **Mobile: missing respond-result signature after HTTP 200 → `NoLongerActive` / Back-only** (display-only; do not treat as APPROVED/DENIED) | Small | Masks #844 without backend deploy | Over-broad: any unsigned/stripped 200 looks “inactive”; weakens distinction between crypto fail and cancel; still sets decision lock on 200 |
| **D** | **Mobile: only map known problem types / never treat unsigned 200 as decision success** (keep missing-sig as `UncertainDecision` with Back, not Try again) | Small | Better than retryable `GlobalError` for uncertain post-HTTP bodies | Still ambiguous copy; doesn’t teach Admin-cancel semantics |
| **E** | **Sign problem+json** (or signed error envelope) with integration key | Large | Authenticates error attribution end-to-end | New payload canon, key mandatory on all error paths, OpenAPI/clients, little gain vs A for cancel; overkill for public-alpha posture |
| **F** | **A + tighten mobile copy only** (ensure 409 → `NoLongerActive` covered by Jest cancel-then-respond) | Small | Matches #833 acceptance; tests lock the contract | Minimal — recommended core |

---

## 5. Recommendation

**Do not sign problem responses for this issue.** The goal is honest, non-retryable UX when the attempt is already gone — not a new crypto product surface.

**Prefer option A (+ F):** classify “respond when status ≠ `READ`” as **`AuthAttemptStateConflictException`** → HTTP **409** `TYPE_AUTH_ATTEMPT_STATE_CONFLICT`, same as TTL expiry and cancel-win races. Mobile on #833 already maps that to **`NoLongerActive`** (« Cette demande n'est plus active… » / EN equivalent, Back only). Add backend cancel-then-respond and Jest coverage when implementing.

**Optionally later (not required for #844):** if `buildFailedResponse` is kept for true validation failures, ensure `authAttempt` is non-null before signing (reload by id) so unsigned 200 FAILED becomes rare — still distinct from `NoLongerActive`.

**Avoid C as the primary fix:** treating every missing respond signature as inactive blurs crypto failures with lifecycle conflicts and is harder to reason about in the FSM (`GlobalError` vs `UncertainDecision` vs `NoLongerActive`).

**Implementation note (deferred):** a one-line exception-type change in `validateAndGetAttempt` plus tests is enough for MOB-AUTH-007; no OpenAPI field change; no weakening of signature checks on real `APPROVED`/`DENIED`/`FAILED` decisions.

---

## References

| Artifact | Role |
| --- | --- |
| [#844](https://github.com/mgagp/ezkey/issues/844) | Smoke failure MOB-AUTH-007 |
| [#833](https://github.com/mgagp/ezkey/pull/833) | Passwordless cancel + mobile `NoLongerActive` acceptance |
| PR #835 / `MOBILE_APP_STATE_MACHINE.md` | Canonical mobile FSM vocabulary |
| `AuthAttemptRespondService.java` | Respond status gate + FAILED builder |
| `GlobalExceptionHandler.java` | 400 vs 409 problem mapping |
| `authApiProblem.ts` / `usePendingAuth.ts` | 409 → `NoLongerActive`; 200 → signature first |
