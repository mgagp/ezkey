<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Client behaviour contract (application-independent)

| | |
| --- | --- |
| **Version** | 2026-10-27 |
| **Status** | Normative companion for any Auth API client (third-party Flutter, React Native reference app, Demo Device, or other) |

Protocol wire formats remain in [`MOBILE_DEVELOPER_GUIDE.md`](MOBILE_DEVELOPER_GUIDE.md),
[`ENDPOINT.md`](ENDPOINT.md), and the signature payload docs. The React Native finite-state model is
[`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md).
Flutter SDK surface: [`ezkey_dart/README.md`](../ezkey_dart/README.md).

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT",
"RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in
[RFC 2119](https://datatracker.ietf.org/doc/html/rfc2119) and
[RFC 8174](https://datatracker.ietf.org/doc/html/rfc8174) when, and only when, they appear in all
capitals, as shown here.

Each rule cites the state-machine topic and the [#834](https://github.com/mgagp/ezkey/issues/834)
question number (**Q*n***). Rationales live in those sources; do not duplicate them here. Each rule
also carries a **Basis** label:

| Basis | Meaning |
| --- | --- |
| **server** | Enforced by Auth API / core service behaviour (citeable in Java). A non-conforming client still cannot defeat the server guarantee. |
| **client invariant** | Required for a correct, honest client UX or local trust posture. The wire protocol alone does not force it; omitting it yields a broken or misleading client. |

| Keyword | Meaning here |
| --- | --- |
| **MUST** / **MUST NOT** | Required: either **server**-enforced, or a **client invariant** explicitly labelled as such. |
| **SHOULD** / **SHOULD NOT** | Strong recommendation proven by the reference app / decided #834 answers; not strictly imposed by the wire protocol. |
| **MAY** | Reference-app product choice (navigation, layout, copy placement). Other clients may differ. |

When uncertain whether the protocol forces a behaviour, this contract uses **SHOULD** and says why.

---

## 1. Pending claim and display (Q1, Q4, Q14)

1. **MUST** treat `POST /api/v1/auth-attempts/pending` as a one-shot claim: a successful `200` with a
   verified integration signature moves the attempt to `READ` and will not be delivered again.
   **Basis: server** (`AuthAttemptPendingService` claim path). State machine: claim sub-machine.
   Protocol: [`ENDPOINT.md`](ENDPOINT.md) § pending.
2. **MUST NOT** show pending context (`contextTitle` / `contextMessage` / challenge flag) until the
   pending integration signature verifies. On fail-closed, **MUST NOT** send a refusal for that
   claim. **Basis: client invariant**. State machine: Detail `fail_closed`. **Q4**.
3. **MUST NOT** use a device-local countdown or an invented `expiresAt` as a security control. The
   device pending body does not carry expiry fields. **Basis: client invariant**. State machine:
   Timers / TTL. **Q1**, **Q14**.
4. **SHOULD** leave remaining-time display to the integrating application’s create/wait UI.
   **Q1**.
5. **MAY** omit any phone-side timer chrome entirely (reference app choice). **Q1**.

---

## 2. Decision lock (Q2, Q10, Q13)

**Server guarantee (stated once):** the Auth API already ensures at most one successful
approve/deny transition for an attempt (conditional `UPDATE … WHERE status = READ`, plus terminal
409 on a second respond). A client that sends a second decision does not create a second accept; it
gets **409**. The **client lock** below is therefore a **client invariant**: it avoids a misleading
UI (Retry / leave-as-deny that looks actionable) and an unnecessary second HTTP call — it does
**not** create the single-decision guarantee.

1. **MUST** remember the `authAttemptId` after any `respond` exchange that completed or became
   undecidable at the HTTP layer, and **MUST NOT** send a second approve/deny for that same id
   (including leave-as-deny). **Basis: client invariant**. State machine:
   `serverDecisionAttemptIdRef` / Pending Auth FSM. **Q2**, **Q10**, **Q13**.
2. **MUST** set that lock when `respond` returns HTTP `200`, **before** local verification of the
   result integration signature (including malformed bodies and failed integrity checks on that
   200). **Basis: client invariant**. State machine: Approve / Deny table. **Q2**, **Q9**.
3. **MUST** treat a `respond` call that was sent but produced **no HTTP response** (timeout,
   transport cut, undecidable) as an **UNKNOWN** outcome: lock the attempt id, show an uncertain
   result (not a failure), offer Back only, and **MUST NOT** send another decision for that id.
   **Basis: client invariant**. State machine: `UncertainDecision`. **Q10**.
4. **SHOULD** treat an ambiguous **5xx** on `respond` the same as UNKNOWN (lock; Back only; no
   second decision). This is an **extension of Q10** (the state machine’s Q10 text covers “no HTTP
   response” only; ambiguous 5xx after send has the same client uncertainty). **503**
   `integration-signing-unavailable` remains **retryable**, but a retry **MAY** then return
   terminal **409** (for example when a wrong challenge already committed `INVALID` before signing
   failed — `AuthAttemptRespondService` FAILED-response path). Do **not** treat 503 as proof that
   no server-side commitment occurred. Classified **SHOULD** because the protocol does not expose a
   single “committed?” signal for every 5xx.
5. After `respond` was sent, HTTP **4xx** or **429** **with a problem/empty body the client can
   classify** keep catalog retryability (not UNKNOWN): for example validation **400** or rate-limit
   **429**. Pre-send failures (biometric cancel, local validation) stay retryable without lock.
   State machine: Approve / Deny table.
6. **MUST** scope the lock per `authAttemptId` so a later pending attempt with a different id is
   not blocked. **Basis: client invariant**. **Q13**.

### Known gap (code follow-up, not this docs PR)

Neither current client fully implements rules 2.3–2.4:

- **Dart SDK** (`ezkey_dart`): lost `respond` transport → `EzkeyNetworkException` with
  `retryable: true`; many 5xx kinds also `retryable`.
- **React Native reference app** (`usePendingAuth.ts`): locks on transport-without-HTTP-response
  (Q10) and on non-retryable catalog errors, but does **not** lock on retryable 5xx (including
  ambiguous 5xx after send).

Docs state the contract; SDK and app behaviour changes are separate code follow-ups (#1052 plan).

---

## 3. Leave screen (Q3, Q5)

1. **SHOULD** send `respond(accepted=false)` when the user leaves a screen that still holds a
   loaded, unlocked pending attempt (same wire path as Deny). Delivered with #833 (merged
   2026-10-16). State machine: Leave screen. **Q5**. Classified **SHOULD** (not MUST): the Auth
   API does not require a device-initiated cancel; an abandoned `READ` row expires by server TTL.
2. **SHOULD** keep a permanent, always-visible line that leaving declines the request (no
   confirmation dialog). **Q5**. Classified **SHOULD**: UX honesty, not a wire requirement.
3. **SHOULD** still allow navigation if leave-as-deny fails (network), with one short message.
   Inside that recommendation, clients **MUST NOT** auto-retry leave-deny in a loop (**Basis:
   client invariant** nested under SHOULD). **Q3**.
4. **MAY** choose how navigation stacks return to enrollment detail after leave or complete.
   Reference app choice.

---

## 4. Protected local confirmation (Q11)

1. **SHOULD** prompt for biometric or device credential on **Approve only** when protected mode
   applies (enrollment policy and/or device preference). Once Q11 is implemented, clients
   **MUST NOT** require that prompt for Deny or leave-as-deny (**Basis: client invariant** nested
   under SHOULD). State machine: Approve / Deny; Leave screen. **Q11**. Classified **SHOULD**:
   app-enforced presence, not Keystore-bound proof to the server (see developer guide Android
   notes).
2. **Status:** **Q11 is decided** (#834, 2026-10-15). The reference React Native app still prompts
   on Deny today (`usePendingAuth.ts` passes `requiresProtectedApproval` into `signForRespond` for
   both accept and reject) until the code follow-up ships; clients **SHOULD** follow approve-only
   now. Do not treat “prompt on deny” as protocol-required.

---

## 5. Conflict and expiry on respond (Q8, #895)

1. **MUST** treat HTTP **409** `auth-attempt-state-conflict` as final for that attempt: one
   “no longer active” (or equivalent) sentence, Back only, no Retry that sends another decision.
   Covers not-found, still `PENDING`, terminal, cancelled, superseded, and non-owner signature
   cases per server catalog. **Basis: server** (`AuthAttemptRespondService` state gates /
   conditional update). State machine: `NoLongerActive`. **Q8** (+ #844 m1).
2. **MUST** treat HTTP **409** `auth-attempt-expired` as final (wall-clock expiry after ownership
   proof; #895). Clients **MAY** use distinct “expired” copy; they **MUST NOT** offer a Retry that
   re-sends a decision for that id. **Basis: server**. **Q8** amended by #895.
3. Presentation note (not a separate protocol obligation): the server already collapses many
   inactive causes into a **single** `auth-attempt-state-conflict` type. Clients **SHOULD** show
   one sentence for that type. They need not invent further cause-splitting UX for causes the
   server already unified. Distinct copy for `auth-attempt-expired` remains **MAY** (rule 5.2).

---

## 6. Result signature after HTTP 200 (Q2, Q9)

1. **MUST** verify `authAttemptProofTokenResultSignedByIntegration` before showing approved or
   denied. On missing signature, malformed body, or failed integrity check after HTTP 200:
   **MUST NOT** display APPROVED/DENIED, **MUST** keep the decision lock, **MUST NOT** send another
   decision, Back only. Sentence **SHOULD** point at the sign-in where the request was started,
   without naming a tenant. **Basis: client invariant**. **Q2**, **Q9**.

---

## 7. Error presentation (G5)

1. **MUST** branch client behaviour on HTTP **status** and RFC 9457 problem **`type`** (under
   `https://ezkey.io/problems/…` when present). **Basis: client invariant**.
2. **MUST NOT** display problem `detail` or `title` verbatim to end users. Map `type` (and status)
   to local / localized copy. Catalog strings are operator-safe and may be labelled client-facing
   in server Javadoc, but **the client must not depend on that text** (the same `type` can carry
   different `title`/`detail` across handlers) and **must not display it**. **Basis: client
   invariant**.
3. **MUST NOT** treat non-Ezkey Problem Details (for example edge/WAF JSON) as Auth API domain
   copy. Use a generic unreachable / request-failed string. **Basis: client invariant**.

Full type table: [`MOBILE_DEVELOPER_GUIDE.md`](MOBILE_DEVELOPER_GUIDE.md) § Auth API problem types.

---

## 8. Attempt lifetime (server facts clients rely on)

State the three numbers separately; do not collapse them into one range:

| Quantity | Value | Source |
| --- | --- | --- |
| Per-request `ttlSeconds` floor | **15** (below → 400) | `AuthAttemptService.REQUEST_TTL_MIN_SECONDS` |
| Hard ceiling (request clamp + applied create) | **120** | `REQUEST_TTL_MAX_SECONDS`; `min(request\|default, 120)` |
| Configured property `ezkey.core.auth-attempt.ttl-seconds` | Bean Validation **30–600**; default **120**; startup clamp **>120 → 120** | `EzkeyCoreProperties` + `#895` M2 |

Effective **configured default** after startup clamp therefore lies in **30–120**. Applied lifetime
for a create is always in **15–120** when a request supplies `ttlSeconds`, or the clamped default
when omitted.

1. **MUST** assume those bounds when reasoning about stranded `READ` windows and wait timeouts.
   **Basis: server**.
2. **SHOULD** document stranded `READ` after process death / background during respond as an
   accepted limit until expiry. **Q12**.

---

## 9. Quick citation index

| Topic | Keywords | Basis | State machine | #834 |
| --- | --- | --- | --- | --- |
| One-shot pending claim | MUST | server | claim sub-machine | — |
| Fail-closed pending display | MUST NOT | client invariant | Detail `fail_closed` | Q4 |
| No security countdown on phone | MUST NOT | client invariant | Timers / TTL | Q1, Q14 |
| Lock on HTTP 200 before result-sig check | MUST | client invariant | Approve / Deny | Q2 |
| Lost respond / UNKNOWN | MUST | client invariant | `UncertainDecision` | Q10 |
| Ambiguous 5xx after send | SHOULD (extension of Q10) | — | — | Q10 ext. |
| Lock per `authAttemptId` | MUST | client invariant | Cross-cutting locks | Q13 |
| Leave-as-deny + permanent line | SHOULD | — | Leave screen | Q5 |
| Failed leave-deny still exits | SHOULD / MUST NOT loop | client invariant (MUST NOT) | Leave screen | Q3 |
| Biometric / credential on Approve only | SHOULD (decided; app code may lag) | — | Approve / Deny | Q11 |
| 409 state-conflict / expired | MUST | server | `NoLongerActive` | Q8 (+ #895) |
| Bad / missing result signature after 200 | MUST | client invariant | Approve / Deny | Q2, Q9 |
| Do not depend on / display `detail` | MUST NOT | client invariant | Error handling | G5 / #1052 |
| TTL 15 / 120 / property 30–600 | MUST | server | Timers / TTL | Q12 |

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
