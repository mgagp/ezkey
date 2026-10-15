<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# Ezkey Mobile — Application State Machine (canonical)

## Purpose

This document is the **canonical finite-state model** of the React Native reference app’s
decision points: enrollment (QR → bind → verify), pending-auth claim, approve/deny, leaving
`PendingAuthScreen`, errors, locks, TTL, and re-entry / duplicate-tap guards.

It is derived from **actual code** (file:line citations). Protocol wire formats and Auth API
contracts remain canonical in root docs (`docs/ENDPOINT.md`, `docs/CRYPTO.md`, signature payload
docs). Operational flow narration lives in
[MOBILE_FUNCTIONAL_FLOWS.md](MOBILE_FUNCTIONAL_FLOWS.md); screen inventory in
[MOBILE_SCREENS_AND_WIREFLOWS.md](MOBILE_SCREENS_AND_WIREFLOWS.md). **When behavior and those docs
disagree, this FSM + the cited code win until the owning docs are updated.**

Interactive complement (optional): [mobile-app-state-machine.html](mobile-app-state-machine.html)
(self-contained; no CDN).

## Baseline and pending #833

| Baseline | Meaning |
| --- | --- |
| **main** | Enrollment FSM, claim/pending, approve/deny respond path, pull model (no background poll). |
| **Post-#833** (PR [#833](https://github.com/mgagp/ezkey/pull/833), branch `cursor/admin-rate-limit-passwordless-831-f1f9`, head `fcc31fbf`) | Leaving `PendingAuthScreen` with a loaded attempt → Auth API `respond(accepted=false)` (same as Deny). Guards: `serverDecisionSentRef`, `respondInFlightRef` / `isRespondInFlight`. Backend approve/deny uses conditional `UPDATE … WHERE status = READ`; cancel uses `expireIfPendingOrRead`. |

**This document describes post-#833 behavior.** Until #833 merges, main still leaves abandoned
attempts `PENDING`/`READ` until TTL (see historical UX comment on main
`PendingAuthScreen.tsx` L12–14). There is **no mobile protocol or Auth API surface change** for
devices: same `pending` / `respond` endpoints. `POST /api/v1/admin/auth/passwordless-cancel` is
Admin API (Admin UI); **mobile does not call it**.

