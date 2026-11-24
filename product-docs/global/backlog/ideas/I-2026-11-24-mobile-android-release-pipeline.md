# Backlog Idea — `I-2026-11-24-mobile-android-release-pipeline` Mobile: automated Android build and Play internal upload pipeline

## Metadata

- **ID:** `I-2026-11-24-mobile-android-release-pipeline`
- **Status:** `captured`
- **Priority:** `P2` (proposed)
- **Created at:** `2026-11-24`
- **Updated at:** `2026-11-24`
- **Last reviewed at:** `2026-11-24`
- **Progression markers:** `P3-distribution`
- **Component tags:** `mobile`, `ci`, `docs`, `security`
- **Lane:** `D`
- **Captured by:** Marc
- **GitHub issue:** none
- **Classification:** program (distribution), not hygiene

## Intent

Move the signed Android release from a hand-run sequence on Rootbeer to a GitHub Actions workflow:
a deliberate trigger runs the mobile tests, builds the signed AAB, runs the 16 KB gate and uploads
it to the Play **internal** track. Promotion beyond internal (closed, production) stays a human
action in Play Console. iOS is out of scope (separate catch-up, `I-2026-0027`).

## Problem and value

- **Problem:** Every Play upload depends on one workstation (Rootbeer holds the upload key and the
  service-account JSON) and on manual steps: read the highest `versionCode` in Play Console, bump
  `android/app/build.gradle`, run `bundle-release.sh`, then `play-upload-internal.sh`. Windows long
  paths force a short checkout path. Each manual step is a chance to upload the wrong commit or
  reuse a `versionCode`.
- **Expected value:** One trigger produces an upload tied to a known commit, built on a clean Linux
  runner (no long-path issue), with the same guards as today. Rootbeer becomes the fallback path,
  not the only one.

## What already exists (do not rebuild)

- Upload signing reads `EZKEY_UPLOAD_*` Gradle properties; the CI form
  (`ORG_GRADLE_PROJECT_EZKEY_UPLOAD_*`) is already documented in
  [`MOBILE_RELEASE_SIGNING.md`](../../../../ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md).
- [`bundle-release.sh`](../../../../ezkey_mobile/scripts/bundle-release.sh): JDK 17, release
  production-clean `.env` check, `bundleRelease`, 16 KB gate.
- [`play-upload-internal.sh`](../../../../ezkey_mobile/scripts/play-upload-internal.sh): Play
  Developer API, internal track only, draft by default, refuses a `versionCode` not strictly above
  every track, one edit at a time.
