# Tracer Bullet Brief — `TB-2026-08-14-mobile-exit-experimental-messaging` Exit experimental in-app messaging

## Metadata

- **ID:** `TB-2026-08-14-mobile-exit-experimental-messaging`
- **Status:** `under-review`
- **Related idea:** `I-2026-08-02-mobile-exit-experimental-messaging`
- **GitHub issue:** none
- **GitHub branch:** none
- **GitHub PR:** none
- **Created at:** `2026-08-14`
- **Updated at:** `2026-08-14`
- **Captured by:** Marc

## Objective

Replace the always-on "limited experimental / invited audience / activation code" in-app narrative
with copy appropriate for an official Android app, without changing protocol, crypto, or Play
Console paperwork.

## Boundaries in scope

- Home release banner, Release Notes screen, Settings "What's new" subtitle
- EN/FR strings in `ezkey_mobile/app/i18n/resources.ts`
- Unit tests that asserted experimental strings
- Coming Soon remains as a sober near-term roadmap (auth policy / local-auth + certificate pinning)

## Out of scope

- Play listing/compliance mechanics (`I-2026-08-02-mobile-play-official-compliance-gate`)
- F2a / debug-panel / flow-trace harness gating
- Protocol, crypto, or backend changes
- Retiring Coming Soon

## First executable slice

Rewrite EN (source) then FR copy; keep Coming Soon; update tests; `yarn validate:ci` on the touched
surface.

## Rollback or fallback posture

Revert the copy and tests. No data migration and no native contract.

## Critical flows

- **Nominal:** Home banner → Release Notes describes capabilities, Android 12+, no activation code.
- **Settings:** "What's new" subtitle no longer says "experimental."
- **Coming Soon:** still reachable; still sets expectations for pinning and local-auth.

## Evidence plan

- `ReleaseNotesScreen.test.tsx` and `SettingsScreen.test.tsx` assert the new official copy
- Manual device smoke (EN/FR): Home, Release Notes, Settings, Coming Soon

## Quality gates

- Copy does not overclaim protocol maturity or imply analytics/background tracking
- Coming Soon decision (keep) is recorded on the parent idea

## Exit criteria

No remaining user-visible "experimental / invited audience / activation code" strings on Home,
Release Notes, or Settings. Coming Soon still present.