Decision (repo-as-memory): Leave-with-loaded-attempt is Deny via Auth API respond, not cancel and
not TTL-wait — reason: unblock Admin passwordless-wait when the user abandons the phone screen;
issue [#834](https://github.com/mgagp/ezkey/issues/834) / PR #833.

---

## Notation

| Symbol | Meaning |
| --- | --- |
| **State** | Named UI/logic mode the user or hook is in (not necessarily a single `enum`). |
| **Event** | User action, navigation event, or async completion. |
| **Guard** | Condition that must hold for the transition (or that blocks it). |
| **Effect** | Side effect: Auth API call, crypto, local storage, navigation. |
| **TTL** | Server auth-attempt lifetime (`ezkey.core.auth-attempt.ttl-seconds`, default **120 s**). |

Server auth-attempt statuses (backend): `PENDING` → (device claims) `READ` → `ACCEPTED` |
`REJECTED` | `INVALID` | `EXPIRED`. Mobile does not model those as local enums; it holds an
in-memory `PendingAttempt` or not.

---

## 1. Enrollment wizard FSM (QR → bind → verify)

**Owner code:** `app/hooks/useEnrollmentWizard.ts`, `app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx`.

### States

| State | How recognized in code | User sees |
| --- | --- | --- |
| `ScanIdle` | `!draft`, `!isBinding`, `!scannerVisible` | Scan CTA; optional F2a bypass in debug |
| `ScannerOpen` | `scannerVisible === true` | Camera modal |
| `Binding` | `isBinding === true` | Primary CTA “Binding…”; draft cleared for this attempt |
| `BindError` | `bindError` or `cameraError` set, still `!draft` | Error banner on scan stage |
| `DraftReady` | `draft` set, `!isSubmitting` | Verify stage: 6-digit challenge + info card |
| `Verifying` | `isSubmitting === true` | Primary “Finishing…” |
| `ChallengeError` | `challengeError` set while draft may still exist | Inline challenge error |
| `Done` | Success path calls `popToTop()` after persist | Returns to Home |

### Events, guards, effects

| Event | From | To | Guard | Effect / API |
| --- | --- | --- | --- | --- |
| Tap primary (no draft) | `ScanIdle` / `BindError` | `ScannerOpen` | Camera permission granted (else `cameraError`) | Opens scanner (`handlePrimary` L452–468) |
| QR scanned / F2a seed | `ScannerOpen` | `Binding` | Parsed id + proof token; Auth URL or global base URL | `enrollmentsApi.bind` (`performBinding` L243–314) |
| Bind success + sig OK | `Binding` | `DraftReady` | `integrationKeyAlgorithm` Ed25519; bind signature verifies | In-memory `draft` only — not persisted yet (L280–308) |
| Bind fail / bad sig / algo | `Binding` | `BindError` | — | Sets `bindError`; closes scanner |
| Duplicate bind while binding | `Binding` | `Binding` | `isBinding` | Early return — no second bind (L245–247) |
| Enter 6 digits + primary | `DraftReady` | `Verifying` | Challenge length === 6 | Keystore ensure → sign → `enrollmentsApi.verify` → verify-result sig → optional signed instance-info → `saveEnrollment` → `popToTop` (L319–429) |
| Verify / persist fail | `Verifying` | `ChallengeError` | — | Deletes key pair if created; clears challenge (L420–426) |
| Secondary / cancel with draft | `DraftReady` | leave wizard | `!isBinding && !isSubmitting` | Clears draft; `popToTop` (L480–488) |
| Back with draft | `DraftReady` | `ScanIdle` | `!isBinding && !isSubmitting` | Clears draft/challenge; stays on wizard (L494–504) |
| Back without draft | `ScanIdle` | previous screen | `!isBinding && !isSubmitting` | `goBack()` (L505–506) |
| Back while binding/verifying | any | same | `isSubmitting \|\| isBinding` | **No-op** — cannot leave mid-flight (L496–498) |

### User stories (enrollment)

1. **Happy path:** As a user, I scan the Admin QR, see who I’m enrolling for, enter the 6-digit code, and land on Home with a new enrollment card.
2. **Wrong camera / missing URL:** As a user, if the QR has no Auth URL and the app has no global base URL, bind fails with a clear error and I can re-scan.
3. **Abandon after bind:** As a user, I can tap Back after a successful bind to discard the draft without persisting keys; I am not left half-enrolled.

```mermaid
stateDiagram-v2
  [*] --> ScanIdle
  ScanIdle --> ScannerOpen: primary / camera OK
  ScanIdle --> BindError: camera denied
  ScannerOpen --> Binding: QR / seed parsed
  Binding --> DraftReady: bind + sig OK
  Binding --> BindError: bind/sig/algo fail
  BindError --> ScannerOpen: primary retry
  DraftReady --> Verifying: primary + 6 digits
  Verifying --> Done: verify + persist OK
  Verifying --> ChallengeError: verify/persist fail
  ChallengeError --> Verifying: primary retry
  DraftReady --> ScanIdle: back (clear draft)
  DraftReady --> [*]: secondary cancel → Home
  Done --> [*]
  note right of Binding
    Guard: isBinding blocks re-entry
  end note
  note right of DraftReady
    Guard: isBinding/isSubmitting block back
  end note
```

---

## 2. Enrollment Detail → claim pending

**Owner code:** `EnrollmentDetailScreen.tsx` (`handleCheckPending`), shared
`claimPendingAttempt.ts`.

Pull model: **no background polling**. User must tap **Check pending** (or Check again on
Pending Auth).

### States (Detail)

| State | Recognition | Notes |
| --- | --- | --- |
| `DetailIdle` | Enrollment loaded, not checking | Shows identity + Check pending |
| `DetailChecking` | `isCheckingPending` | Button busy |
| `DetailFeedback` | `inlineFeedback` set | No pending / fail-closed / network message |
| `DetailUnusable` | Broken enrollment (MOB-015) | No Check pending |

### Transitions

| Event | From | To | Guard | Effect |
| --- | --- | --- | --- | --- |
| Check pending | `DetailIdle` | `DetailChecking` | Enrollment present; `!isCheckingPending` (L104–106) | Persist last-checked timestamp (best-effort); `claimPendingAttempt` |
| Claim `none` | `DetailChecking` | `DetailFeedback` | HTTP empty / 204 | Stay on Detail; “no pending” |
| Claim `fail_closed` | `DetailChecking` | `DetailFeedback` | Missing key / bad sig / malformed | Stay; fail-closed copy |
| Claim `attempt` | `DetailChecking` | navigate `PendingAuth` | Signature verified | Pass `initialAttempt` in route params (L139–142) |
| Network/crypto throw | `DetailChecking` | `DetailFeedback` | — | `extractErrorMessage` |
| Double tap Check | `DetailChecking` | same | `isCheckingPending` | Early return (L104–106) |

### Claim sub-machine (`claimPendingAttempt`)

| Step | Effect |
| --- | --- |
| `start` → `after_ensure` | `requireEnrollmentKeyPair` |
| Mint + sign proof | Fresh `deviceProofToken` + device signature |
| `POST …/auth-attempts/pending` | Auth API claim (server `PENDING`→`READ` on success) |
| Empty body | `{ kind: 'none' }` |
| Verify integration sig | Fail-closed on missing key / invalid sig / malformed |
| Success | `{ kind: 'attempt', attempt }` — **no `expiresAt` stored locally** (open question) |

```mermaid
stateDiagram-v2
  [*] --> DetailIdle
  DetailIdle --> DetailChecking: Check pending
  DetailChecking --> DetailFeedback: none / fail_closed / error
  DetailChecking --> PendingAuthScreen: attempt claimed
  DetailFeedback --> DetailChecking: Check pending again
  DetailUnusable --> [*]: remove / back
```

---

## 3. Pending Auth FSM (load → decide → leave)

**Owner code:** `usePendingAuth.ts`, `PendingAuthScreen.tsx` (post-#833 leave listener).

### UI / hook states

| State | Recognition | User sees |
| --- | --- | --- |
| `EnrollmentLoading` | `isEnrollmentLoading` | Spinner |
| `LoadingPending` | `loading` | Spinner “Contacting Auth API…” |
| `Empty` | `showEmptyState` | No pending + Check again |
| `GlobalError` | `globalError` set, no attempt | Error + Try again (+ optional debug panel) |
| `Ready` | `attempt` set, `!isProcessing` | Context card; Approve / Deny; optional 2-digit challenge |
| `Responding` | `isProcessing` / `respondInFlightRef` | Buttons disabled; “Sending…” on approve |
| `Completed` | After verified respond + `setRecentAuthResult` | Navigates back to Detail |

Internal refs (post-#833):

| Ref | Set when | Purpose |
| --- | --- | --- |
| `serverDecisionSentRef` | Immediately after Auth API `respond` HTTP success (before local signature checks) | Skip leave-as-deny and further respond (`hasSubmittedServerDecision`) — L448–449 on #833 |
| `respondInFlightRef` | Synchronously at start of `handleRespond`, cleared in `finally` | Block leave-as-deny racing an in-flight approve (`isRespondInFlight`) — L407–408, L511 |

### Auto-load on mount

| Condition | Behavior | Citation (#833 / main same idea) |
| --- | --- | --- |
| `initialAttempt` present | Skip auto `loadPendingAttempt`; mark enrollment id in `autoLoadEnrollmentRef` | usePendingAuth auto-load effect |
| No `initialAttempt` | Load once per enrollment id | Same |
| Re-entry same enrollment | `autoLoadEnrollmentRef` prevents duplicate auto-load | L353–357 |

### Approve / Deny (`handleRespond`)

| Event | Guard | Effect |
| --- | --- | --- |
| Approve / Deny press | `enrollment`, `attempt`, `!isProcessing`, `!serverDecisionSentRef`, `!respondInFlightRef` | Else skip (duplicate / in-flight) |
| Approve only | If `challengeRequired`, challenge length === 2 (`AUTH_CHALLENGE_LENGTH`) | Else form error; no API |
| Deny | Challenge **not** required locally; server also skips challenge on reject | Same `respond` path with `accepted=false` |
| Protected mode | `requiresProtectedApproval` | Biometric / device-credential then `signForRespond` |
| HTTP `respond` OK | — | Set `serverDecisionSentRef`; verify respond-result signature; `setRecentAuthResult`; `goBack` / navigate Detail |
| HTTP / crypto fail | — | `globalError`; stay on screen; `respondInFlightRef` cleared |
| Buttons while processing | `disabled={isProcessing}` | UI lock; refs close the sync gap before re-render |

API: `POST /api/v1/auth-attempts/respond` (Auth API). **Not** Admin cancel / passwordless-cancel.

### Leave screen (post-#833) — critical

`navigation.addListener('beforeRemove')` on `PendingAuthScreen` (#833 L84–105):

| Condition | Behavior |
| --- | --- |
| No `attempt` (empty / error / still loading) | Allow leave; **no** respond |
| `isProcessing` or `isRespondInFlight()` or `hasSubmittedServerDecision()` | Allow leave; **no** second deny |
| Else (`Ready` with loaded attempt) | `preventDefault` → `handleRespond(false)` (identical Deny) |
| Deny succeeds | Hook already navigates via `handleReturnToEnrollmentDetail` |
| Deny fails | Still `navigation.dispatch(e.data.action)` so the user is **not trapped** |

**Pre-#833 (main):** leave did not call respond; attempt stayed `READ`/`PENDING` until TTL scheduler.

### Timers / TTL

| Layer | Behavior |
| --- | --- |
| Server | Attempt `expiresAt` from `ezkey.core.auth-attempt.ttl-seconds` (default 120). Scheduler in Admin API process marks `EXPIRED`. |
| Mobile UI | **No countdown**; `PendingAttempt` does not carry `expiresAt` (types.ts). Respond after expiry → Auth API state conflict / failure surfaced as error. |
| Mobile wait | No client-side timeout loop on Pending Auth; user must act or leave. |

### Backend race (post-#833, context for mobile)

| Path | Mechanism | Mobile impact |
| --- | --- | --- |
| Approve/deny | `updateStatusIfCurrent(id, READ → ACCEPTED\|REJECTED)`; 0 rows → `AuthAttemptStateConflictException` → HTTP **409** | Mobile maps to user-facing error; no special 409 UI |
| Admin / Integration cancel | `expireIfPendingOrRead(PENDING\|READ → EXPIRED)` | If cancel wins, mobile respond gets 409 / conflict |
| Admin passwordless-cancel | Admin API only | Not used by mobile |

```mermaid
stateDiagram-v2
  [*] --> EnrollmentLoading
  EnrollmentLoading --> LoadingPending: no initialAttempt
  EnrollmentLoading --> Ready: initialAttempt
  LoadingPending --> Empty: none
  LoadingPending --> GlobalError: fail_closed / network
  LoadingPending --> Ready: attempt
  Empty --> LoadingPending: Check again
  GlobalError --> LoadingPending: Try again
  Ready --> Responding: Approve / Deny / Leave-as-deny
  Responding --> Completed: respond OK + result sig OK
  Responding --> GlobalError: respond / sig / network fail
  Completed --> [*]
  Ready --> [*]: leave without attempt guards\n(empty/error path)
  note right of Responding
    respondInFlightRef sync
    serverDecisionSentRef after HTTP OK
  end note
```

### User stories (pending auth)

1. **Approve:** As a user, I open Check pending, see the request context, enter the 2-digit code if asked, approve (with biometric if protected), and return to Detail with an “approved” summary.
2. **Deny button:** As a user, I tap Deny and the waiting Admin login unblocks as rejected without needing TTL.
3. **Leave as deny (post-#833):** As a user, I swipe back / hardware back with a loaded request; the app sends the same Deny respond so I do not leave the server waiting until TTL.
4. **Double tap:** As a user, rapid Approve taps do not send two responds (`respondInFlightRef` / `isProcessing` / `serverDecisionSentRef`).
5. **Leave during approve:** As a user, if I leave while approve is in flight, the app does **not** send a competing deny; the in-flight respond continues.

---

## 4. Cross-cutting locks and re-entry

| Concern | Mechanism | Location |
| --- | --- | --- |
| Enrollment bind re-entry | `isBinding` early return | useEnrollmentWizard `performBinding` |
| Enrollment verify re-entry | `isSubmitting`; back no-op | `finalizeEnrollment` / `handleBack` |
| Detail check re-entry | `isCheckingPending` | EnrollmentDetailScreen |
| Pending load | `loading` UI; auto-load once per enrollment | usePendingAuth |
| Respond re-entry | `isProcessing` + refs (post-#833) | `handleRespond` |
| Leave vs approve race | `respondInFlightRef` set before any `await` | #833 m2 |
| Second deny after success | `serverDecisionSentRef` | #833 / #831 |
| Unusable enrollment | No Check pending | MOB-015 Detail / Home |
| Protected approve | Local auth before sign | `signForRespond` + preference |

---

## 5. API side-effect map (mobile)

| Mobile action | HTTP | Notes |
| --- | --- | --- |
| Bind | `POST /api/v1/enrollments/bind` | Per-enrollment or QR `authUrl` |
| Verify | `POST /api/v1/enrollments/verify` | Then optional signed `instance-info` |
| Claim pending | `POST /api/v1/auth-attempts/pending` | Pull; read-once claim |
| Approve | `POST /api/v1/auth-attempts/respond` `accepted=true` | Challenge when required |
| Deny / leave-as-deny | Same respond `accepted=false` | Post-#833 leave path |
| — | **Not called:** `…/auth-attempts/{id}/cancel` | Admin/Integration cancel |
| — | **Not called:** `POST /api/v1/admin/auth/passwordless-cancel` | Admin UI only (#833) |

---

## 6. Open questions (code vs intent — no code change in this docs task)

1. **No mobile TTL UI:** Server returns / stores `expiresAt`, but `PendingAttempt` omits it and the UI has no countdown. Should Detail/Pending show remaining time, or is “act or leave” enough?
2. **`createdAt` on `PendingAttempt`:** Set to local `checkedAt`, not server attempt `createdAt` (`claimPendingAttempt.ts`). Misleading if used for TTL math later.
3. **HTTP OK then local sig fail:** `serverDecisionSentRef` is set after respond HTTP success, before respond-result signature verification. Server may already be `ACCEPTED`/`REJECTED` while the user sees an error and stay on screen; leave will not re-deny. Is “trust HTTP, then verify for display” the intended fail-closed display rule?
4. **Leave while `Responding`:** `beforeRemove` allows navigation when `isRespondInFlight()` without waiting for completion. In-flight approve may still finish and call `goBack` again. Confirm this is acceptable.
5. **Leave after deny network failure:** User is allowed to leave; attempt may remain `READ` until TTL or Admin cancel — intentional fail-open for “don’t trap the user,” but waiters may still block until TTL.
6. **Empty/error leave:** Leaving without a loaded `attempt` does not deny. If claim succeeded server-side but client failed signature verification (`fail_closed` clears attempt), the server may still be `READ` until TTL — confirm whether fail-closed should still deny/cancel.
7. **Main vs #833 docs drift:** Until #833 merges, `MOBILE_FUNCTIONAL_FLOWS` / screen docs still describe leave → TTL. This FSM is authoritative for **post-#833**; update sibling docs when #833 lands.
8. **409 handling:** Mobile has no dedicated copy for Auth API state conflict vs generic request failure — sufficient for alpha?

---

## Related documents

| Doc | Role |
| --- | --- |
| [MOBILE_FUNCTIONAL_FLOWS.md](MOBILE_FUNCTIONAL_FLOWS.md) | End-to-end nominal/exception narration |
| [MOBILE_SCREENS_AND_WIREFLOWS.md](MOBILE_SCREENS_AND_WIREFLOWS.md) | Screen inventory and wireflows |
| [MOBILE_API_MAPPINGS.md](MOBILE_API_MAPPINGS.md) | Field-level Auth API mapping |
| [../../docs/ENDPOINT.md](../../docs/ENDPOINT.md) | Auth attempt TTL, cancel, wait |
| [../../docs/features/AUTH_SECURITY.md](../../docs/features/AUTH_SECURITY.md) | Pull model / read-once |
| [mobile-app-state-machine.html](mobile-app-state-machine.html) | Clickable visual complement |

## Provenance

- GitHub issue: [#834](https://github.com/mgagp/ezkey/issues/834)
- Post-#833 behavior from PR [#833](https://github.com/mgagp/ezkey/pull/833) head `fcc31fbf`
- Line citations for leave/respond guards refer to that branch’s `usePendingAuth.ts` /
  `PendingAuthScreen.tsx` and `AuthAttemptRespondService.java` / `AuthAttemptService.cancel`