- Service account `ezkey-play-publisher`: testing tracks only, no production right
  ([`MOBILE_PLAY_PUBLISHING.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md)).
- CI already builds a release APK on `ubuntu-24.04` (`android-16kb-alignment` in
  [`ezkey-mobile-unit-tests.yml`](../../../../.github/workflows/ezkey-mobile-unit-tests.yml)), and
  that workflow is callable (`workflow_call`), so a release workflow can reuse the mobile test jobs.
- Prior trace: the mobile PRD planned "Fastlane (build automation) in later phase"
  ([`ezkey_mobile/PRD.md`](../../../../ezkey_mobile/PRD.md) § 9). This idea supersedes that line for
  Android (see Tooling).

## Direction

1. **Automate up to internal, keep promotion human.** Internal has no Play review, so the upload
   result is immediate. Closed and production reviews are asynchronous (hours to days); a pipeline
   can submit but cannot wait for them. Keep the service account without production rights:
   fail-closed — an unattended path to production would weaken a guarantee an MFA app must keep.
2. **Trigger:** `workflow_dispatch` on `main`, behind a protected GitHub **Environment** (for
   example `play-internal`) that requires Marc's approval before secrets are exposed. After a
   successful upload the workflow pushes a record tag. A tag-push trigger is an acceptable
   alternative; a named branch is not (it moves, a tag points at one commit).
3. **`versionCode` derived, not committed:** compute "highest on Play + 1" from the Play API at
   build time and pass it to Gradle; the record tag carries it. Removes the Console lookup and the
   bump commit.
4. **Secrets:** upload keystore (base64) and passwords as Environment secrets, never available to
   pull requests or forks. With Play App Signing, Google holds the app-signing key; the upload key
   can be reset from Play Console if it leaks, so storing it in CI is a bounded, recoverable risk.
   Later, replace the service-account JSON with **Workload Identity Federation** (GitHub OIDC → GCP,
   no long-lived key).

## Tooling

| Option | Decision |
|--------|----------|
| GitHub Actions + existing scripts | **Chosen.** No new stack element; guards already live in our script. Free on standard runners for a public repository; Play Developer API is free. |
| Fastlane (`supply`) | Not for Android alone (duplicates the Python uploader, adds Ruby). Re-evaluate with iOS, where `match` and TestFlight upload give it real value. |
| Gradle Play Publisher | No gain over the existing script; adds a plugin to the app build. |
| Third-party upload actions (`r0adkll/upload-google-play`…) | Rejected: third-party code with access to the upload secrets, for no gain. |
| Hosted mobile CI (Codemagic, Bitrise, Expo EAS) | Not needed; GitHub Actions already runs the mobile CI. |

## Phases

- **Phase 0 (next release):** keep the Rootbeer path; record frictions met.
- **Phase 1:** `mobile-android-release.yml` — dispatch + Environment approval → reused mobile test
  jobs → signed `bundleRelease` + 16 KB gate → `play-upload-internal.sh` as **draft** → AAB kept as
  workflow artifact. Human rolls out in Console. Rootbeer stays the break-glass path.
- **Phase 2:** `versionCode` from Play, record tag after success, "What's new" text read from a
  repo file, optional `--status completed` on internal.
- **Phase 3 (if needed):** Workload Identity Federation; build provenance attestation
  (`actions/attest-build-provenance`); optional internal → closed promotion workflow (requires
  widening the service account: explicit decision).
- **Not planned:** automatic production release; Fastlane for Android only; emulator E2E as a
  release gate (separate track, see `I-2026-0019`).

## Scope

- **In scope:** the release workflow, its Environment and secrets setup, `versionCode` derivation,
  doc updates in `MOBILE_PLAY_PUBLISHING.md` and `MOBILE_RELEASE_SIGNING.md`.
- **Out of scope:** iOS; Play listing and compliance work
  ([`I-2026-08-02-mobile-play-official-compliance-gate`](I-2026-08-02-mobile-play-official-compliance-gate.md));
  changes to `ci-gate` (the release workflow is separate and never runs on pull requests).

## Risks and open questions

- Moving the upload key off Rootbeer is Marc's call (copy into GitHub secrets vs. generate a new
  upload key and register it through Play Console).
- Play API allows one open edit per app: the workflow needs a concurrency group so two runs never
  overlap (lesson from 2026-10-19 in `MOBILE_PLAY_PUBLISHING.md`).
- Security review (Christophe) before Phase 1 merges: secret exposure paths, trigger permissions,
  log output of the upload step.

## Links

- Vision:
  [`V-2026-08-02-mobile-official-play-release-posture`](../../vision/V-2026-08-02-mobile-official-play-release-posture.md)
- Mobile release canon:
  [`MOBILE_PLAY_PUBLISHING.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md),
  [`MOBILE_RELEASE_SIGNING.md`](../../../../ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md),
  [`MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md)
- Related ideas:
  [`I-2026-08-02-mobile-play-official-compliance-gate`](I-2026-08-02-mobile-play-official-compliance-gate.md),
  [`I-2026-0027`](I-2026-0027-mobile-ios-implementation.md),
  [`I-2026-0019`](I-2026-0019-android-real-device-mobile-functional-tests.md)
