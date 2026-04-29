## Plan: Mobile Local Activity UX

Replace the misleading current-status treatment in the mobile app with a calmer and more truthful
local-activity design. The recommended direction is to stop implying live remote availability,
keep the protocol and APIs unchanged, persist only a very small set of local historical facts, and
redesign the Home enrollment list plus Enrollment Detail around two categories: stable local facts
and recent observed activity.

**Design intent**
1. Show what the device truly knows, not what it guesses about the server.
2. Keep the main list scannable in 2 seconds.
3. Favor a neutral, work-oriented UX for ordinary users over protocol-oriented status language.
4. Include a discreet but explicit visual indication when authentication challenge is required.
5. Add no new API and no new protocol surface for the 1.0 direction.

**Steps**
1. Confirm the implementation anchor and data boundaries. The relevant app is the React Native
	module at c:\github\ezkey-worktree3\ezkey_mobile. The concrete anchors are the local storage
	model in StoredEnrollment, the list card in HomeScreen, and the detail summary in
	EnrollmentDetailScreen.
2. Reframe the design around two information families only.
	Stable local facts: configured on this device, verified in the past, and challenge required.
	Recent observed activity: last pending check plus its result, and last approval.
	Exclude last denial from the primary design to avoid noise.
3. Extend the local storage shape minimally so the UI can speak truthfully without inventing a new
	status model. Add local-only fields to StoredEnrollment for:
	lastPendingCheckedAt,
	lastPendingResult,
	lastApprovedAt,
	authChallengeRequired.
	Do not add READY, UNAVAILABLE, REVOKED, or any current-availability field.
4. Redesign the Home enrollment card around one neutral badge, one small secondary indicator for
	challenge requirement, and two short informational lines max.
5. Redesign Enrollment Detail as an activity-oriented summary instead of a pseudo-status screen,
	with the same neutral configured badge and an explicit challenge-required hint in the identity
	area.
6. Make the copy intentionally modest and historical.
	Prefer Configured, Challenge required, Last approval, Last check, No recent approval, No
	request found on last check, and No recent activity.
	Avoid Active, Available, Revoked, Unavailable, or any wording that implies current remote truth.
7. Validate the design against ordinary-user value, not protocol cleverness.
	Each displayed field should answer one practical question for a busy user:
	Has this device already worked?
	What happened recently?
	Will this enrollment ask me for an authentication challenge when I use it?
	Do I need to know anything before tapping Check pending?

**Proposed Home Screen Design**
1. Keep the card title as the integration name and keep enrollmentName as the subtitle when
	present.
2. Replace the current green ACTIVE or PENDING badge in the card header with a neutral badge
	labeled Configured.
3. Add a secondary visual indicator for challenge requirement directly on the card. This should be
	visible without opening details, but remain quieter than the main badge.
	Recommended presentation:
	a small outlined pill labeled Challenge required
	or a compact shield or keypad-style icon plus text Challenge
	placed near the Configured badge or directly below the title row.
4. Add one compact metadata line for approval activity:
	Last approval today at 09:12
	or No recent approval.
5. Add one compact metadata line for the last check summary:
	Last check: no request found 18 min ago
	Last check: request found 4 min ago
	Last check: check failed 1 h ago
	or No recent activity if there has never been an approval or a recorded check.
6. Keep the card lightweight. Do not exceed the current density by more than two short lines plus
	the challenge hint.
7. Keep the visual hierarchy clear:
	integration name first,
	neutral configured state second,
	challenge requirement third,
	recent activity after that.

**Home Screen Display Rules**
1. Always show Configured when the enrollment exists locally and is usable on this device.
2. Always show the challenge-required indicator when authChallengeRequired is true.
3. If lastApprovedAt exists, show it on the first activity line.
4. If lastApprovedAt does not exist, show No recent approval on the first activity line.
5. If lastPendingCheckedAt and lastPendingResult exist, show the humanized last-check summary on
	the second line.
6. If there has never been a check and never been an approval, replace the second line with No
	recent activity.
7. Do not show raw technical result values or protocol terms like pending, 204, approved response,
	or failed signature.

**Proposed Enrollment Detail Design**
1. Keep the identity zone and preserve installation, host hint, tenant, integration, device name,
	and description because that structure is already clear.
