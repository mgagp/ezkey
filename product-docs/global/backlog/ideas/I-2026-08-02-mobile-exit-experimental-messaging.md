# Backlog Idea — `I-2026-08-02-mobile-exit-experimental-messaging` Mobile: exit experimental release messaging

## Metadata

- **ID:** `I-2026-08-02-mobile-exit-experimental-messaging`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-08-02`
- **Updated at:** `2026-08-02`
- **Last reviewed at:** `2026-08-02`
- **Progression markers:** `P3-distribution`
- **Component tags:** `mobile`, `docs`
- **Lane:** `D`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Retire the always-on "limited experimental release / invited audience / activation code" framing
currently shipped in every Ezkey Mobile build (debug and release alike), and replace it with
release-notes and onboarding copy appropriate for an official, generally-available Android app.

## Problem and value

- **Problem:** The Home banner, Release Notes screen, and Settings subtitle all present the app as
  a **limited experimental** product requiring an activation code from `info@ezkey.org`, regardless
  of build type. This framing is not gated by any feature flag or build flavor — it is the same
  copy in production release builds as in local development. It was accurate for the first invited
  Play release but does not match a decision to publish an official app line.
- **Expected value:** An honest, current release story that does not undersell a working MFA app to
  a general audience, without introducing new claims the product cannot yet back up (e.g. do not
  overclaim protocol maturity while retiring the "experimental" framing).

## Scope

- **In scope:**
  - Rewrite or remove the Home screen release banner
    ([`HomeScreen.tsx`](../../../../ezkey_mobile/app/screens/Home/HomeScreen.tsx)).
  - Rewrite the Release Notes screen
    ([`ReleaseNotesScreen.tsx`](../../../../ezkey_mobile/app/screens/ReleaseNotes/ReleaseNotesScreen.tsx))
    to describe the current release without the "limited audience" / activation-code narrative.
  - Decide the fate of the Coming Soon screen
    ([`ComingSoonScreen.tsx`](../../../../ezkey_mobile/app/screens/ComingSoon/ComingSoonScreen.tsx)):
    keep as a sober near-term roadmap (auth policy, local-auth, certificate pinning) or retire it if
    the product prefers not to publish forward-looking commitments in-app.
  - Update the Settings entry subtitle ("Current release and experimental information") to match the
    new framing
    ([`SettingsScreen.tsx`](../../../../ezkey_mobile/app/screens/Settings/SettingsScreen.tsx)).
  - Update EN/FR copy in
    [`resources.ts`](../../../../ezkey_mobile/app/i18n/resources.ts) and the tests that currently
    assert experimental strings
    (`ReleaseNotesScreen.test.tsx`, `SettingsScreen.test.tsx`).
  - Align the eventual Play listing "What's new" text with the new in-app story.
- **Out of scope:**
  - Play listing/compliance mechanics (privacy URL, Data Safety, versioning) — see
    [`I-2026-08-02-mobile-play-official-compliance-gate`](I-2026-08-02-mobile-play-official-compliance-gate.md).
  - Any change to F2a / debug-panel / flow-trace harness gating — that surface is already
    production-clean gated per
    [`MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`](../../../../ezkey_mobile/docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md)
    and is not part of this messaging change.
  - Any protocol, crypto, or backend change.

## Key assumptions

- The maintainer has decided (or is deciding, in parallel with this idea) to publish an official,
  non-experimental Android release rather than continue an invited-audience posture.
- Dropping the "experimental" framing does not require removing the Danger Zone or any other
  already-shipped functional surface; this idea is about narrative copy and navigation, not feature
  removal.

## Risks and exceptions

- If the Play compliance gate (`I-2026-08-02-mobile-play-official-compliance-gate`) is not yet
  closed, shipping this messaging change alone could imply a readiness the release does not yet
  have. Sequence release timing with that idea, even though the two can be implemented in parallel.
- Removing Coming Soon entirely would drop the only in-app place that currently sets user
  expectations about certificate pinning and local-auth; if removed, confirm the roadmap
  communication moves to `ezkey.org` instead of disappearing.

## Promotion notes

Ready for a `TB-*` once:

- the product decision on Coming Soon (keep as sober roadmap vs. retire) is made,
- replacement copy for Home banner and Release Notes is drafted in English (source language) with
  French follow-up,
- the relationship to the Play compliance gate idea's timeline is confirmed (can ship independently
  or should wait).

This is expected to be the **first executable `TB-*`** in the mobile official-release program: low
risk, no backend dependency, mostly copy/navigation/test changes.

## Links

- Vision:
  [`V-2026-08-02-mobile-official-play-release-posture`](../../vision/V-2026-08-02-mobile-official-play-release-posture.md)
- Sibling ideas in the same program:
  [`I-2026-08-02-mobile-play-official-compliance-gate`](I-2026-08-02-mobile-play-official-compliance-gate.md),
  [`I-2026-08-02-mobile-client-update-mechanism`](I-2026-08-02-mobile-client-update-mechanism.md),
  [`I-2026-08-02-mobile-installation-version-and-compat-discovery`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
- Origin of current messaging: `.github/prompts/archived/2026-05/plan-mobileReleaseMessaging.prompt.md`
- Mobile production-clean contract (unaffected by this idea):
  [`ezkey_mobile/docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`](../../../../ezkey_mobile/docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md)
