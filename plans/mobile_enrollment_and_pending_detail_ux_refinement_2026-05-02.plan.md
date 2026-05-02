# Plan: Mobile Enrollment And Pending-Check Detail UX Refinement

## Status

- Enrollment screen refinement: implemented and deployed on device.
- Enrollment detail / pending-check entry screen: implemented and deployed on device.

## Scope

This plan captures two tightly related mobile UX refinements in `ezkey_mobile`:

1. reduce obsolete or redundant content in the enrollment completion flow,
2. review the enrollment detail screen that precedes `Check pending` so it shows the right information in the right order with lower cognitive load.

The intent is pragmatic and product-led: this app is an authenticator, not an admin console. The user should understand what matters now, with as little friction and ambiguity as possible.

## Discussion Summary And Historical Record

The recent discussion converged on a deliberately simple and pragmatic UX direction for the mobile authenticator.

### Enrollment screen conclusion

- After a successful QR scan and trusted bind result, scan instructions are obsolete and should disappear.
- The screen should focus immediately on the next required action: verification with the six-digit code.
- This was implemented and deployed in debug for device testing.

### Enrollment detail / pending-check screen conclusion

- The screen should optimize for a recurring daily task: quickly recognizing the enrollment and deciding whether to check pending requests.
- The small host hint shown near the top of the identity card is redundant with the lower `Server` box.
- The team converged on removing the top host hint and keeping the lower `Server` box as the single technical server cue.
- A collapsible disclosure pattern was considered for server details, but rejected for now because:
  - the screen is not overloaded,
  - the visible server box is cognitively tolerable in its current lower position,
  - adding a disclosure interaction would increase UI complexity without enough benefit.

### Timestamp semantics conclusion

- `createdAt` keeps its clear meaning: local enrollment creation timestamp.
- `lastActivityAt` is repurposed to mean the timestamp of the last user-initiated pending check.
- The visible label becomes `Last verification` in English and `Dernière vérification` in French.
- The timestamp is updated immediately when a pending-check operation starts.
- The timestamp updates regardless of whether a pending request exists or whether the backend returns an error.
- This was chosen because it matches the user’s mental model: the relevant moment is when they explicitly initiated a verification step from the app.

### Product rationale

- Ezkey Mobile is a necessary daily tool, not a destination product users open for its own sake.
- The UX should therefore prefer directness, low ambiguity, and low cognitive load over extra explanatory chrome or technical duplication.
- The accepted solution is intentionally modest: reduce redundant information, keep the useful technical detail in one stable place, and align timestamps with a meaning users can understand immediately.

### Implementation record

- The detail screen implementation now removes the top host hint.
- The lower `Server` box remains the only visible server-location cue.
- Timestamp rendering now uses two lines instead of a single wrapped sentence.
- The visible labels are now `Created` / `Last verification` in English and `Créé` / `Dernière vérification` in French.
- `lastActivityAt` is now updated when the mobile app initiates a pending-check operation.
- The updated debug build was installed successfully on the connected Pixel 7 Pro on 2026-05-02 for manual functional testing.

## Part 1 - Enrollment Screen After QR Scan

### Decision

Once a QR scan has succeeded and the bind response has been validated into a trusted draft, scan guidance is no longer relevant and must disappear.

### Implementation

- Applied in `ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx`.
- The `Scan` block is now rendered only when no trusted draft exists yet.
- After successful bind, the screen pivots directly to:
  - `Vérification`
  - six-digit challenge input
  - enrollment details card
- The obsolete divider between the hidden scan block and verify block was removed.

### Documentation Alignment

- Updated `ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md`.
- Updated `ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md`.
- Updated `ezkey_mobile/docs/MOBILE_ARCHITECTURE.md`.

### Validation

- No editor errors on touched files.
- Android debug build reinstalled successfully on the connected Pixel 7 Pro.

## Part 2 - Enrollment Detail Screen Before Pending Check

### Screen Under Review

- `ezkey_mobile/app/screens/EnrollmentDetail/EnrollmentDetailScreen.tsx`

This is the screen that shows the enrollment identity and the primary action `Check pending`. In real usage, the user may remain on this screen and return to it frequently. That makes information hierarchy especially important.

### Current Content Order

The current screen renders content in this order:

1. installation name
2. installation host hint when available
3. tenant name
4. integration name
5. enrollment/device name
6. installation description
7. one-line metadata string combining `createdAt` and `lastActivityAt`
8. server box with full `authUrl` when available
9. primary CTA `Check pending`

### Confirmed Data Meaning

#### `createdAt`

- This is the local timestamp written when the enrollment is successfully persisted on device.
- It is initialized in `EnrollmentWizardScreen` at the moment the stored enrollment record is created.
- It is used consistently elsewhere as an enrollment creation timestamp.

