<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Ezkey Mobile — Strategic Orientation (from FSM open questions)

## Context and method

The canonical FSM in [MOBILE_APP_STATE_MACHINE.md](MOBILE_APP_STATE_MACHINE.md) (issue
[#834](https://github.com/mgagp/ezkey/issues/834), PR [#835](https://github.com/mgagp/ezkey/pull/835))
surfaced **open questions**: places where code behavior exists but product intent / UX / security
posture was never made explicit. Those gaps are not “bugs by default”; they are **hidden decisions**.

This document explores each finding, names the underlying signal, and proposes a **proportionate,
phased** continuous-improvement backlog. It does **not** open GitHub issues yet and does **not**
change product code. Collaborator reviews @ `4ccecdf3` (Alex, Julie, Christophe, Patrick) are
integrated below; where reviewers **disagree**, options sit side by side with **no pick**.

| Baseline | Notes |
| --- | --- |
| Language | English — matches `ezkey_mobile/docs/` convention. |
| Code truth | **Branch of PR [#833](https://github.com/mgagp/ezkey/pull/833) @ `fcc31fbf`** (not yet an ancestor of `main`). Line citations for leave-as-deny / race guards are from that tip. |
| Phase 0 gate | **All Phase 0 mobile behavior items are conditional on #833 merging** (otherwise Findings 3–5 line numbers and leave semantics do not apply on `main`). |
| Closed-test link | Phase 0 honesty fixes (expired copy, leave-deny toast/hint, F3/F6 copy) are proposed as **prerequisites before widening the closed test** under the [#711](https://github.com/mgagp/ezkey/issues/711) gate (Northwind / next Play testers) — Alex. |
| Method | Observed behavior (file:line) → signal → impact → options → recommendation (or open disagreement). |
| FSM nature | The FSM is **descriptive of current hooks/flags**, not an implemented state machine in code (Patrick). |
| Repo-as-memory | Once Marc **decides**, behavior locks land in `MOBILE_APP_STATE_MACHINE.md` + `MOBILE_FUNCTIONAL_FLOWS.md`; 409 / pending `expiresAt` wire semantics in `docs/ENDPOINT.md`. **Undecided items are not recorded as decisions.** |

---

## Finding 1 — No mobile TTL countdown

### Observed behavior

Server attempts carry `expiresAt` / TTL (`ezkey.core.auth-attempt.ttl-seconds`, default 120 s). The
Auth API **pending response DTO used by mobile does not expose `expiresAt`**
(`AuthAttemptPendingResponseDto`). Mobile `PendingAttempt` has no expiry
(`types.ts` L1–11); claim cannot copy what the wire does not send (`claimPendingAttempt.ts`
L191–204). UI has no timer.

**Clock / trust (Patrick):** comparing an absolute `expiresAt` to the phone’s `Date.now()` is wrong
by clock skew. Prefer `expiresInSeconds` relative to the claim response, or `expiresAt` +
`serverNow`. That field is **not** covered by the integration pending signature
(`buildPendingPayload` / verify at `claimPendingAttempt.ts` L153, L172–176) → **display-only**, never
a cryptographic gate. Disabling Approve at local “0” inherits that unsigned clock.

### Underlying signal

Missing urgency feedback is a **gap** for late approvers (Alex: not “calm UX by design”). Admin
login already exposes attempt `expiresAt`; the phone does not.

### User impact

Approve after server expiry → opaque error. Without a terminal expired state, a timer alone is
half-honest (Julie).

### Options

| Option | Trade-off |
| --- | --- |
| A. Status quo | Soft-fails after TTL |
| B. Light remaining-time line + terminal expired | Needs Auth API field; clock skew design |
| C. Phase 0 expired **message** on post-TTL respond (no new contract), then B in Phase 1 | Alex; honesty before wire change |

### Recommendation

**Phase 0 — C** (clear “This request expired…” when respond fails for expiry / conflict), without
waiting for contract. **Phase 1 — B** split into two backlog items: (1) Auth API expose relative
expiry on pending, (2) mobile map + sober line (“Expires in about 2 min”, amber under ~30 s) **and**
terminal disable Approve with “This request expired. Ask for a new sign-in.” in the **same** mobile
delivery (Julie). Do not ship the timer without the terminal copy.

**Open (no pick):** Julie prefers approximate duration always; Marc Q asks whether to show nothing
until near expiry — see Questions for Marc.

---

## Finding 2 — `PendingAttempt.createdAt` is local claim time

### Observed behavior

`createdAt: checkedAt` (`claimPendingAttempt.ts` L107, L201) is local claim time, not server
attempt `createdAt`.

### Recommendation

**Phase 0 — rename to `checkedAt`** (before any TTL UI). Uncontroversial across reviews.

---

## Finding 3 — `serverDecisionSentRef` after respond HTTP 200, before result signature verify

### Observed behavior (#833 @ `fcc31fbf`)

After `authAttemptsApi.respond` returns HTTP 200, code sets `serverDecisionSentRef.current = true`
(`usePendingAuth.ts` L448–449), **then** verifies the integration-signed respond-result (L451–482).
On missing/invalid signature: `globalError`, stay on screen. Leave will not send a second deny.

**Correction (Christophe):** HTTP 200 does **not** mean “decision committed as APPROVED/DENIED.”
A 200 may carry a signed business **`FAILED`** (`AuthAttemptRespondService.java` L135–138); some
failures leave the row still `READ` (e.g. attempt not in READ / modeling as
`AuthAttemptRequestFailedException`). Prefer: **decision processed** with outcome
**`APPROVED` | `DENIED` | `FAILED`** (and 409 for true state conflicts).

**Nit (Christophe):** leave-as-deny that reaches verify-fail keeps the user on screen
(`PendingAuthScreen.tsx` L101) — document.

**Safe (Christophe):** setting the ref before local verify is safe; a forged/replayed body cannot
change server state; server rejects a second decision. **A+C validated; B rejected** (all
reviewers).

### Options (copy — aligned direction, wording differs)

| Option | Wording lean |
| --- | --- |
| Alex | “Decision sent. Confirmation could not be verified.” Never show APPROVED/DENIED. |
| Julie | “Your response was sent, but its confirmation couldn't be verified. Check your sign-in on the other device.” Hide Approve/Deny; show Back only. |

**Open (no pick):** whether to point the user at “the other device” given MFA cards intentionally omit
tenant names (Julie → Marc).

### Recommendation

**Phase 0 — A + C** (document intent; dedicated copy; hide actions). **Never B** (move ref after
verify — unsafe with leave-as-deny).

---

## Finding 4 — Leave allowed while respond is in flight

### Observed behavior

`beforeRemove` allows leave when `isRespondInFlight()` / `isProcessing` (`PendingAuthScreen.tsx`
L86–92). In-flight success may still call `handleReturnToEnrollmentDetail` (L503) → **second
`goBack`**, potentially popping Detail, plus setState on an unmounted hook (Patrick L234–240 /
L511–512). Not “usually benign.”

### Options

| Option | Who | Phase lean |
| --- | --- | --- |
| A. Keep allow-leave; document | — | — |
| C. Suppress post-success navigate if screen unfocused / unmounted | Julie (Walk proof), Patrick (Phase 0, ~3 lines) | **Disagreement on phase** |

**Note:** Patrick marks this (and Findings 9 lock-reset / uncertain-decision handling below) as
**being fixed in #833 itself** — track there; do not duplicate a competing mobile PR until #833
lands.

### Recommendation

Document now. Prefer Option C once #833’s fix set is clear; do not re-pick phase here.

---

## Finding 5 — Leave after deny network failure still exits

### Observed behavior

Failed leave-deny still dispatches leave (`PendingAuthScreen.tsx` L98–103). Attempt may stay `READ`
until TTL. Christophe: TTL 120 s (`EzkeyCoreProperties`); conditional update is idempotent → auto
retry of deny is safe; pull model avoids push fatigue.

### Options (disagreement on phase / retry)

| Option | Who |
| --- | --- |
| Toast then leave (no auto-retry), Phase 0 | Alex, Julie — copy: “Couldn't send your refusal. The request will expire on its own.” Neutral toast. |
| One auto-retry then toast, Phase 1 | Christophe |

**No pick** between Phase 0 toast-only and Phase 1 retry+toast.

### Recommendation

Fail-open leave stays. Ship **some** honesty toast before closed-test widen (#711). Exact retry
policy awaits Marc if Christophe’s retry is desired.

---

## Finding 6 — Fail-closed pending clears local attempt; server may stay `READ`

### Observed behavior

Bad pending sig → `fail_closed`, no local `attempt` (`claimPendingAttempt.ts` L183–188). Leave
without attempt does not deny. Christophe: no other channel can approve (device Ed25519 required);
TTL enforced on respond; newer attempt supersedes. **TTL now; device-proof cancel only in Phase 2;
never unsigned deny.**

### Recommendation

**Phase 0 — document + calmer copy** (Julie): explain it wasn’t shown, will expire, contact admin if
it repeats — no “signature” jargon in production copy. Alex: ask Christophe whether an audit event
is warranted (open). Phase 2 protocol only on real incidents.

---

## Finding 7 — Sibling docs still describe pre-#833 leave → TTL

### Recommendation

**Phase 0 after #833 merges:** cut over `MOBILE_FUNCTIONAL_FLOWS` / screens; remove “pending #833”
hedges. Already partly bannered in #835.

---

## Finding 8 — Auth API 409 state conflict copy

### Observed behavior

`TYPE_AUTH_ATTEMPT_STATE_CONFLICT` already exists
(`AuthApiProblemCatalog.java` L35–36 → `…/auth-attempt-state-conflict`). Mobile
`userFacingAuthApiError` does not map that `type` to friendly i18n (`authApiProblem.ts` L75–81).
Today cancelled / expired / already-decided collapse to one conflict (Patrick — say if intentional
once Marc decides; land semantics in `docs/ENDPOINT.md`).

### Recommendation

**Phase 0** (not Phase 1): map **problem `type`**, not raw HTTP 409. Julie’s proposed single user
string: “This request is no longer active. No action needed.” → Back to Detail. Prefer this **before**
the timer so expiry isn’t opaque while `expiresAt` is still missing from pending.

**Open (no pick):** one user-facing string for all causes (Julie) vs distinct problem `type`s per
cause (Patrick → Marc).

---

## Finding 9 — New attempt on same screen after “decision sent” lock (Patrick)

### Observed behavior

`serverDecisionSentRef` is set true at respond HTTP 200 (`usePendingAuth.ts` L449) and **never
reset**. `loadPendingAttempt` (L246) does not clear it. Path: Finding 3 (HTTP 200, sig fail →
`globalError`) → Try again → new attempt loaded → Approve/Deny blocked by guard L381; leave no
longer denies (L89). Lock is **screen-scoped**, not `authAttemptId`-scoped.

### Recommendation

**Phase 0 / #833:** key the lock per `authAttemptId` (or reset on successful new claim). Patrick:
items 2–4 of his review (this lock, double goBack, 409 mapping) are **being fixed in #833** — verify
on that PR rather than opening a parallel fix here.

---

## Finding 10 — Uncertain decision / lost response after server processing (Patrick + Christophe)

### Observed behavior

Network cut **after** server processed respond → mobile `catch` (L504–509), ref may stay false →
retry Approve or leave-deny → **409** while decision already exists. Christophe (medium before GA):
approve **ACCEPTED** on server but response lost → retry/leave-deny gets signed **FAILED** / conflict
UI saying “failure” while a session may already be open.

### Recommendation

**Document now** as alpha limit. FSM should name an **uncertain / NoLongerActive** outcome when 409
follows an in-flight respond. Before GA: signed respond body should carry the **real final status**
when re-queried or on conflict (Christophe → Marc). Do not treat 409 after uncertain respond as a
blind “try again.”

---

## Finding 11 — Biometric on leave-as-deny / deny (Christophe)

### Observed behavior

Leave-as-deny calls `handleRespond(false)`, which may open BiometricPrompt when
`requiresProtectedApproval` (`usePendingAuth.ts` L423–435; `approvalRequirement.ts` L42–45 applies to
approve **or** deny). Cancel prompt → `READ` until TTL. Christophe: biometric on **deny** has little
security value (Level 2); recommend signing deny **without** protected prompt.

### Recommendation

Document current behavior. Product/security decision for Marc (Christophe reco: no biometric on
deny). Implementation only after GO — may land with #833 or a follow-up.

---

## Finding 12 — Process death / background during respond; TTL during biometric (Christophe + Patrick)

### Observed behavior

App killed or backgrounded during in-flight respond: `beforeRemove` may **not** run → no leave-deny
→ `READ` until TTL. TTL can expire while biometric UI is open → later respond → 409. Deep links not
verified.

### Recommendation

Document as **known limits** (no code in Phase 0). Optional later: resume handling — not a protocol
change.

---

## Cross-cutting themes

1. **Processed vs displayed** — HTTP 200 means a respond **outcome** (`APPROVED`/`DENIED`/`FAILED`),
   not “safe to show APPROVED.”
2. **Don’t trap the human vs unblock waiters** — leave-as-deny + fail-open leave; honesty toasts/hints.
3. **Unsigned clocks are UX only** — pending expiry display must not become a crypto gate.
4. **Descriptive FSM** — flags/refs today; `NoLongerActive` as a **product state**, not a new library.
5. **#833 is the behavior train** — Phase 0 honesty + Patrick’s lock/nav/409 fixes ride that merge;
   docs cutover follows.
6. **Closed-test honesty (#711)** — Phase 0 copy/hint/toast before widening testers.

### Proposed product state: `NoLongerActive` (Julie)

Neutral terminal (expired / cancelled / already decided / conflict after act): **Back only**, not
retryable like `GlobalError`. Distinct in the FSM diagram and UI.

---

## Reviewer disagreements (no pick)

| Topic | Side A | Side B |
| --- | --- | --- |
| F5 honesty | Alex/Julie: Phase 0 toast, then leave | Christophe: Phase 1 one auto-retry + toast |
| Leave-as-deny visibility | Alex: visible announce “Leaving declines” | Julie: discreet hint under buttons (no dialog) — same direction, different intensity |
| 409 user copy | Julie: one “no longer active” string | Patrick: ask Marc whether to split cancelled / expired / already-decided `type`s |
| Timer presentation | Julie: always approximate + amber &lt;30s | Marc Q (via Julie): nothing until near expiry? |
| F4 double goBack phase | Patrick: Phase 0 (and fixing in #833) | Julie: Phase 1 on Walk proof |
| Disable Approve at 0 | Julie: same Phase 1 delivery as timer | Patrick: call out dependence on unsigned clock |

---

## Phased roadmap

**Phase 0 = after (or in) #833 merge.** Items marked “in #833” should not spawn duplicate issues.

| Item | Phase | Effort | Risk | Dependency | Owner zone | Proposed backlog title (not opened) |
| --- | --- | --- | --- | --- | --- | --- |
| Lock Finding 3 language + post-verify-fail copy; hide Approve/Deny | 0 | S | Low | #833 | mobile + docs | Mobile: copy when respond processed but result sig fails |
| Map `TYPE_AUTH_ATTEMPT_STATE_CONFLICT` → friendly i18n (+ `NoLongerActive`) | 0 | S | Low | type already in Auth API | mobile | Mobile: map auth-attempt-state-conflict to NoLongerActive copy |
| Phase 0 expired/conflict honesty without pending `expiresAt` | 0 | S | Low | #833; #711 widen | mobile | Mobile: clear expired/inactive copy before closed-test widen |
| Toast (and/or one retry — TBD) on failed leave-deny | 0 | S | Low | #833; see disagreement | mobile | Mobile: honesty when leave-as-deny fails |
| Leave-as-deny visible hint under actions | 0 | S | Low | #833; #711 | mobile | Mobile: “Leaving declines this request” hint |
| Fail-closed pending calmer copy | 0 | S | Low | — | mobile | Mobile: fail-closed pending copy without crypto jargon |
| Rename `createdAt` → `checkedAt` | 0 | S | Low | — | mobile | Mobile: PendingAttempt.checkedAt |
| Sibling doc cutover leave-as-deny | 0 | S | Low | #833 merge | docs | Docs: flows/screens match leave-as-deny |
| Document stranded READ / process death / biometric-on-deny limits | 0 | S | Low | — | docs | Docs: alpha limits — READ until TTL paths |
| Reset decision lock per `authAttemptId` | 0 | S | Med | **fixing in #833** (Patrick) | mobile | (track #833 — do not duplicate) |
| Suppress goBack if unmounted / unfocused | 0 | S | Med | **fixing in #833** (Patrick) | mobile | (track #833 — do not duplicate) |
| Auth API: pending relative expiry (`expiresInSeconds` or `expiresAt`+`serverNow`) | 1 | M | Med | contract refresh | auth-api | Auth API: expose pending attempt remaining lifetime on pending |
| Mobile: map expiry + approximate line + terminal expired UI | 1 | M | Med | Auth API item above | mobile | Mobile: remaining-time line and expired terminal on Pending Auth |
| Lost-response / uncertain decision UX (409 after act → NoLongerActive) | 1 | M | Med | #833; Marc on GA bar | mobile (+ auth-api if status echo) | Mobile: treat post-respond conflict as already decided |
| Deny without biometric when protected mode on | 1–2 | S | Med | Marc + Christophe GO | mobile | Mobile: unprotected deny / leave-as-deny signing |
| Abandon claimed attempt after bad pending sig | 2 | L | High | security design | auth-api + mobile | Auth/mobile: release READ without trusted pending UI |
| Signed final status on re-query after lost respond | 2 | L | High | Christophe GA bar | auth-api | Auth API: convey real final status after uncertain client |

---

## Questions for Marc

1. Is missing TTL UI an oversight to fix (Alex), and should Phase 0 ship expired/inactive copy before any wire `expiresAt`? (Alex, Julie)
2. Confirm Finding 3: after respond is **processed**, phone may show verify-fail without undoing server outcome — and never choose “ref after verify”? (all)
3. Fail-open leave after deny failure: toast-only Phase 0 (Alex/Julie) or one auto-retry then toast Phase 1 (Christophe)?
4. Stranded `READ` after fail-closed pending: TTL-only alpha OK, or P1 protocol? Audit event needed? (Alex, Christophe)
5. Leave-as-deny: visible announce vs discreet under-button hint — which intensity? (Alex Q8, Julie c)
6. Are Phase 0 honesty fixes (expired copy, leave hint, F3/F6/F5 toast) **prerequisites** before widening closed test (#711)? (Alex Q9)
7. Timer presentation: always “about N min” + amber under 30s, or silent until near expiry? (Julie a)
8. One user string “no longer active” for expired/cancelled/already-decided, or distinct problem `type`s? (Julie b, Patrick)
9. After F3 verify-fail, may copy point to “the other device” even though cards omit tenant names? (Julie d)
10. Alpha: lost approve response shown as failure acceptable, or signed real final status required before GA? (Christophe a)
11. Should deny / leave-as-deny require biometric when protected mode is on? Christophe reco: **no**. (Christophe b)
12. Is the 120s `READ` window after fail-closed / abandoned leave an accepted alpha limit without protocol until real incidents? (Christophe c)
13. Must the “decision processed” lock be per `authAttemptId`, and should 409 after an uncertain respond display as “already decided / check other device”? (Patrick; also Tracking in #833)
14. Pending expiry on the wire: accept **unsigned display-only** (`expiresInSeconds` / `serverNow`), or require it inside the signed pending payload (Christophe impact)? (Patrick)

---

## Revues des collaborateurs

### Alex (product intent)

Agrees with restraint: Phase 0 docs/rename/copy before protocol; Phase 2 behind Marc + Christophe GO.
Partial disagreement on Finding 1: no timer is a **gap**, not calm UX — keep option B but ship a clear
**expired** message in Phase 0 without contract, then `expiresAt` in Phase 1. Finding 3: A+C, never B;
copy must not show APPROVED/DENIED. Finding 5: fail-open + toast in Phase 0. Missed signal:
leave-as-deny is invisible — Q5 is the most important; announce “Leaving declines the request.”
Finding 6: document for alpha; copy must say what to do (contact admin); ask about audit. Finding 8:
Phase 1 B originally — **superseded by Patrick**: type already stable → Phase 0 mapping. Phasing:
tie honesty fixes to Northwind / closed Play test widen (**#711**). Repo-as-memory: each Marc answer
→ one line with reason + #834 before opening issues.

### Julie (operability / UX)

Compass: Ezkey is never the adopter’s core business; 3-second test; MFA cards must not reintroduce
tenant names. Finding 1: option B without per-second countdown — approximate “Expires in about 2
min,” amber “Expires soon” under 30s; **terminal expired state in the same Phase 1 delivery** as the
timer (move disable-Approve-at-0 up from Phase 2). Finding 8: real case is Approve after admin cancel
or TTL — needs one “no longer active” string + Back; put **before** the timer. Finding 5: don’t trap
the user, but silent leave lies — toast Phase 0 (not “0–1”); verify Admin wait expiry copy on Walk.
Finding 3: most important UX point — “sent but confirmation unverified” + hide Approve/Deny, Back
only. Finding 6: calmer fail-closed copy, no signature jargon. Leave-as-deny: **discreet hint** under
buttons, no confirmation dialog. Finding 4: Option C on device Walk proof. Avoid shipping timer
without 409/expired copy. FSM should add neutral terminal **`NoLongerActive`** distinct from
retryable `GlobalError`.

### Christophe (security) — minor

Code read @ `fcc31fbf`. Docs: “HTTP OK = decision committed” is **false** — reformulate to
**processed (`APPROVED`/`DENIED`/`FAILED`)**. `serverDecisionSentRef` before verify is safe; A+C
OK, B rejected; document leave-as-deny path that stays on screen after verify fail. **Before GA:**
leave/deny may open BiometricPrompt — little security value on deny; recommend unprotected deny
signing. Stranded READ after bad pending sig: no alternate approve channel; TTL OK now; Phase 2
device-proof cancel only; never unsigned deny. Network-failed deny: idempotent update → retry safe;
option C Phase 1. **Medium before GA:** lost approve response can show failure while session exists —
document now; signed final status before GA. Flow trace OK if release-disabled and no proof tokens
logged; check `setRecentAuthResult` persistence. Add transitions: app killed during respond; TTL
during biometric; deep links unchecked. Questions: lost-response display for alpha; biometric on
deny (reco no); 120s READ as known alpha limit.

### Patrick (senior engineering) — minor (docs)

Analysis is sound; #833 line refs accurate. **Baseline wrong** if phrased as main+post-#833 —
pin #833 @ `fcc31fbf`; condition Phase 0 on merge. FSM missing: **new attempt on same screen**
(Finding 9 — lock never resets); **uncertain decision after lost response** (409 while decided).
Finding 4 underestimated — double `goBack` + setState after unmount; Option C is Phase 0 (~3 lines).
FSM is descriptive (scattered flags/refs), not implemented — say so; no FSM library. 409 type
**already exists** → Phase 0 map by `type`. `expiresAt` underspecified — relative clock;
unsigned/display-only; split Auth API vs mobile roadmap rows; resolve ambiguous “0–1” phases.
Repo-as-memory: behavior decisions belong in FSM + functional flows; 409/`expiresAt` semantics in
`docs/ENDPOINT.md` once Marc decides. **Items 2–4 of his review (lock per attempt, double goBack,
409 mapping) are being fixed in #833 itself** — do not duplicate.

---

## Provenance

- Parent FSM: [MOBILE_APP_STATE_MACHINE.md](MOBILE_APP_STATE_MACHINE.md)
- Issue [#834](https://github.com/mgagp/ezkey/issues/834) · PR [#835](https://github.com/mgagp/ezkey/pull/835)
- Behavior baseline: PR [#833](https://github.com/mgagp/ezkey/pull/833) @ `fcc31fbf`
- Collaborator reviews integrated: Alex, Julie, Christophe, Patrick @ docs tip `4ccecdf3`
- Decision (repo-as-memory): orientation holds options/phasage; **decided** behavior locks go to FSM
  + functional flows; **decided** 409 / pending expiry wire semantics go to `docs/ENDPOINT.md` —
  reason: keep undecided items out of canon; issue #834.
