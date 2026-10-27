# Vision Note — `V-2026-10-27-third-party-authenticator-integration-kit`
# From reference app to integration kit: guiding a third-party authenticator through `ezkey_dart`

## Metadata

- **ID:** `V-2026-10-27-third-party-authenticator-integration-kit`
- **Status:** `draft`
- **Lane:** program (SDK contract + new canonical docs + first external consumer; not hygiene)
- **Created at:** `2026-10-27`
- **Updated at:** `2026-10-27` (status after #1053)
- **Captured by:** Marc (owner)
- **Priority:** to be set by Marc at triage
- **First consumer:** the Open Authenticator fork `mgagp/openauthenticator` (Flutter, Android first)
- **Does not edit:** vision index, backlog index (left to the PR that lands this note)

## Intent

Ezkey has a lab server, a React Native reference app (`ezkey_mobile/`, Android first), and an
experimental pure-Dart device SDK (`ezkey_dart/`, 0.2.0). What it does not have yet is a
**canonical kit that guides a third-party authenticator** to integrate the Auth API correctly:
not only the wire protocol, but the behavior the reference app learned the hard way (one-shot
claims, leave-as-deny, uncertain decisions, fail-closed trust checks).

This note sets the direction: make the kit a **thin, distilled layer** over the existing canon,
validate it with one real integration (Open Authenticator, Flutter), and grow it only from what
that integration actually needs.

## Motivation

- The FSM (`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`) recently surfaced many edge cases
  (#834 Questions 1–14). Those lessons live in a document written for the reference app's own
  code (hooks, refs, line numbers). A third party cannot tell which rows are **protocol
  obligations** and which are **reference-app choices**.
- `ezkey_dart` is the first SDK that targets a third-party authenticator stack. Using it for a
  real integration is the cheapest way to find out whether it is complete and easy to adopt.
- Without a distillation rule, every evolution of the reference app would have to be copied by
  hand into the third-party guidance, and the two would drift.

## What already exists (do not duplicate)

| Concern | Canonical place today | Kit role |
| --- | --- | --- |
| Wire protocol, signed payloads, verification order, error catalog | `docs/MOBILE_DEVELOPER_GUIDE.md` (already aimed at third-party clients), `docs/ENDPOINT.md`, `docs/CRYPTO.md`, the two `*_SIGNATURE_PAYLOAD.md` | Link only |
| Dart integration (API, storage hooks, key store recipes, pitfalls) | `ezkey_dart/README.md` | Link only; fix drift (below) |
| Reference-app behavior, states, guards | `ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md` (+ strategic orientation, functional flows) | Source of the distillation |
| Reference-app screens, identity vocabulary | `ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md`, `MOBILE_POSITIONING.md` | Source of the UX guidance |

The only missing piece was an **authenticator-agnostic behavioural contract**: what any
authenticator must do between the API calls, regardless of stack and screen layout. It now exists
as [`docs/CLIENT_BEHAVIOUR_CONTRACT.md`](../../../docs/CLIENT_BEHAVIOUR_CONTRACT.md) (#1053).

## Proposed shape of the kit

1. **Behavioral contract** (new, one document, authenticator-agnostic). Each rule is classified:
   - **MUST** — protocol-imposed; violating it breaks security or leaves the integrating
     application waiting (e.g. never display context before the pending signature verifies;
     one claim owner per enrollment; no second decision after a respond was sent).
   - **SHOULD** — strong recommendation proven by the reference app (e.g. leave-as-deny,
     biometric on Approve only, no countdown on the phone).
   - **MAY / reference choice** — layout and navigation (e.g. Home → Detail → Pending).
   Each rule cites its FSM row or #834 question instead of restating the rationale.
2. **Conformance checklist** — one table: rule → how `ezkey_dart` supports it (API, or "app
   responsibility") → test that proves it (SDK unit test, e2e case, or app test).
3. **UX interaction guide** — written last, from the Open Authenticator experience: the minimum
   screens and copy (enrollment confirmation, challenge entry, prompt, outcome, "no longer
   active", "uncertain"), using the `MOBILE_POSITIONING.md` identity vocabulary.

Placement proposal: the contract and checklist in root `docs/` (they are authenticator-agnostic,
next to `MOBILE_DEVELOPER_GUIDE.md`); `ezkey_dart/README.md` links to them for the Dart specifics.

## Gap inventory and status

Read-only review on 2026-10-27, re-verified and filed as
[#1052](https://github.com/mgagp/ezkey/issues/1052). The documentation side closed with
[#1053](https://github.com/mgagp/ezkey/pull/1053), which added
[`docs/CLIENT_BEHAVIOUR_CONTRACT.md`](../../../docs/CLIENT_BEHAVIOUR_CONTRACT.md).

| # | Finding | Docs | Code follow-up |
| --- | --- | --- | --- |
| G1 | Endpoint parity: the SDK covers all five device endpoints; unsigned `GET /public/instance-info` is intentionally unused. | — | none |
| G2 | A `respond` lost after send must be an UNKNOWN outcome (lock, Back only, no second decision); the SDK reports it as a retryable `EzkeyNetworkException`, and ambiguous 5xx as retryable too. | Done (contract §2) | Done in `ezkey_dart` 0.3.0: `EzkeyRespondOutcomeUnknownException` (pending PR) |
| G3 | Per-attempt decision lock and leave-as-deny. | Done (contract §2 MUST, §3 SHOULD) | Decided: the SDK owns the lock. Done in `ezkey_dart` 0.3.0: `PendingAuthAttempt.decisionState` / `canDecide` (pending PR) |
| G4 | Biometric on Approve only (Q11). | Done (contract §4) | Reference app only (`usePendingAuth.ts`); not an SDK change |
| G5 | Error copy: branch on `type` and status, never display `detail`. | Done (contract §7) | none |
| G6 | Problem catalog: 17 of 19 types mapped (`resource-not-found`, `method-not-allowed` missing). | Done (developer guide type table) | Done in `ezkey_dart` 0.3.0: both types mapped, test pins 19 types (pending PR) |
| G7 | FSM drift (#833 merged; lifetime 15–120 s per #895). | Done (FSM + HTML twin, contract §8) | none |

## Stack fit for the first consumer (Open Authenticator, Flutter)

- **Dependencies:** `ezkey_dart` adds `cryptography`, `pointycastle` and `unorm_dart` (new to the
  app); `http` and `args` already match. No version conflict found in the app's lockfile. In the
  fork this is the single planned exception to its upstream-alignment rule.
- **Platform key store is the real work.** The SDK defines `EzkeyDeviceKeyStore` but ships no
  Flutter implementation; an Android Keystore platform channel (StrongBox first, per README §4.2)
  is needed. Candidate for a small reusable plugin later, not now.
- **Secure storage:** the README suggests `flutter_secure_storage`; Open Authenticator uses
  `simple_secure_storage`. The contract should stay storage-agnostic ("platform secure storage,
  never plain preferences").
- **Polling:** the reference app is strictly user-initiated pull. The SDK's `pollPending` /
  `watchPending` fit an approvals screen; the contract should state the bound (visible screen
  only, no background loop).

## Gradual path (milestones)

Each step is small, reviewable, and leaves the corpus consistent on its own.

1. **This note** — direction and gap inventory. *Done.*
2. **Doc hygiene and behavioural contract v1** — *done* in #1053 (`CLIENT_BEHAVIOUR_CONTRACT.md`,
   RFC 2119 keywords with a server / client-invariant **Basis** on every MUST).
3. **SDK code follow-up** in `ezkey_dart`: UNKNOWN outcome for a `respond` sent without an HTTP
   answer (contract §2.3) and for ambiguous 5xx (§2.4, keeping 503 retryable), the two unmapped
   problem types, and SDK tests for each. *Done in the working tree as `ezkey_dart` 0.3.0,
   pending its PR.* Once it lands, the docs outside the SDK folder that still describe the SDK
   gap need a touch: `docs/CLIENT_BEHAVIOUR_CONTRACT.md` § Known gap (Dart part) and the SDK
   column of the `docs/MOBILE_DEVELOPER_GUIDE.md` problem-type table. Then the **conformance
   checklist** (contract rule → SDK API or app duty → proving test).
4. **Tracer bullet in Open Authenticator** (`TB-*`): Android key store channel, enrollment, one
   approval, outcome screens, against the local stack. Every friction point becomes a contract or
   SDK change, not a fork-side workaround.
5. **UX interaction guide** from step 4's real screens; then decide whether the kit needs more.

## Keeping it maintainable

- **One canonical place per concept.** The contract cites FSM rows / #834 questions; it never
  restates their rationale. The FSM stays canonical for the reference app.
- **Drift trigger, not drift ceremony.** One line in `ezkey_mobile/docs/README.md` (and the FSM
  header): a change to a FSM row cited by the contract updates the contract in the same PR.
- **Automate only the cheap checks.** Shared golden payload vectors already exist; add the
  problem-catalog parity test (G6). The conformance checklist links tests instead of describing
  them.
- **No iOS commitment.** Same posture as the reference app: Android first; iOS notes stay as
  sketches (README §4.2) until iOS becomes a milestone.

## Open questions for Marc

Answered by #1053: error `detail` is never displayed (contract §7); the contract lives in root
`docs/`; leave-as-deny is a **SHOULD** (contract §3.1).

None open. G3 was decided on 2026-10-27: the SDK owns the decision lock (Marc).

## Promotion criteria

- Step 3 merged, including the doc touch it triggers outside the SDK folder.
- Step 4's tracer bullet completes enrollment + approve + deny on a real Android device against the
  local stack using only the kit and `ezkey_dart` (no undocumented knowledge).
- Then canonize: the contract becomes the durable doc, this note moves to `promoted`.

## Related documents

- [`../../../ezkey_dart/README.md`](../../../ezkey_dart/README.md)
- [`../../../docs/MOBILE_DEVELOPER_GUIDE.md`](../../../docs/MOBILE_DEVELOPER_GUIDE.md)
- [`../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md)
- [`../../../ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md`](../../../ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md)
- [`../../../ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md`](../../../ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md)
- [`../../../ezkey_mobile/docs/MOBILE_POSITIONING.md`](../../../ezkey_mobile/docs/MOBILE_POSITIONING.md)
- [`../../../docs/CLIENT_BEHAVIOUR_CONTRACT.md`](../../../docs/CLIENT_BEHAVIOUR_CONTRACT.md)
- Issues / PRs: #833, #834, #844, #895, #994 (`ezkey_dart` 0.2.0), #1052, #1053
