# Backlog Idea — `I-2026-08-02-mobile-play-official-compliance-gate` Mobile: Play official release compliance gate

## Metadata

- **ID:** `I-2026-08-02-mobile-play-official-compliance-gate`
- **Status:** `active`
- **Priority:** `P2`
- **Created at:** `2026-08-02`
- **Updated at:** `2026-08-14`
- **Last reviewed at:** `2026-08-14`
- **Progression markers:** `P3-distribution`
- **Component tags:** `mobile`, `docs`
- **Lane:** `D`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Close the concrete, non-messaging gaps already identified in
[`MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md)
so the team can call the Play release operationally complete: version alignment, a validated GA
publication-candidate artifact, listing/compliance inputs, product naming, and the React Native
stack-baseline decision.

## Problem and value

- **Problem:** The audit previously stated the project was "partially ready, not yet release-complete."
  Remaining work is operational: a validated GA publication-candidate AAB, Console paste of Data
  Safety / listing copy, and screenshots. Version name is aligned (`package.json` `1.0.0`);
  `versionCode` policy is documented (workspace candidate **2** pending Console confirmation).
  Privacy URL and support contact exist on ezkey.org. The React Native **0.86.2** baseline decision
  is **release now**; 0.87 is post-listing debt
  ([`MOBILE_RELEASE_DECISION_MEMO.md`](../../../../ezkey_mobile/docs/MOBILE_RELEASE_DECISION_MEMO.md)).
- **Expected value:** A publishable, compliant official Play listing with a validated release
  candidate — the last mile between "technically working app" and "app the team can submit for
  official review."

## Scope

- **In scope:**
  - Align `package.json` version and Android `versionName`/`versionCode`; define the `versionCode`
    increment policy for future releases.
  - Produce a signed GA publication-candidate AAB using the already-validated local upload-signing
    path (see Key assumptions), and validate it on a device before submission.
  - Define the public privacy-policy URL, support contact, and complete Play Data Safety answers
    from actual app behavior (camera, network, local storage).
  - Finalize the Play listing / launcher product name (`app.json` currently uses `"Ezkey"`; the
    publishing checklist example name `"Ezkey Authenticator"` is illustrative only).
  - Resolve the RN stack-baseline decision from
    [`MOBILE_RELEASE_DECISION_MEMO.md`](../../../../ezkey_mobile/docs/MOBILE_RELEASE_DECISION_MEMO.md)
    (release now with upgrade scheduled as follow-up debt, vs. upgrade before first official
    listing).
  - Complete the remaining unchecked items in
    [`MOBILE_PLAY_PUBLISHING.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md)
    (screenshots, feature graphic, license report via `yarn license:app-data`, etc.).
- **Out of scope:**
  - Re-establishing or rotating the upload signing key/keystore — that mechanism already exists and
    is operationally validated on the maintainer workstation (see Key assumptions). This idea
    consumes that mechanism; it does not rediscover it.
  - Experimental-messaging copy changes — see
    [`I-2026-08-02-mobile-exit-experimental-messaging`](I-2026-08-02-mobile-exit-experimental-messaging.md).
  - Any backend/protocol change.

## Key assumptions

- **Upload signing path is already operational, outside the repository.** Verified 2026-07-29
  (presence-only check, no secret values read or recorded): on the maintainer workstation,
  `~/.gradle/gradle.properties` contains all four `EZKEY_UPLOAD_STORE_FILE`,
  `EZKEY_UPLOAD_STORE_PASSWORD`, `EZKEY_UPLOAD_KEY_ALIAS`, `EZKEY_UPLOAD_KEY_PASSWORD` properties
  set to non-empty values, and the keystore file the store-file property points to exists (observed
  basename `ezkey-upload.jks`; canon example in
  [`MOBILE_RELEASE_SIGNING.md`](../../../../ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md) uses the
  illustrative extension `.keystore` — either extension is fine as long as `STORE_FILE` resolves to
  the real file). No non-debug keystore exists inside either git clone (`C:\GitHub\ezkey` or the
  worktree) — this is correct; secrets belong in `~/.gradle`, never in the repository. This idea's
  remaining work is therefore producing and validating a **final GA candidate artifact**, not
  recovering or reconfiguring signing credentials.
- The Play Console account and its association with the existing upload key are already in place
  from the prior experimental release.

## Risks and exceptions

- If the RN stack-baseline decision favors "upgrade first," this idea's timeline depends on that
  upgrade completing — track as a sequencing risk, not scope creep into this idea.
- Data Safety answers must reflect actual data handling (camera permission, local Keystore-backed
  crypto, no analytics/telemetry observed) — do not copy a generic template without verification
  against the current permission surface
  (`ezkey_mobile/android/app/src/main/AndroidManifest.xml`).

## Promotion notes

Promotion notes updated 2026-08-14: RN stack-baseline is **release now on 0.86.2**; privacy URL
`https://ezkey.org/privacy.html`; support `support@ezkey.org`; listing/launcher name **Ezkey**.
Remaining Console/device work: screenshots, feature graphic, GA AAB evidence, paste Data Safety.

See [`MOBILE_PLAY_PUBLISHING.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md),
[`MOBILE_PLAY_DATA_SAFETY.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_DATA_SAFETY.md),
[`MOBILE_PLAY_STORE_LISTING.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_STORE_LISTING.md).

## Links

- Vision:
  [`V-2026-08-02-mobile-official-play-release-posture`](../../vision/V-2026-08-02-mobile-official-play-release-posture.md)
- Sibling ideas in the same program:
  [`I-2026-08-02-mobile-exit-experimental-messaging`](I-2026-08-02-mobile-exit-experimental-messaging.md),
  [`I-2026-08-02-mobile-client-update-mechanism`](I-2026-08-02-mobile-client-update-mechanism.md),
  [`I-2026-08-02-mobile-installation-version-and-compat-discovery`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
- Mobile release canon:
  [`ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md),
  [`ezkey_mobile/docs/MOBILE_RELEASE_DECISION_MEMO.md`](../../../../ezkey_mobile/docs/MOBILE_RELEASE_DECISION_MEMO.md),
  [`ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md),
  [`ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md`](../../../../ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md)
