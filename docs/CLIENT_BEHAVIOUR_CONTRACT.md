<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Client behaviour contract (application-independent)

> Status: normative companion for any Auth API client (third-party Flutter, React Native
> reference app, Demo Device, or other). Protocol wire formats remain in
> [`MOBILE_DEVELOPER_GUIDE.md`](MOBILE_DEVELOPER_GUIDE.md), [`ENDPOINT.md`](ENDPOINT.md), and the
> signature payload docs. The React Native finite-state model is
> [`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md).
> Flutter SDK surface:
> [`ezkey_dart/README.md`](../ezkey_dart/README.md).

This document states **client behaviour** using [RFC 2119](https://datatracker.ietf.org/doc/html/rfc2119)
keywords. Each rule cites the state-machine topic and the
[#834](https://github.com/mgagp/ezkey/issues/834) question number (**Q*n***). Rationales live in those
sources; do not duplicate them here.

| Keyword | Meaning here |
| --- | --- |
| **MUST** | Required for a correct client relative to Auth API / protocol behaviour (verified in server code or protocol docs). |
| **SHOULD** | Strong recommendation proven by the reference app / decided #834 answers; not strictly imposed by the wire protocol. |
| **MAY** | Reference-app product choice (navigation, layout, copy placement). Other clients may differ. |

When uncertain whether the protocol forces a behaviour, this contract uses **SHOULD** and says why.

---

## 1. Pending claim and display (Q1, Q4, Q14)

1. **MUST** treat `POST /api/v1/auth-attempts/pending` as a one-shot claim: a successful `200` with a
   verified integration signature moves the attempt to `READ` and will not be delivered again.
   State machine: claim sub-machine. Protocol: [`ENDPOINT.md`](ENDPOINT.md) § pending.
2. **MUST NOT** show pending context (`contextTitle` / `contextMessage` / challenge flag) until the
   pending integration signature verifies. On fail-closed, **MUST NOT** send a refusal for that
   claim. State machine: Detail `fail_closed`. **Q4**.
3. **MUST NOT** use a device-local countdown or an invented `expiresAt` as a security control. The
   device pending body does not carry expiry fields. State machine: Timers / TTL. **Q1**, **Q14**.
4. **SHOULD** leave remaining-time display to the integrating application’s create/wait UI.
   **Q1**.
5. **MAY** omit any phone-side timer chrome entirely (reference app choice). **Q1**.

---

## 2. Decision lock (Q2, Q10, Q13)

1. **MUST** remember the `authAttemptId` after any `respond` exchange that completed or became
   undecidable at the HTTP layer, and **MUST NOT** send a second approve/deny for that same id
   (including leave-as-deny). State machine: `serverDecisionAttemptIdRef` / Pending Auth FSM.
   **Q2**, **Q10**, **Q13**.
2. **MUST** set that lock when `respond` returns HTTP `200`, **before** local verification of the
   result integration signature. State machine: Approve / Deny table. **Q2**.
3. **MUST** treat a `respond` call that was sent but produced **no HTTP response** (timeout,
   transport cut, undecidable) as an **UNKNOWN** outcome: lock the attempt id, show an uncertain
   result (not a failure), offer Back only, and **MUST NOT** send another decision for that id.
   State machine: `UncertainDecision`. **Q10**.
4. **SHOULD** treat an ambiguous **5xx** on `respond` the same as UNKNOWN when the problem `type`
   does not prove the server rolled back. Exception: **503**
   `integration-signing-unavailable` is explicitly retryable (transaction rolled back; #844) and
   **MUST NOT** be locked as UNKNOWN. Classified **SHOULD** for other 5xx because the server may
   or may not have committed depending on failure timing; the protocol does not expose a single
   “committed?” signal. **Q10**.
5. **MUST** scope the lock per `authAttemptId` so a later pending attempt with a different id is
   not blocked. **Q13**.

### Known gap (SDK — code follow-up, not this docs PR)

The Dart SDK (`ezkey_dart`) currently maps lost `respond` transport failures to
`EzkeyNetworkException` with `retryable: true`, and treats many 5xx classifications as retryable.
That contradicts rules 2.3–2.4. Integrators reading only the SDK error flags today can be misled
into answering again. Docs state the contract; SDK behaviour change is a separate code follow-up
(see issue #1052 plan).

---

## 3. Leave screen (Q3, Q5)

1. **SHOULD** send `respond(accepted=false)` when the user leaves a screen that still holds a
   loaded, unlocked pending attempt (same wire path as Deny). Delivered with #833 (merged
   2026-10-16). State machine: Leave screen. **Q5**. Classified **SHOULD** (not MUST): the Auth
   API does not require a device-initiated cancel; an abandoned `READ` row expires by server TTL.
2. **SHOULD** keep a permanent, always-visible line that leaving declines the request (no
   confirmation dialog). **Q5**. Classified **SHOULD**: UX honesty, not a wire requirement.
3. **SHOULD** still allow navigation if leave-as-deny fails (network), with one short message and
   **MUST NOT** auto-retry leave-deny in a loop. **Q3**.
4. **MAY** choose how navigation stacks return to enrollment detail after leave or complete.
   Reference app choice.

---

## 4. Protected local confirmation (Q11)

1. **SHOULD** prompt for biometric or device credential on **Approve only** when protected mode
   applies (enrollment policy and/or device preference). **MUST NOT** require that prompt for
   Deny or leave-as-deny once Q11 is implemented. State machine: Approve / Deny; Leave screen.
   **Q11**. Classified **SHOULD**: app-enforced presence, not Keystore-bound proof to the server
   (see developer guide Android notes).
2. **Status:** **Q11 is decided** (#834, 2026-10-15). The reference React Native app may still
   prompt on Deny until the code follow-up ships; clients **SHOULD** follow approve-only now.
   Do not treat “prompt on deny” as protocol-required.

---

## 5. Conflict and expiry on respond (Q8, #895)

1. **MUST** treat HTTP **409** `auth-attempt-state-conflict` as final for that attempt: one
   “no longer active” (or equivalent) sentence, Back only, no Retry that sends another decision.
   Covers not-found, still `PENDING`, terminal, cancelled, superseded, and non-owner signature
   cases per server catalog. State machine: `NoLongerActive`. **Q8** (+ #844 m1).
2. **MUST** treat HTTP **409** `auth-attempt-expired` as final (wall-clock expiry after ownership
   proof; #895). Clients **MAY** use distinct “expired” copy; they **MUST NOT** offer a Retry that
   re-sends a decision for that id. **Q8** amended by #895.
3. **MUST NOT** split `auth-attempt-state-conflict` into multiple user-visible causes for
   anti-enumeration. **Q8**.

---

## 6. Result signature after HTTP 200 (Q2, Q9)

1. **MUST** verify `authAttemptProofTokenResultSignedByIntegration` before showing approved or
   denied. On failure after HTTP 200: **MUST NOT** display APPROVED/DENIED, **MUST** keep the
   decision lock, **MUST NOT** send another decision, Back only. Sentence **SHOULD** point at the
   sign-in where the request was started, without naming a tenant. **Q2**, **Q9**.

---

## 7. Error presentation (G5)

1. **MUST** branch client behaviour on HTTP **status** and RFC 9457 problem **`type`** (under
   `https://ezkey.io/problems/…` when present).
2. **MUST NOT** display problem `detail` (or `title`) verbatim to end users. Map `type` (and
   status) to local / localized copy. `detail` remains operator-safe catalog text for logs and
   diagnostics only.
3. **MUST NOT** treat non-Ezkey Problem Details (for example edge/WAF JSON) as Auth API domain
   copy. Use a generic unreachable / request-failed string.

Full type table: [`MOBILE_DEVELOPER_GUIDE.md`](MOBILE_DEVELOPER_GUIDE.md) § Auth API problem types.

---

## 8. Attempt lifetime (server facts clients rely on)

1. **MUST** assume applied attempt lifetime is **15–120 seconds** per create (`ttlSeconds` floor
   15; effective `min(request|default, 120)` since #895). Default configured property remains
   `ezkey.core.auth-attempt.ttl-seconds` (default **120**).
2. **SHOULD** document stranded `READ` after process death / background during respond as an
   accepted limit until expiry. **Q12**.

---

## 9. Quick citation index

| Topic | Keywords | State machine | #834 |
| --- | --- | --- | --- |
| No phone expiry field / no security countdown | MUST / SHOULD / MAY | Timers / TTL; claim sub-machine | Q1, Q14 |
| Fail-closed pending | MUST | Detail `fail_closed` | Q4 |
| Lock on HTTP 200 before result-sig check | MUST | Approve / Deny | Q2 |
| Lost respond / UNKNOWN | MUST | `UncertainDecision` | Q10 |
| Lock per `authAttemptId` | MUST | Cross-cutting locks | Q13 |
| Leave-as-deny + permanent line | SHOULD | Leave screen | Q5 |
| Failed leave-deny still exits | SHOULD / MUST NOT loop | Leave screen | Q3 |
| Biometric / credential on Approve only | SHOULD (decided; app code may lag) | Approve / Deny | Q11 |
| 409 state-conflict / expired | MUST | `NoLongerActive` | Q8 (+ #895) |
| Bad result signature after 200 | MUST | Approve / Deny | Q2, Q9 |
| Never display `detail` | MUST | Error handling (guide) | G5 / #1052 |

---

## Related documents

| Doc | Role |
| --- | --- |
| [`MOBILE_DEVELOPER_GUIDE.md`](MOBILE_DEVELOPER_GUIDE.md) | Protocol specification + problem-type table |
| [`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) | Reference-app FSM (canonical descriptive model) |
| [`ezkey_dart/README.md`](../ezkey_dart/README.md) | Flutter SDK integration guide |
| [`ENDPOINT.md`](ENDPOINT.md) | Auth / Integration HTTP semantics |
| [#834](https://github.com/mgagp/ezkey/issues/834) | Decision questions Q1–Q14 |
| [#1052](https://github.com/mgagp/ezkey/issues/1052) | Docs corpus consistency that produced this contract |