#### `lastActivityAt`

- In the current implementation, this field is also initialized to the same timestamp as `createdAt` during enrollment persistence.
- No meaningful update path was confirmed in the current mobile flow for this field after enrollment creation.
- That means the current label `Last activity` overstates the semantic certainty of the value on this screen.

#### Decision recorded on 2026-05-02

- `lastActivityAt` is repurposed product-wise as the timestamp of the last user-initiated pending check.
- The user-facing label should become `Last verification` in English and `Dernière vérification` in French.
- The timestamp must be updated as soon as a pending-check operation is initiated by the user.
- This update happens regardless of outcome:
  - pending request found,
  - no pending request,
  - backend error or transport failure.
- Product rationale: this timestamp represents the last moment the user explicitly initiated an authentication-related check from the app, not the last successful auth result.

### Analysis: Redundancy And Information Hierarchy

#### Confirmed redundancy

There are currently two server-location signals on the same screen:

- the installation host hint near the top of the identity card,
- the dedicated `Server` box lower on the screen with the full HTTPS URL.

These two elements are related but do not carry the same weight:

- the host hint is compact and contextual,
- the server box is explicit and technically precise.

Showing both increases repetition without clearly improving trust for a typical end user.

#### Recommended direction

For this screen, the most coherent direction is:

- keep the installation name in the top card,
- keep the dedicated `Server` box with the full HTTPS URL,
- remove the top host hint from the identity card in a future implementation pass.

Why this is the strongest current option:

- it preserves the human-readable instance identity at the top,
- it keeps the precise technical endpoint available lower on the screen,
- it removes the repeated “same idea twice” problem,
- it matches the user’s real task: identify the enrollment quickly, then decide whether to check pending requests.

#### Information order assessment

The high-level order is close to correct already:

- instance / installation identity first,
- business context next (tenant, integration, device),
- technical detail after that (server URL),
- action last and visually primary.

The main problems are not the overall blocks but:

- the duplicate server cues,
- the ambiguous `lastActivityAt` wording,
- the current one-line rendering of the two timestamps.

### Analysis: Created / Last Activity Meta Block

The current meta line combines both values into one sentence:

- EN: `Created {{created}} · Last {{last}}`
- FR: `Créé {{created}} · Dernière activité {{last}}`

This is visually fragile on a narrow mobile screen because line wrapping can split the sentence in awkward places.

More importantly, the current rendering visually suggests that both values are equally stable and equally meaningful, which is not true right now.

#### Recommended interpretation

- `createdAt` remains the local enrollment creation timestamp.
- `lastActivityAt` now has a concrete intended meaning: last pending-check initiation by the user.
- The remaining work is implementation alignment, not product semantics discovery.

#### Options for the next decision

Option A - conservative and honest:

- show `Created` on its own line,
- temporarily remove `Last activity` from this screen until its semantics are implemented for real.

Pros:

- lowest ambiguity,
- lowest visual noise,
- most honest to the actual data semantics.

Cons:

- the screen loses one piece of temporal context.

Option B - implemented product direction:

- show `Created` on one line,
- show `Last verification` on a second line,
- update the underlying timestamp on every user-initiated pending check.

Pros:

- preserves both timestamps,
- immediately fixes the line-wrap issue,
- matches user intuition and real daily use,
- minimal visual redesign.

Cons:

- requires implementation updates so the stored metadata matches the new label.

Option C - semantic hardening first, then keep both:

- define exactly what updates `lastActivityAt`,
- implement that behavior consistently across the relevant local flows,
- then keep both timestamps on separate lines.

Pros:

- strongest long-term integrity.

Cons:

- larger scope than the current UX-only pass.

### Working Recommendation Before Next Implementation Pass

The strongest current recommendation is:

1. keep the top identity card,
2. keep the lower `Server` box with full HTTPS URL,
3. remove the top host hint later to eliminate redundancy,
4. split the timestamp area into separate lines,
5. rename `Last activity` to `Last verification` / `Dernière vérification`,
6. update the timestamp whenever the user initiates a pending check.

## Decision Points For Next Discussion

No open decision remains for this refinement slice. The next step is manual functional validation on device.

## Relevant Files

- `ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx`
- `ezkey_mobile/app/screens/EnrollmentDetail/EnrollmentDetailScreen.tsx`
- `ezkey_mobile/app/screens/PendingAuth/PendingAuthScreen.tsx`
- `ezkey_mobile/app/i18n/resources.ts`
- `ezkey_mobile/app/services/api/types.ts`
- `ezkey_mobile/app/services/storage/enrollmentStorage.ts`
- `ezkey_mobile/app/hooks/useEnrollments.ts`
- `ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md`
- `ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md`
- `ezkey_mobile/docs/MOBILE_ARCHITECTURE.md`
