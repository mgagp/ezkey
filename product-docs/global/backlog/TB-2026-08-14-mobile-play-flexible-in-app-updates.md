# Tracer Bullet Brief — `TB-2026-08-14-mobile-play-flexible-in-app-updates` Soft Play in-app updates

## Metadata

- **ID:** `TB-2026-08-14-mobile-play-flexible-in-app-updates`
- **Status:** `under-review`
- **Related idea:** `I-2026-08-02-mobile-client-update-mechanism`
- **GitHub issue:** none
- **GitHub branch:** none
- **GitHub PR:** none
- **Created at:** `2026-08-14`
- **Updated at:** `2026-08-14`
- **Captured by:** Marc

## Objective

Ship a fail-open, Play-anchored **flexible** (soft) in-app update prompt in the first official
Android binary so a later Play upload can notify installed users. No hard minimum-version gate.
No self-hosted `instance-info` version channel.

## Boundaries in scope

- Thin native Android module wrapping Play Core `AppUpdateManager` (flexible only)
- JS check + dismissible Alert on Home; dismiss remembered per Play `availableVersionCode`
- Debug / sideload / missing Play: treat as "no update" (fail-open)
- Docs: composition with `I-2026-0025` (protocol mismatch vs. "a newer app exists")

## Out of scope

- Immediate / hard update (`AppUpdateType.IMMEDIATE`)
- Custom `ezkey.org` version endpoint
- Any gate keyed on Auth API `instance-info`
- iOS App Store updates
- Demonstrating the prompt on the first listing (requires a second Play upload)

## First executable slice

Native `checkFlexibleUpdate` / `startFlexibleUpdate`; Home Alert; unit tests with the native module
mocked; Play Core dependency only (no third-party RN wrapper).

## Rollback or fallback posture

Remove the Home check and native package. Users still update through the Play Store listing. Failure
of the check never blocks enrollment or authentication.

## Critical flows

- **Nominal (Play install, newer AAB published):** Home shows Update / Later; Update starts flexible
  Play flow.
- **Later:** persist dismissed `availableVersionCode`; do not re-prompt until Play offers a newer
  code.
- **Exception — no Play / debug / API error:** silent no-op; app continues.

## Evidence plan

- JS unit tests for fail-open when the native module is missing or throws
- Docs in `MOBILE_PLAY_PUBLISHING.md` and the parent idea
- Full UI proof waits for a second Play upload after the official listing

## Quality gates

- Fail-open at the Play-availability boundary
- No new network dependency on the self-hosted backend
- Stay on the existing RN stack (Play Core Maven artifact only)

## Exit criteria

Release builds include the flexible-update check; debug and sideload stay quiet; no hard block.
