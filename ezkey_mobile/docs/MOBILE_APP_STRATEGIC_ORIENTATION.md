<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Ezkey Mobile — Strategic Orientation (from FSM open questions)

## Lived loop

The person starts sign-in on the integrating application and watches the countdown there. They open
Ezkey Mobile on purpose to approve or deny, then return to that same screen to see whether the
sign-in completed. The phone does not push, and it does not keep a second clock. This loop is why
the decisions below refuse a phone timer, an automatic retry, and a second telling of a result the
other screen already shows. It is not a use-case catalog.

## Context and method

The canonical FSM in [MOBILE_APP_STATE_MACHINE.md](MOBILE_APP_STATE_MACHINE.md) (issue
[#834](https://github.com/mgagp/ezkey/issues/834), PR [#835](https://github.com/mgagp/ezkey/pull/835))
surfaced **open questions**: places where code behavior exists but product intent / UX / security
posture was never made explicit. Those gaps are not “bugs by default”; they are **hidden decisions**.

This document explores each finding, names the underlying signal, and proposes a **proportionate,
phased** continuous-improvement backlog. It does **not** open GitHub issues yet and does **not**
change product code. Collaborator reviews @ `4ccecdf3` (Alex, Julie, Christophe, Patrick) are
integrated below. Marc’s decisions are in **Decisions** and in **Questions for Marc**. Reviewer
text that still shows an old option is the path that was considered.

| Baseline | Notes |
| --- | --- |
| Language | English — matches `ezkey_mobile/docs/` convention. |
| Code truth | PR [#833](https://github.com/mgagp/ezkey/pull/833) **merged 2026-10-16**. Leave-as-deny / race guards are on `main`. Historical line citations in findings below may still name tip `fcc31fbf`. |
| Phase 0 gate | **Satisfied for leave-as-deny** (#833 on `main`). Remaining honesty / Q11 app-code items are ordinary follow-ups, not gated on that merge. |
| Closed-test link | **Decided (Question 6).** No deadline on internal testers. Ship the confirmed honesty set together, done properly, before widening [#711](https://github.com/mgagp/ezkey/issues/711). Do not cut it down to a minimal pair. Declined items stay declined. |
| Method | Observed behavior (file:line) → signal → impact → options → recommendation (or open disagreement). |
| FSM nature | The FSM is **descriptive of current hooks/flags**, not an implemented state machine in code (Patrick). |
| Repo-as-memory | Behavior locks land in `MOBILE_APP_STATE_MACHINE.md` + `MOBILE_FUNCTIONAL_FLOWS.md` + [`docs/CLIENT_BEHAVIOUR_CONTRACT.md`](../../docs/CLIENT_BEHAVIOUR_CONTRACT.md); 409 / pending expiry wire semantics in `docs/ENDPOINT.md`. |
| TTL (post-#895) | Per-request floor **15**, hard ceiling **120**; property default **120** (Bean Validation 30–600, startup clamp → effective configured default **30–120**). |

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

### Recommendation (superseded)

The review draft recommended Phase 0 copy when respond fails for expiry, then a Phase 1 remaining-time
line. **That recommendation is not the decision.** See [Decision — Question 1](#decision--question-1--the-countdown-stays-on-the-integrating-application).

---

## Decisions

### Decision — Question 1 — The countdown stays on the integrating application

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

Ezkey does not push an authentication request to the phone. The person starts sign-in in the
integrating application, then chooses to open Ezkey Mobile and check for a pending request. That
pull posture is already the product model
([`docs/MOBILE_DEVELOPER_GUIDE.md`](../../docs/MOBILE_DEVELOPER_GUIDE.md) § User-Initiated Polling
Model; [`MOBILE_POSITIONING.md`](MOBILE_POSITIONING.md) excludes background polling and push).

While they wait, the countdown in front of them is the one in that integrating application. The SDK
is expected to show remaining time from the attempt `expiresAt` returned on create and wait. From
that same screen they can cancel through the API, or they can open the phone and approve or deny.
The clock that matches real life is there.

A second countdown on the phone would repeat information they already have, and it would drift by a
few seconds against the handset clock. Two timers that do not agree is a poor experience, and it is
accidental complexity. The phone therefore does not show remaining time, and the Auth API pending
response does not gain `expiresAt` or `expiresInSeconds` in order to drive a phone timer.

A request the person did not start does not change this. Nothing notifies the phone, so a reasonable
person does not open the app to look for a sign-in they did not begin. If they did open it and found
one, they would still know they were not the initiator, and would let it expire or deny it. A mobile
timer does not serve that moment.

The earlier finding treated the missing timer as a gap (Phase 0 expired copy, then an approximate
line). That reading stayed on the mobile screen. It did not step back to the interaction Ezkey
already chose, or to how a person actually lives the wait. The options above stay as the path that
was considered. They are not the decision. The correction is recorded here so later passes start
from the lived flow, not only from the phone UI.

**Locked**

- No TTL countdown on Ezkey Mobile.
- No pending-wire expiry field whose purpose is a phone timer.
- Question 7 (always-on “about N min” versus silence until near expiry) is closed by this decision:
  there is no mobile timer to present.

**Still open, on purpose**

Honest copy when a respond meets an attempt that is already expired, cancelled, or decided is one
sentence, decided in Question 8. This decision does not choose that wording.

### Decision — Question 2 — Lock on HTTP 200, before the result signature check

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834). Confirms the
review consensus on Finding 3.

The person approves or denies on the phone. The app signs and calls `respond`. As soon as the Auth
API returns HTTP 200, the app sets `serverDecisionAttemptIdRef` (per `authAttemptId`), and only then verifies the integration
signature on the result. That 200 means the exchange was **processed**. The body may be `APPROVED`,
`DENIED`, or a business `FAILED`. It does not by itself mean “approved.”

If the result signature is missing or invalid, the phone does not display approved or denied, and it
does not undo the server. Approve and Deny stay hidden. Leaving the screen does not send a second
decision. The person sees whether the sign-in succeeded on the integrating application, where they
started it.

The lock stays before the local check. Moving it to after verification would treat a bad signature
as “nothing was sent,” and leaving the screen could deny over a decision the server already
processed.

**Locked**

- Set the decision lock on respond HTTP 200, before the result-signature check.
- On a failed check: no approved/denied display, no second respond, no undo.
- Hide Approve and Deny on that screen.

**Still open, on purpose**

None. The sentence is decided in Question 9.

### Decision — Question 3 — Short message, then the person decides on the integrating application

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

The person is signing in. They open the phone and consume the pending request. They then change
their mind: Deny, or swipe back to the previous screen. That leave sends a refusal. If the network
fails at that step, the app still lets them leave and shows a short message. It does not retry by
itself.

What they do next is look at that message and return to the integrating application. Either the
refusal arrived and the sign-in is finished, or the countdown there is still running. They choose:
let it finish, or cancel on that screen. The phone does not choose for them.

An automatic retry is safe on the wire — the deny update is idempotent — and it was a reasonable
phone-only option. It still acts after the person has already gone back to look. The same zoom-out
as Question 1 applies: the lived loop is the integrating application, then the phone, then back.
The short message is what that loop needs.

**Locked**

- Leave stays open. Do not trap the person on the phone.
- One short message. No automatic retry.
- Do not add a later auto-retry unless a real incident shows people cannot settle this on the
  integrating application.

**Still open, on purpose**

This message ships with the rest of the confirmed honesty set before testers are widened
(Question 6). Exact words can sit next to the leave hint (Question 5). The policy above does not
wait on the wording.

### Decision — Question 4 — Bad pending signature waits out the server expiry

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

The person checks for a pending request. The integration signature on that payload is missing or
invalid, so the phone does not show it. There is no loaded attempt, so leaving does not send a
refusal. The server row may stay `READ` until the attempt expires (default 120 seconds). Nothing
else can approve it: approval needs the device signature. A newer attempt supersedes this one.

On the integrating application the countdown is still running. The person lets it finish or cancels
there. The phone says, in plain words, that the request was not shown and will expire. It does not
talk about signatures.

Sending an unsigned refusal would decide a payload the phone has already refused to trust. Releasing
the row with a device-proven cancel is a protocol change. It waits for a real incident. An audit
event for repeated failures waits for the same kind of evidence. Neither is part of this decision.

**Locked**

- Fail closed: do not display an unverified pending request.
- Alpha limit: the row may stay `READ` until server expiry. No release protocol now.
- Never send an unsigned refusal for this path.
- Calm copy, without signature jargon.
- No new audit event unless a real incident shows an operator must see these failures.

**Still open, on purpose**

Question 12 still covers other `READ`-until-expiry windows (for example the app killed during
respond). This decision covers the bad pending signature only.

### Decision — Question 5 — A permanent line under the actions

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

Leaving a screen that shows a loaded request sends the same refusal as Deny. Approve and Deny
already say what they do. Back does not. A person may swipe back meaning “I refuse,” or meaning
“I am returning to the integrating application without having decided.” A silent leave refuses for
them.

Marc checked a live approval screen on a phone. The layout has room for one informative line, and
that line stays readable. The intensity stays proportionate: visible at the moment of choice, not a
dialog and not a louder announce.

**Locked**

- While a request is loaded, show a permanent line under Approve and Deny: leaving this screen
  declines the request.
- No confirmation dialog.

Whether this line ships before the closed test widens is decided with the rest of the confirmed
honesty set in Question 6.

### Decision — Question 6 — Ship the confirmed honesty set before widening testers

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

There is no deadline on the internal closed test ([#711](https://github.com/mgagp/ezkey/issues/711)).
The confirmed changes are small. Cutting them down to a minimal pair would leave the phone less
honest than this review has already judged it should be.

Before testers are widened, implement the honesty practices this review confirms, as one proper
pass. That includes the decisions already locked (the leave line, the short message when a refusal
does not send, calm copy when a pending signature fails, and the respond lock when the result
signature cannot be checked) and the later questions in this list once they are confirmed.

A smaller gate was considered: only the leave line and the failed-refusal message, because those
are the gestures a tester will actually hit. That reading is understandable. It is not the
decision. The full confirmed set is what makes the app honest. Items this review declines (a phone
countdown, an automatic retry, an unsigned refusal, a release protocol without an incident) stay
declined. They are not part of the set.

**Locked**

- No date pressure on widening #711.
- Implement every practice this review confirms before that widen.
- Do not ship a minimal subset first.
- Do not implement options this review has declined.

### Decision — Question 8 — One sentence when the request is no longer active

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

`POST /api/v1/auth-attempts/respond` returns **409** with problem type
`auth-attempt-state-conflict` when the attempt is not found, still `PENDING`, was cancelled, is
already decided, or was superseded (#844 Marc m1 on #833 — same wire type; server logs keep the
cause). That collapse is intentional (also anti-enumeration of sequential ids). The phone maps that
type to one sentence: the request is no longer active. Back only. No Try again.

**Amended #895:** wall-clock expiry after ownership proof uses a **distinct** type
`auth-attempt-expired` (still **409**, still Back only; clients **MAY** use distinct “expired”
copy). Do not reopen splitting of the remaining `auth-attempt-state-conflict` causes.

The person sees which cause it was on the integrating application: the countdown finished, they
cancelled, the sign-in already completed, or the request never became actionable. Splitting
`auth-attempt-state-conflict` so the phone can retell those causes would duplicate the other
screen.

A lost approve response, where a session may already exist, is not this case. That stays
Question 10. This sentence ships with the confirmed honesty set before testers are widened
(Question 6).

**Locked**

- Keep a single problem type for not-found, still `PENDING`, cancelled, already decided, and
  superseded (#844 m1 / #833).
- **Amended #895:** expiry is `auth-attempt-expired`, not folded into state-conflict.
- One user sentence for state-conflict. Back only. Not a retryable error.
- Do not add further distinct problem types for the collapsed state-conflict causes.

### Decision — Question 9 — Point at the sign-in screen, not at a tenant name

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

When `respond` returns HTTP 200 and the result signature cannot be checked, the phone already
hides Approve and Deny and does not show approved or denied (Question 2). The sentence may tell
the person to look at the screen where they started signing in. That is the integrating
application, the one with the countdown. It is how they see whether they got in.

The sentence does not name the organization or the tenant. Enrollment cards omit those names for
a different reason. Pointing at the other screen does not put the names back.

**Locked**

- Say that the response was sent and that its confirmation could not be verified.
- Say to check the sign-in where it was started.
- Do not name a tenant or an organization.
- Back only. This wording ships with the confirmed honesty set (Question 6).

### Decision — Question 10 — Uncertain result, then look at the sign-in screen

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

If the `respond` response never comes back, the server may already have accepted the decision and
opened a session. The phone must not say failure, and it must not offer another try as if the
decision were still open. It says the result is uncertain and points at the screen where the
sign-in was started. That screen shows either an open session or a countdown still running.

This case should be rare. Making `respond` idempotent so a replay returns a signed final status
was considered. It is a large mechanism for a path that should almost never happen: same-signature
replay, a contrary decision that must not overwrite, expiry staying on the inactive sentence, and
the one-request-per-five-minutes limit. That is Christophe’s signed-status recommendation, realized
as idempotent `respond`. It is deferred. It is not designed here, not a backlog item, and not a
general-availability gate.

**Locked**

- Lost `respond` response: one sentence that the result is uncertain, and to check the sign-in
  where it was started.
- Do not label that state a failure.
- Do not offer a retry that sends another decision.
- This sentence ships with the confirmed honesty set (Question 6).

**Deferred, not specified**

Idempotent `respond` as a way to return the real signed status. Recorded so a later pass does not
treat the omission as an overlooked gap.

### Decision — Question 11 — Biometric only when approving

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

Protected mode comes from the enrollment policy or the device preference. It guards the yes that
opens a session. Approve still asks for biometric or device-credential confirmation, then signs.

Deny and leaving the screen send a refusal. They do not open a session. They sign that refusal
without the biometric prompt. The permanent line that leaving declines stays true: dismissing a
prompt no longer leaves the attempt `READ` until expiry.

Someone who already holds the unlocked phone can still refuse. The harm is starting the sign-in
again, not granting access.

**Locked**

- Protected mode: biometric or device credential on Approve only.
- Deny and leave-as-deny sign without that prompt.
- This ships with the confirmed honesty set (Question 6).

### Decision — Question 12 — The read window is a known limit

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

If the app is killed or backgrounded during `respond`, or the server lifetime ends while Approve
biometric is open, the attempt may stay `READ` until it expires. There is no release protocol and
no resume machinery in this set. The person lets the countdown finish on the integrating
application, or cancels there. Reopen a mechanism only if a real incident requires it. A bad
pending signature is the same kind of limit, already decided in Question 4.

Figures of **120 seconds** in this review are the **default / ceiling**, used to illustrate that
window. They are not a free-form product constant. Applied create TTL is **15–120 s** since #895
(`min(request|default, 120)`). `ezkey.core.auth-attempt.ttl-seconds` still has Bean Validation
30–600, but values above 120 clamp to 120 at startup.

**Deployment note (not a fix here).** Public hosts on Cloudflare Free, including the community
instance, are cut at the origin after **100 seconds** (HTTP **524**, not raisable). That fact is
already in [`product-docs/global/product-intent.md`](../../product-docs/global/product-intent.md)
for `ezkey.org` and `ezkey.online`. The default attempt lifetime of 120 seconds is longer than
that cut, so a blocking wait on those hosts can end at the proxy while the attempt is still valid.
Take that into account when deploying the community instance. This review does not change the
default or the proxy.

**Locked**

- The `READ`-until-expiry window after a killed app, a backgrounded `respond`, or a lifetime that
  ends during Approve biometric is an accepted limit.
- No protocol and no automatic resume unless a real incident requires it.
- “120 seconds” in these notes means the default / ceiling of applied attempt TTL (**15–120** since
  #895).

**Noted for later deployment**

Cloudflare Free’s 100-second origin timeout versus that default, for the community instance. Not
designed in this review.

### Decision — Question 13 — The decision lock follows the attempt

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

**Decision (#834 Q13 / delivered on [#833](https://github.com/mgagp/ezkey/pull/833)):** the lock is
`serverDecisionAttemptIdRef` keyed to `authAttemptId`. A new pending attempt with a different id is
not locked. Do not open a parallel fix.

A lost `respond` response keeps the uncertain sentence from Question 10 (also delivered on #833:
lock + Back only). A later **409** keeps the single “no longer active” sentence from Question 8.
There is no third sentence such as “already decided, check the other device.”

**Locked**

- The processed-decision lock is per `authAttemptId` (`serverDecisionAttemptIdRef` on #833).
- No new 409 wording. Questions 8 and 10 already cover those screens.

### Decision — Question 14 — No expiry field on the pending response

**Decided 2026-10-15** (Marc). Issue [#834](https://github.com/mgagp/ezkey/issues/834).

Question 1 already keeps the countdown on the integrating application and refuses a phone timer.
This question only chooses the shape of a pending expiry field, if one were added: an unsigned
display value (`expiresInSeconds` or `expiresAt` plus `serverNow`), or the same data inside the
signed pending payload.

Neither. The pending **200** body does not gain `expiresAt`, `expiresInSeconds`, or `serverNow`.
Not as display-only fields, and not inside the signed payload. Create and wait already give the
integrating application the `expiresAt` for the countdown the person watches. Putting a second
clock on the pending response, signed or not, is the duplication Question 1 declined.

**Locked**

- No expiry field on Auth API `pending`, unsigned or signed.
- This confirms Question 1. It does not reopen a phone countdown.

---

## Finding 2 — `PendingAttempt.createdAt` is local claim time

### Observed behavior

`createdAt: checkedAt` (`claimPendingAttempt.ts` L107, L201) is local claim time, not server
attempt `createdAt`.

### Recommendation

**Phase 0 — rename to `checkedAt`** in the honesty implementation pass, not as a gate on #833.
The name must not be read as server creation time (Questions 1 and 14). See
[Order — decisions first, then re-read #833](#order--decisions-first-then-re-read-833).

---

## Finding 3 — `serverDecisionAttemptIdRef` after respond HTTP 200, before result signature verify

### Observed behavior (#833 @ `fcc31fbf`)

After `authAttemptsApi.respond` returns HTTP 200, code sets `serverDecisionAttemptIdRef` to the attempt id
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

**Open (no pick):** closed by Question 9. The sentence may point at the sign-in screen. It does not
name a tenant.

### Recommendation

**Confirmed** in [Decision — Question 2](#decision--question-2--lock-on-http-200-before-the-result-signature-check).
Lock before the local verify. Hide Approve and Deny. Do not show `APPROVED` or `DENIED`. Do not
send a second decision. The sentence may point at the sign-in screen and must not name a tenant
(Question 9).

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

**Note (historical):** Patrick marked this (and Findings 9 lock-reset / uncertain-decision handling)
as fixed inside #833. **#833 merged 2026-10-16** — do not open a duplicate competing PR for that
train.

### Recommendation

**Decided** in [Order — decisions first, then re-read #833](#order--decisions-first-then-re-read-833).
Suppress the extra `goBack` (shipped with #833). A device walk checks the fix. Do not open a
second pull request.

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

### Recommendation

**Decided** in [Decision — Question 3](#decision--question-3--short-message-then-the-person-decides-on-the-integrating-application).
Leave stays open. One short message. No automatic retry. The person settles the wait on the
integrating application.

---

## Finding 6 — Fail-closed pending clears local attempt; server may stay `READ`

### Observed behavior

Bad pending sig → `fail_closed`, no local `attempt` (`claimPendingAttempt.ts` L183–188). Leave
without attempt does not deny. Christophe: no other channel can approve (device Ed25519 required);
TTL enforced on respond; newer attempt supersedes. **TTL now; device-proof cancel only in Phase 2;
never unsigned deny.**

### Recommendation

**Decided** in [Decision — Question 4](#decision--question-4--bad-pending-signature-waits-out-the-server-expiry).
Do not show the request. Leave it `READ` until server expiry. No release protocol and no audit
event until a real incident. Never an unsigned refusal. Calm copy, no signature jargon.

---

## Finding 7 — Sibling docs still describe pre-#833 leave → TTL

### Recommendation

**Done for leave-as-deny:** #833 merged 2026-10-16. Sibling flows/screens/FSM should describe
leave-as-deny on `main` (corpus refresh also tracked under [#1052](https://github.com/mgagp/ezkey/issues/1052)).
See [Order — decisions first, then re-read #833](#order--decisions-first-then-re-read-833).

---

## Finding 8 — Auth API 409 state conflict copy

### Observed behavior

`TYPE_AUTH_ATTEMPT_STATE_CONFLICT` already exists
(`AuthApiProblemCatalog.java` L35–36 → `…/auth-attempt-state-conflict`). Mobile
`userFacingAuthApiError` does not map that `type` to friendly i18n (`authApiProblem.ts` L75–81).
Today cancelled / expired / already-decided collapse to one conflict (Patrick — say if intentional
once Marc decides; land semantics in `docs/ENDPOINT.md`).

### Recommendation

**Decided** in [Decision — Question 8](#decision--question-8--one-sentence-when-the-request-is-no-longer-active).
Map `auth-attempt-state-conflict`. One sentence: the request is no longer active. Back only. Do not
split that type’s collapsed causes. **Amended #895:** wall-clock expiry is
`auth-attempt-expired` (distinct type; still Back only). The lost-response case stays Question 10.

---

## Finding 9 — New attempt on same screen after “decision sent” lock (Patrick)

### Observed behavior

Historically `serverDecisionSentRef` (boolean) stayed true for the whole screen. **Delivered on
#833:** `serverDecisionAttemptIdRef` stores the attempt id; a new pending attempt with a different
id is not locked (Q13). Path after HTTP 200 + sig fail still locks **that** id (Q9 Back-only).

### Recommendation

**Decided / delivered** in [Decision — Question 13](#decision--question-13--the-decision-lock-follows-the-attempt).
No third sentence for a 409 after a lost response (Q8 / Q10).

---

## Finding 10 — Uncertain decision / lost response after server processing (Patrick + Christophe)

### Observed behavior

Network cut **after** server processed respond → historically mobile `catch` could stay retryable →
(delivered on #833: `UncertainDecision` + lock after HTTP started; see Q10) — earlier tip
retry Approve or leave-deny → **409** while decision already exists. Christophe (medium before GA):
approve **ACCEPTED** on server but response lost → retry/leave-deny gets signed **FAILED** / conflict
UI saying “failure” while a session may already be open.

### Recommendation

**Decided** in [Decision — Question 10](#decision--question-10--uncertain-result-then-look-at-the-sign-in-screen).
Uncertain sentence, pointing at the sign-in screen. No failure label. No retry that sends another
decision. Idempotent `respond` is deferred and not specified.

---

## Finding 11 — Biometric on leave-as-deny / deny (Christophe)

### Observed behavior

Leave-as-deny calls `handleRespond(false)`, which may open BiometricPrompt when
`requiresProtectedApproval` (`usePendingAuth.ts` L423–435; `approvalRequirement.ts` L42–45 applies to
approve **or** deny). Cancel prompt → `READ` until TTL. Christophe: biometric on **deny** has little
security value (Level 2); recommend signing deny **without** protected prompt.

### Recommendation

**Decided** in [Decision — Question 11](#decision--question-11--biometric-only-when-approving).
Biometric or device credential on Approve only. Deny and leave-as-deny sign without that prompt.

---

## Finding 12 — Process death / background during respond; TTL during biometric (Christophe + Patrick)

### Observed behavior

App killed or backgrounded during in-flight respond: `beforeRemove` may **not** run → no leave-deny
→ `READ` until TTL. TTL can expire while biometric UI is open → later respond → 409. Deep links not
verified.

### Recommendation

**Decided** in [Decision — Question 12](#decision--question-12--the-read-window-is-a-known-limit).
Known limit. No protocol and no resume unless a real incident requires it. “120 seconds” is the
default / ceiling of applied lifetime (**15–120** since #895). Cloudflare Free’s 100-second cut is
a deployment note for the community instance, not a change in this review. Deep links stay
unchecked; they are not part of this decision.

---

## Cross-cutting themes

1. **Processed vs displayed** — HTTP 200 means a respond **outcome** (`APPROVED`/`DENIED`/`FAILED`),
   not “safe to show APPROVED.”
2. **Don’t trap the human vs unblock waiters** — leave-as-deny + fail-open leave; honesty toasts/hints.
3. **Countdown lives on the integrating application** (Question 1). The phone does not show remaining time. An unsigned clock must not become a cryptographic gate.
4. **Descriptive FSM** — flags/refs today; `NoLongerActive` as a **product state**, not a new library.
5. **#833 is the behavior train** — Phase 0 honesty + Patrick’s lock/nav/409 fixes ride that merge;
   docs cutover follows.
6. **Closed-test honesty (#711)** — **Decided (Question 6).** The confirmed honesty set ships together, done properly, before testers are widened. No minimal cut. No deadline.

### `NoLongerActive` (user-facing shape decided in Question 8)

One sentence — the request is no longer active — and **Back only**, not Try again. The name is a
descriptive label for that screen, not a new state-machine library. Expired, cancelled, and already
decided share it.

---

## Reviewer disagreements (no pick)

| Topic | Side A | Side B |
| --- | --- | --- |
| F5 honesty | **Decided (Q3).** Phase 0 short message, then leave. No automatic retry. | Christophe’s one auto-retry is declined. The person settles the wait on the integrating application. |
| Leave-as-deny visibility | **Decided (Q5).** Permanent line under Approve and Deny. No dialog. | Alex’s louder announce and a confirmation dialog are declined. The line is visible enough to read before the gesture. |
| 409 user copy | **Decided (Q8).** One “no longer active” sentence. Back only. One problem type. | Distinct types for cancelled / expired / already-decided are declined. The integrating application shows which it was. |
| Timer presentation | **Decided (Q1).** No mobile timer. The countdown stays on the integrating application. | Julie’s always-on line and a near-expiry-only line are both declined. |
| F4 double goBack phase | **Decided.** Suppress the extra navigation inside #833, before that PR merges. | A device walk checks the fix. It does not decide whether to make it. No second PR. |
| Disable Approve at 0 | **Closed by Q1.** There is no phone clock at which to disable Approve. | Patrick’s unsigned-clock warning stands as a reason not to add that clock. |

---

## Phased roadmap

**Phase 0 = after (or in) #833 merge.** Items marked “in #833” should not spawn duplicate issues.

| Item | Phase | Effort | Risk | Dependency | Owner zone | Proposed backlog title (not opened) |
| --- | --- | --- | --- | --- | --- | --- |
| Post-verify-fail copy; hide Approve/Deny | 0 | S | Low | #833; behavior locked (Q2); wording locked (Q9); ships before #711 (Q6) | mobile + docs | Mobile: copy when respond processed but result sig fails. Point at the sign-in screen. No tenant name. |
| Map `TYPE_AUTH_ATTEMPT_STATE_CONFLICT` → one “no longer active” sentence, Back only | 0 | S | Low | type already in Auth API; behavior locked (Q8); ships before #711 (Q6) | mobile | Mobile: map auth-attempt-state-conflict to one inactive sentence |
| Phase 0 inactive copy for that same 409 | 0 | S | Low | #833; same sentence as the row above (Q8); ships before #711 (Q6) | mobile | Mobile: clear inactive copy before closed-test widen |
| Short message when leave-deny fails; no auto-retry | 0 | S | Low | #833; behavior locked (Q3) | mobile | Mobile: honesty when leave-as-deny fails |
| Permanent line under actions: leaving declines the request | 0 | S | Low | #833; behavior locked (Q5) | mobile | Mobile: “Leaving declines this request” line |
| Fail-closed pending calmer copy | 0 | S | Low | behavior locked (Q4) | mobile | Mobile: fail-closed pending copy without crypto jargon |
| Rename `createdAt` → `checkedAt` | 0 | S | Low | — | mobile | Mobile: PendingAttempt.checkedAt |
| Sibling doc cutover leave-as-deny | 0 | S | Low | #833 merge | docs | Docs: flows/screens match leave-as-deny |
| Document stranded READ / process death / lifetime-during-biometric limits | 0 | S | Low | behavior locked (Q12); bad-sig window locked (Q4) | docs | Docs: READ-until-expiry is a known limit. Applied TTL 15–120 s (default/ceiling 120). Cloudflare Free 100 s is a community-deploy note |
| Reset decision lock per `authAttemptId` | 0 | S | Med | behavior locked (Q13); **fixing in #833** | mobile | (track #833 — do not duplicate) |
| Suppress goBack if unmounted / unfocused | 0 | S | Med | **fixing in #833** (Patrick) | mobile | (track #833 — do not duplicate) |
| Auth API: pending relative expiry (`expiresInSeconds` or `expiresAt`+`serverNow`) | Declined (Q1, Q14) | — | — | — | — | Do not add a pending expiry field, unsigned or inside the signed payload |
| Mobile: map expiry + approximate line + terminal expired UI | Declined (Q1) | — | — | — | — | No remaining-time line on Pending Auth |
| Uncertain sentence when the respond response is lost | 0 | S | Low | behavior locked (Q10); ships before #711 (Q6) | mobile | Mobile: uncertain result points at the sign-in screen; no failure label; no retry |
| Biometric on Approve only; Deny and leave sign without it | 0 | S | Low | behavior locked (Q11); ships before #711 (Q6) | mobile | Mobile: no biometric on deny or leave-as-deny |
| Abandon claimed attempt after bad pending sig | Declined until incident (Q4) | — | — | — | — | Do not add a release protocol for a bad pending signature unless a real incident requires it |
| Idempotent respond to return a signed final status | Deferred (Q10) | — | — | — | — | Seen and set aside. Not designed. Not a general-availability gate. |

---

## Questions for Marc

1. **Decided.** The missing mobile TTL UI is not an oversight. The countdown lives on the integrating application; a phone timer would duplicate it and drift. See [Decision — Question 1](#decision--question-1--the-countdown-stays-on-the-integrating-application). The 409 sentence is Question 8.
2. **Decided.** Lock on respond HTTP 200, before the result-signature check. A failed check shows that confirmation could not be verified, hides Approve/Deny, and does not send a second decision. See [Decision — Question 2](#decision--question-2--lock-on-http-200-before-the-result-signature-check). The sentence is Question 9.
3. **Decided.** Leave stays open. One short message, no automatic retry. The person looks back at the integrating application and lets the countdown finish or cancels there. See [Decision — Question 3](#decision--question-3--short-message-then-the-person-decides-on-the-integrating-application).
4. **Decided.** A bad pending signature is not shown. The row may stay `READ` until server expiry. No release protocol, no unsigned refusal, and no audit event until a real incident. Calm copy, no signature jargon. See [Decision — Question 4](#decision--question-4--bad-pending-signature-waits-out-the-server-expiry).
5. **Decided.** A permanent line under Approve and Deny: leaving this screen declines the request. No dialog. The live approval screen has room for it. See [Decision — Question 5](#decision--question-5--a-permanent-line-under-the-actions). It ships with the confirmed set before #711 widens (Question 6).
6. **Decided.** No deadline on internal testers. Implement the full set of practices this review confirms, properly, before widening #711. Do not cut that set down. Declined options stay declined. See [Decision — Question 6](#decision--question-6--ship-the-confirmed-honesty-set-before-widening-testers).
7. **Closed by Question 1.** There is no mobile timer, so neither an always-on line nor a near-expiry line.
8. **Decided.** One sentence for `auth-attempt-state-conflict`: the request is no longer active. Back only. Do not split not-found / PENDING / cancelled / already-decided / superseded into separate problem types (#844 m1). **Amended #895:** wall-clock expiry is distinct `auth-attempt-expired` (still Back only). See [Decision — Question 8](#decision--question-8--one-sentence-when-the-request-is-no-longer-active). A lost approve response stays Question 10. Missing integration signing key is **503** retryable (a later retry may hit terminal 409), documented on #833/#844 — not a Q8 split.
9. **Decided.** The unverified-confirmation sentence may say to check the sign-in where it was started. It does not name a tenant or an organization. Back only. See [Decision — Question 9](#decision--question-9--point-at-the-sign-in-screen-not-at-a-tenant-name).
10. **Decided.** A lost `respond` response shows that the result is uncertain and points at the sign-in screen. Not a failure. No retry that sends another decision. Idempotent `respond` is deferred and not specified. See [Decision — Question 10](#decision--question-10--uncertain-result-then-look-at-the-sign-in-screen).
11. **Decided.** Protected mode asks for biometric or device credential on Approve only. Deny and leave-as-deny sign the refusal without that prompt. See [Decision — Question 11](#decision--question-11--biometric-only-when-approving).
12. **Decided.** A `READ` window until expiry, after a killed or backgrounded app or when the lifetime ends during Approve biometric, is an accepted limit. No protocol unless a real incident requires it. Applied TTL is **15–120 s** (default/ceiling 120; #895). Cloudflare Free cuts the origin at 100 seconds; note that for the community instance, without changing it here. See [Decision — Question 12](#decision--question-12--the-read-window-is-a-known-limit).
13. **Decided / delivered on #833.** The processed-decision lock is per `authAttemptId` (`serverDecisionAttemptIdRef`). A lost response stays the Question 10 sentence (UncertainDecision). A later 409 stays the Question 8 sentence. See [Decision — Question 13](#decision--question-13--the-decision-lock-follows-the-attempt).
14. **Decided.** No expiry field on the pending response, neither unsigned for display nor inside the signed payload. The countdown stays on the integrating application (Question 1). See [Decision — Question 14](#decision--question-14--no-expiry-field-on-the-pending-response).

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
**processed (`APPROVED`/`DENIED`/`FAILED`)**. `serverDecisionAttemptIdRef` before verify is safe; A+C
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
- Question 1 decided 2026-10-15: no countdown on the phone; pending stays without an expiry field
  for a mobile timer. Locked in the FSM, functional flows, and `docs/ENDPOINT.md`.
- Question 2 decided 2026-10-15: decision lock on respond HTTP 200, before the result-signature
  check. Failed check does not display approved/denied and does not send a second decision. Locked
  in the FSM and functional flows.
- Question 9 decided 2026-10-15: that sentence may point at the sign-in screen where the person
  started. It does not name a tenant or an organization. Back only. Locked in the FSM and
  functional flows.
- Question 3 decided 2026-10-15: failed leave-deny still exits, with one short message and no
  automatic retry. The person settles the wait on the integrating application. Locked in the FSM
  and functional flows.
- Question 4 decided 2026-10-15: bad pending signature is not shown; the row may stay `READ` until
  server expiry. No release protocol, unsigned refusal, or audit event until a real incident.
  Locked in the FSM and functional flows.
- Question 5 decided 2026-10-15: permanent line under Approve and Deny that leaving declines the
  request. No dialog. Confirmed readable on a live approval screen. Locked in the FSM, functional
  flows, and screens doc.
- Question 6 decided 2026-10-15: no deadline on #711. The confirmed honesty set ships together,
  done properly, before testers are widened. Declined options stay out.
- Question 8 decided 2026-10-15: one `auth-attempt-state-conflict` type and one “no longer active”
  sentence, Back only for `auth-attempt-state-conflict`. **Amended #895:** wall-clock expiry uses
  distinct `auth-attempt-expired` (clients MAY use expired copy; still Back only). Locked in the
  FSM, functional flows, and `docs/ENDPOINT.md`.
- Question 10 decided 2026-10-15: a lost `respond` response is an uncertain result and points at
  the sign-in screen. Not a failure. No retry that sends another decision. Idempotent `respond`
  is deferred and not specified. Locked in the FSM and functional flows.
- Question 11 decided 2026-10-15: biometric or device credential on Approve only. Deny and
  leave-as-deny sign without that prompt. Locked in the FSM, functional flows, and screens doc.
- Question 12 decided 2026-10-15: `READ` until expiry after process death or a lifetime that ends
  during Approve biometric is an accepted limit. No protocol unless a real incident requires it.
  Applied TTL is **15–120 s** (default/ceiling 120; #895). Cloudflare Free’s 100-second origin
  timeout is a deployment note for the community instance, not a change here. Locked in the FSM
  and functional flows.
- Question 13 decided 2026-10-15: the processed-decision lock is per `authAttemptId`. Delivered
  with #833 (merged 2026-10-16); do not duplicate it. No third sentence beyond Questions 8 and 10.
  Locked in the
  FSM and functional flows.
- Question 14 decided 2026-10-15: no expiry field on Auth API pending, unsigned or inside the
  signed payload. Confirms Question 1. Locked in the FSM, functional flows, and
  `docs/ENDPOINT.md`.

## Order — decisions first, then re-read #833

**Status update (2026-10-27, #1052):** PR [#833](https://github.com/mgagp/ezkey/pull/833)
**merged 2026-10-16**. Leave-as-deny is on `main`. The historical ordering text below is retained
for decision trace. **Q11** (biometric / device credential on Approve only) remains **decided but
not fully shipped** in reference app code — tracked as a code follow-up.

**Decided 2026-10-15** (Marc). Keep pull request
[#833](https://github.com/mgagp/ezkey/pull/833) pending until this note is merged to `main`. Then
re-read #833 against these decisions before merging it.

This note is the intent. Pull request 833 was the behavior branch those decisions were read against
(`fcc31fbf`). Merging the note first gave the re-read a fixed canon. Historical honesty: before
that PR merged, `main` left a loaded screen without sending a refusal.

That PR’s leave path calls the same respond as Deny. Question 11 forbids a biometric prompt on
Deny and on leave; align app code with that (code follow-up if still prompting on Deny).

### Three follow-ups

1. **Second `goBack` while respond is in flight (Finding 4).** Suppress the extra navigation when
   the screen is already gone, so the person lands on Enrollment Detail. Fix it inside #833,
   before that pull request merges. The bug is born in that change. A device walk checks the fix.
   It does not decide whether to make it. Do not open a second pull request.
2. **Rename `PendingAttempt.createdAt` to `checkedAt` (Finding 2).** Do this in the honesty
   implementation pass, not as a gate on #833. The field is the local claim time. The old name
   reads like server creation time and would invite the phone countdown Questions 1 and 14
   refused.
3. **Sibling docs that still say leave waits for expiry (Finding 7).** Cut over after #833 merge
   (docs refresh under #1052). Hedges that said “until #833 merges” are obsolete.

### After this note is on main

Merge this branch first. Then rebase [#833](https://github.com/mgagp/ezkey/pull/833) onto that
`main` and revise its code against these decisions. Do not reopen the fourteen questions.

Expect a conflict in `docs/ENDPOINT.md` (both branches edit it). Keep this note’s pending-expiry
rule (Questions 1 and 14) and the single 409 sentence (Question 8). Keep #833’s other endpoint
edits where they do not contradict those two.

On the rebased #833, before it merges, the code must match:

- Question 11 — no biometric prompt on Deny or on leave.
- Question 13 — the processed-decision lock is per `authAttemptId`.
- Finding 4 — no second `goBack` when the screen is already gone.
- Question 3 — a failed leave-deny still lets the person leave, with no automatic retry.

The honesty pass comes after that merge. It is not a gate on #833: the permanent leave line
(Question 5), the 409 / unverified-confirmation / uncertain-result sentences (Questions 8, 9, 10),
the calm fail-closed pending copy (Question 4), and the `checkedAt` rename (Finding 2). Removing
the “pending #833” hedges (Finding 7) rides the #833 merge itself.

## Closeout — no use-case catalog

**Decided 2026-10-15** (Marc). A separate use-case corpus would add maintenance without a new
decision. The lived loop at the top of this note is enough. Do not open that catalog.