2. Replace the current ACTIVE or PENDING all-caps badge with the same neutral Configured badge
	used on Home.
3. Show Challenge required as an explicit secondary fact in the identity zone whenever
	authChallengeRequired is true.
	Recommended presentation:
	a subtle row directly below the badge line,
	or a small label beside the badge if spacing allows.
4. Replace the current Created {date} · Last {date} meta line with separate factual rows:
	Created on Apr 12, 2026 at 09:41
	Last approval today at 09:12
	Last check 18 min ago
	Last check result: no request found
5. If no approval has ever been recorded, show No recent approval instead of a blank date.
6. If no check has ever been recorded, show Last check: none yet.
7. Keep the Check pending button as the primary action. The design should clarify recent history,
	not compete with the main action.

**Enrollment Detail Display Rules**
1. Configured remains the primary neutral badge.
2. Challenge required is always visible when true because it is a meaningful day-to-day usage fact.
3. Created on remains visible as archival context, but it should be visually subordinate to Last
	approval and Last check.
4. Last approval is the most important activity field.
5. Last check and Last check result are second-order support fields.
6. No denial history is shown in the primary detail design.

**Data To Persist Locally**
1. lastPendingCheckedAt — timestamp of the most recent explicit pending check attempt.
2. lastPendingResult — minimal local summary such as no_request_found, request_found, or
	check_failed.
3. lastApprovedAt — timestamp of the most recent successful approval observed on this device.
4. authChallengeRequired — local fact used to show the challenge-required indicator on Home and
	Detail.

**Data Deliberately Excluded**
1. lastDeniedAt from the primary UI.
2. Any server-availability or honesty-style status.
3. Any detailed operator reason such as tenant disabled or integration suspended.
4. Any background polling or status-refresh mechanism.

**Relevant files**
- c:\github\ezkey-worktree3\docs\MOBILE_DEVELOPER_GUIDE.md — confirms that pending is
  user-initiated and that 204 means no pending attempt, not a general status refresh.
- c:\github\ezkey-worktree3\ezkey_mobile\app\services\storage\enrollmentStorage.ts — exact
  local persistence shape via StoredEnrollment.
- c:\github\ezkey-worktree3\ezkey_mobile\app\services\api\types.ts — exact EnrollmentSummary
  and EnrollmentStatus fields currently exposed to the app.
- c:\github\ezkey-worktree3\ezkey_mobile\app\screens\Home\HomeScreen.tsx — current list card
  structure and status badge location.
- c:\github\ezkey-worktree3\ezkey_mobile\app\screens\EnrollmentDetail\EnrollmentDetailScreen.tsx
  — current detail identity zone, meta line, and primary action.

**Verification**
1. Check that every proposed displayed value can be captured from existing flows without a new
	endpoint.
2. Check that no label implies current remote availability.
3. Check that the Home card remains scannable in 2 seconds.
4. Check that the challenge-required indicator is visible but not noisy on Home.
5. Check that the Detail screen adds clarity rather than protocol jargon.
6. If implementation happens later, validate one happy-path case and one stale-data case to ensure
	the UI still feels truthful.

**Decisions**
- Included scope: redesign of the main enrollment list and corresponding detail screen.
- Included scope: minimal local storage additions needed to support the redesign.
- Included scope: explicit prioritization of challenge required, last approval, and last check.
- Excluded scope: new Auth API endpoint, out-of-band availability API, protocol changes, and
  implementation.
- Key finding: the current app does not persist last pending check, last approval, or
  challenge-required state in StoredEnrollment today, so these must be added explicitly if the UI
  is to use them.
- Key finding: challenge required is important enough to show directly on Home and Detail as a
  local fact, not hide as a secondary implementation note.
- Key finding: the current Home and Detail screens are simple enough that this redesign can remain
  lightweight if the event set stays very small.

**Further Considerations**
1. The challenge-required hint should be factual and discrete, not alarming.
2. Home fallback copy should prefer No recent activity over empty or ambiguous timestamps.
3. Created on should remain visible in Detail as archival context, but primary emphasis should
	shift to Last approval and Last check because those are more useful to ordinary users.
4. If visual density becomes a concern on Home, shorten the challenge indicator label before
	removing the signal entirely.
