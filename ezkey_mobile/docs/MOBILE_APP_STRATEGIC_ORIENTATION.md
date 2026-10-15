<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Ezkey Mobile — Strategic Orientation (from FSM open questions)

## Context and method

The canonical FSM in [MOBILE_APP_STATE_MACHINE.md](MOBILE_APP_STATE_MACHINE.md) (issue
[#834](https://github.com/mgagp/ezkey/issues/834), PR [#835](https://github.com/mgagp/ezkey/pull/835))
surfaced **eight open questions**: places where code behavior exists but product intent / UX / security
posture was never made explicit. Those gaps are not “bugs by default”; they are **hidden decisions**.

This document explores each finding, names the underlying signal, and proposes a **proportionate,
phased** continuous-improvement backlog. It does **not** open GitHub issues yet and does **not**
change code.

| Baseline | Notes |
| --- | --- |
| Language | English — matches `ezkey_mobile/docs/` convention (strategy/editorial French lives elsewhere). |
| Code truth | Main + post-[#833](https://github.com/mgagp/ezkey/pull/833) leave-as-deny / race guards (`fcc31fbf`). |
| Method | Observed behavior (file:line) → signal → impact → options → recommendation. |
| Restraint | Prefer Phase 0 docs/copy and small mobile UX before protocol or backend redesign. |

Collaborator review slots (empty for later fill): [§ Revues des collaborateurs](#revues-des-collaborateurs).
Owner decisions: [§ Questions pour Marc](#questions-pour-marc).

---

## Finding 1 — No mobile TTL countdown

### Observed behavior

Server auth attempts carry `expiresAt` / TTL (`ezkey.core.auth-attempt.ttl-seconds`, default 120 s;
see `docs/ENDPOINT.md`). The Auth API **pending response DTO used by mobile does not expose
`expiresAt`** (`AuthAttemptPendingResponseDto` in generated model — fields stop at context
title/message). Mobile `PendingAttempt` likewise has no expiry
(`ezkey_mobile/app/services/pendingAuth/types.ts` L1–11); claim mapping cannot copy what the wire
does not send (`claimPendingAttempt.ts` L191–204). Pending Auth UI shows context + Approve/Deny
only — no timer.

### Underlying signal

Product lean toward **deliberate action**, not urgency theatre. Pull model already rejects background
polling; a countdown would be the first “clock anxiety” surface on the phone.

### User impact

User may start approving after server expiry → opaque failure. Admin waiters already know the clock;
the phone user does not.

### Security note

Countdown is UX, not a trust control. Hiding TTL does not strengthen crypto; it can increase
confused retries after expiry.

### Options

| Option | Trade-off |
| --- | --- |
| A. Keep no UI timer (status quo) | Simplest; soft-fails after TTL |
| B. Show remaining time only (no auto-dismiss) | Honest urgency; needs `expiresAt` on claim DTO path |
| C. Soft banner near expiry + disable Approve after clock | Clearer; more UI states |

### Recommendation

**Phase 1 — B (light):** first add `expiresAt` to Auth API pending response (contract +
`update-specs` + Orval), then map it on mobile and show a sober remaining-time line. No auto-leave.
Until the wire field exists, mobile cannot invent a trustworthy countdown from local clocks alone.

---

## Finding 2 — `PendingAttempt.createdAt` is local claim time

### Observed behavior

`createdAt: checkedAt` uses the local ISO timestamp from the claim call
(`claimPendingAttempt.ts` L107, L201), not the server attempt’s `createdAt`.

### Underlying signal

Field name implies server provenance; implementation is **local diagnostics / “when I checked”**.
Dangerous if later reused for TTL math (Finding 1).

### User impact

Today mostly invisible (not shown as “created”). Future countdown or audit copy would be wrong.

### Security note

Misleading timestamps in debug panels can waste incident time; not a crypto bypass.

### Options

| Option | Trade-off |
| --- | --- |
| A. Rename to `claimedAt` / `checkedAt` | Honest; small type + call-site churn |
| B. Keep name; document “local only” | Cheap; trap for future authors |
| C. Store both server `createdAt` and local `checkedAt` | Clearest; needs API field on pending response |

### Recommendation

**Phase 0 — A:** rename to `checkedAt` (or dual-name with deprecation comment). **Phase 1 — C** if
TTL UI needs server clock alignment.

---

## Finding 3 — `serverDecisionSentRef` after HTTP OK, before result signature verify

### Observed behavior (post-#833)

After `authAttemptsApi.respond` returns, code sets `serverDecisionSentRef.current = true`
(`usePendingAuth.ts` on #833 ≈ L448–449), **then** verifies the integration-signed respond-result
(L451–482). On missing/invalid signature: `globalError`, stay on screen, **no** navigation. Leave
will not send a second deny because the ref is already true.

### Underlying signal

Split between **server authority** (decision committed) and **display trust** (signed result for UI
summary). Fail-closed for *what we show*, fail-open for *not undoing a committed decision*.

### User impact

Rare path: user sees “invalid signature” while Admin wait may already be unblocked (approve/deny
done). Confusing but better than re-denying an approve.

### Security note

Correct not to flip a committed decision from the client. Display must not invent APPROVED/DENIED
without a verified result — current code already withholds `setRecentAuthResult` until verify
passes (L495+).

### Options

| Option | Trade-off |
| --- | --- |
| A. Keep (document as intentional) | Matches integrity of server commit |
| B. Move ref set to after verify | Leave could re-deny after HTTP OK — **unsafe** with leave-as-deny |
| C. Dedicated copy: “Decision sent; confirmation could not be verified” | Honest UX; no protocol change |

### Recommendation

**Phase 0 — A + C:** lock intent in FSM/docs; add specific i18n string for post-HTTP verify failure.
Do **not** choose B.

---

## Finding 4 — Leave allowed while respond is in flight

### Observed behavior (post-#833)

`beforeRemove` returns early (allows leave) when `isRespondInFlight()` or `isProcessing`
(`PendingAuthScreen.tsx` on #833 L86–92). No competing deny. In-flight `handleRespond` may still
complete and call `handleReturnToEnrollmentDetail()` (L503).

### Underlying signal

Priority: **never race leave-deny against approve**. Secondary: don’t trap the user on the screen
during network/biometric waits.

### User impact

User can leave mid-approve; navigation may pop twice or land oddly if goBack runs after leave.
Usually benign on a stack.

### Security note

Avoiding a second deny is the security-relevant part; double navigation is operability.

### Options

| Option | Trade-off |
| --- | --- |
| A. Keep allow-leave during in-flight | Matches #833 m2 intent |
| B. Block leave until respond settles | Safer nav; can feel sticky during biometric |
| C. Allow leave but suppress post-success navigate if screen unfocused | Cleaner nav; small hook change |

### Recommendation

**Phase 0 — A** (document). **Phase 1 — C** if Walk / device tests show confusing double pops.

---

## Finding 5 — Leave after deny network failure still exits

### Observed behavior (post-#833)

On leave-as-deny, if respond fails, `finally` still `navigation.dispatch(e.data.action)` when
`!hasSubmittedServerDecision()` (`PendingAuthScreen.tsx` L98–103). Attempt may remain `READ` until
TTL scheduler or Admin cancel.

### Underlying signal

Explicit **UX fail-open**: “don’t imprison the user on Pending Auth.” Waiter unblocking is
best-effort on leave, guaranteed only on successful deny or TTL/#833 cancel paths.

### User impact

Admin passwordless-wait may hang until TTL (~120 s) after a flaky leave. Phone user is free.

### Security note

Acceptable for alpha if Admin UX already handles wait timeout. Not a privilege escalation.

### Options

| Option | Trade-off |
| --- | --- |
| A. Keep fail-open leave | Current #833 comment intent |
| B. Stay on screen with error + Retry deny | Better for waiters; can trap impatient users |
| C. One retry then leave; toast “request may still be waiting” | Compromise |

### Recommendation

**Phase 0 — A** + short user-visible toast on failed leave-deny (**Phase 1 — C**). Confirm with Julie
whether Admin wait copy already covers timeout.

---

## Finding 6 — Fail-closed pending clears local attempt; server may stay `READ`

### Observed behavior

Invalid pending signature / missing key → `{ kind: 'fail_closed' }` and no trusted `attempt`
(`claimPendingAttempt.ts` L183–188; Detail/Pending clear attempt / show error). Server claim already
moved attempt to `READ`. Leave without `attempt` does **not** deny (#833 L87).

### Underlying signal

Display fail-closed (don’t show untrusted context) without a paired **server release** strategy.
Read-once claim + local distrust = stranded `READ` until TTL.

### User impact

Admin waiter blocked ~TTL; user sees scary signature error and cannot Approve (correct) but also
doesn’t release the attempt (gap).

### Security note

Must not Approve on bad pending sig. Releasing via unsigned cancel is a **product/security design**
choice (Auth API cancel is not mobile today; deny needs a trusted attempt proof token).

### Options

| Option | Trade-off |
| --- | --- |
| A. Status quo (TTL cleans up) | Simple; bad for wait latency |
| B. Mobile calls Integration/Admin cancel with enrollment proof | New mobile surface; authz design |
| C. Auth API: claim that fails client verify is rare — add “abandon claimed” with device proof only | Protocol work |
| D. Document as known alpha limit + Admin cancel / wait timeout | Phase 0 honesty |

### Recommendation

**Phase 0 — D.** **Phase 2** only if real incidents show stranded READ after bad pending sig;
otherwise TTL is enough for alpha. Do not rush unsigned deny.

---

## Finding 7 — Sibling docs still describe pre-#833 leave → TTL

### Observed behavior

FSM + this orientation describe post-#833 leave-as-deny. `MOBILE_FUNCTIONAL_FLOWS` / parts of
screen docs historically said leave relies on TTL (main `PendingAuthScreen` header comment L12–14
pre-#833). Risk of agents/humans implementing the old story.

### Underlying signal

Repo-as-memory lag across parallel PRs (#833 code, #835 docs).

### User impact

Indirect: wrong support advice, wrong tests, wrong future patches.

### Security note

Doc drift can reintroduce “leave without deny” as “correct.”

### Options

| Option | Trade-off |
| --- | --- |
| A. Update sibling docs in same PR as #833 merge | Clean single cutover |
| B. Update now with “pending #833” banners | Slight duplication until merge |
| C. Wait passively | Drift continues |

### Recommendation

**Phase 0 — B already partly done** in #835. **Phase 0 follow-up — A** as soon as #833 merges:
refresh functional flows + screen leave row; remove “pending #833” hedges.

---

## Finding 8 — No dedicated mobile copy for Auth API 409 state conflict

### Observed behavior

Generated client types include `respondResponse409`
(`authentication-attempts.ts`). UI maps errors via `userFacingAuthApiError` → Problem Detail
`detail` if Ezkey type, else generic fallback (`authApiProblem.ts` L75–81). No
`pendingAuth.conflict` / “already cancelled” string.

### Underlying signal

Alpha tolerance for rare races (cancel vs approve, #833 m1). Generic copy is honest enough if
backend `detail` is safe and clear.

### User impact

User may see a technical or generic line after Admin cancel wins. Recoverable: back to Detail,
check again later.

### Security note

Prefer catalog `detail` over leaking internal status enums. Don’t invent client-side status.

### Options

| Option | Trade-off |
| --- | --- |
| A. Keep generic + server detail | Minimal |
| B. Map problem type `auth-attempt-state-conflict` to friendly i18n | Clearer; needs stable type URL |
| C. Special-case HTTP 409 only | Fragile if other 409s appear |

### Recommendation

**Phase 1 — B** after confirming Problem Detail `type` stability post-#833. Phase 0: none required
for alpha if wait/cancel races stay rare.

---

## Cross-cutting themes

1. **Server commit vs phone display trust** — Findings 3, 6: crypto fail-closed for UI; server may
   already have moved.
2. **Unblock waiters vs don’t trap the human** — Findings 4, 5, #833 leave-as-deny: tension resolved
   toward human freedom + best-effort deny.
3. **Clock honesty** — Findings 1, 2: missing or misnamed time fields block sober urgency UX.
4. **Doc lag is product risk** — Finding 7: continuous-improvement includes documentation cutover.
5. **Proportion** — Prefer copy/rename/map fields before new Auth API endpoints (Finding 6 Phase 2).

---

## Phased roadmap

| Item | Phase | Effort | Risk | Dependency | Owner zone | Proposed backlog issue title (not opened) |
| --- | --- | --- | --- | --- | --- | --- |
| Lock Finding 3 intent + post-HTTP verify copy | 0 | S | Low | #833 | mobile + docs | Mobile: honest copy when respond HTTP OK but result sig fails |
| Rename `PendingAttempt.createdAt` → `checkedAt` | 0 | S | Low | — | mobile | Mobile: rename PendingAttempt checkedAt (local claim time) |
| Toast on failed leave-deny then exit | 0–1 | S | Low | #833 | mobile | Mobile: toast when leave-as-deny fails but navigation continues |
| Sibling doc cutover leave-as-deny | 0 | S | Low | #833 merge | docs | Docs: align functional flows/screens with leave-as-deny post-#833 |
| Document stranded READ after fail-closed pending | 0 | S | Low | — | docs | Docs: alpha limit — untrusted pending leaves server READ until TTL |
| Expose `expiresAt` on pending + light remaining-time UI | 1 | M | Med | Auth API contract + #833 settled | auth-api then mobile | Auth+mobile: pending expiresAt on wire and Remaining time on Pending Auth |
| Suppress navigate-after-respond if screen left | 1 | S | Low | #833 | mobile | Mobile: avoid double goBack when leaving during in-flight respond |
| Friendly i18n for auth-attempt state conflict | 1 | S | Low | #833 Problem types | mobile | Mobile: dedicated copy for respond 409 state conflict |
| Protocol/path to release READ after bad pending sig | 2 | L | High | security design | auth-api + mobile | Auth/mobile: abandon claimed attempt without trusted pending UI |
| Richer expiry (soft disable Approve at 0) | 2 | M | Med | Phase 1 timer | mobile | Mobile: disable approve after local expiry clock |

**Phase 0** = quick wins (docs, rename, copy) after/with #833. **Phase 1** = small UX honesty
(timer, 409 copy, nav polish). **Phase 2** = protocol or rare-path redesign — only with Marc +
Christophe GO.

---

## Questions pour Marc

1. Is **no countdown** a deliberate calm-UX choice, or an oversight to fix in Phase 1?
2. Confirm **Finding 3**: server commit wins; phone may show verify error without undoing — OK for
   product language?
3. Confirm **Finding 5**: fail-open leave after deny failure is preferred over trapping the user?
4. For **Finding 6**, is TTL-only cleanup acceptable for public alpha, or must stranded `READ` after
   bad pending sig be a P1?
5. Should leave-as-deny be explained in end-user copy (“Leaving denies this request”), or remain
   silent behavior?
6. Priority between Phase 1 timer vs Phase 1 409 copy vs Phase 0 rename — which first after #833?
7. Any veto on Phase 2 “abandon claimed attempt” before a security design pass?

---

## Revues des collaborateurs

### Alex (intention produit)

_À compléter._

### Julie (opérabilité / UX)

_À compléter._

### Christophe (sécurité)

_À compléter._

### Patrick (solidité technique)

_À compléter._

---

## Provenance

- Parent FSM: [MOBILE_APP_STATE_MACHINE.md](MOBILE_APP_STATE_MACHINE.md)
- Issue [#834](https://github.com/mgagp/ezkey/issues/834) · PR [#835](https://github.com/mgagp/ezkey/pull/835)
- Behavior baseline PR [#833](https://github.com/mgagp/ezkey/pull/833)
- Decision (repo-as-memory): open FSM questions feed this orientation note for phased mobile
  continuous improvement — reason: make hidden UX/security intent reviewable before backlog issues;
  issue #834.
