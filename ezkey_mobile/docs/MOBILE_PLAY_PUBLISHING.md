# Ezkey Mobile — Google Play publishing checklist

Short checklist for when you target a **production** Play listing. It is not a substitute for legal review.

Companion copy and form answers:

- Listing text, What's new, screenshot shot list: [`MOBILE_PLAY_STORE_LISTING.md`](MOBILE_PLAY_STORE_LISTING.md)
- Data Safety answers from current app behavior: [`MOBILE_PLAY_DATA_SAFETY.md`](MOBILE_PLAY_DATA_SAFETY.md)
- Signing: [`MOBILE_RELEASE_SIGNING.md`](MOBILE_RELEASE_SIGNING.md)
- Readiness snapshot: [`MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](MOBILE_PLAY_RELEASE_READINESS_AUDIT.md)

## Store listing

- [x] **App name** — launcher and listing default **Ezkey** (`app.json` `displayName`, `strings.xml` `app_name`). Optional search subtitle "Authenticator" may be used in the short description only.
- [ ] **Screenshots** and **feature graphic** for phone (and tablet if required). Shot list: [`MOBILE_PLAY_STORE_LISTING.md`](MOBILE_PLAY_STORE_LISTING.md).
- [x] **Privacy policy URL** — `https://ezkey.org/privacy.html` (French: `https://ezkey.org/fr/confidentialite.html`).
- [x] **Support contact** — `support@ezkey.org` (same as the privacy page).

## Privacy and data

- [x] **Data safety** answers drafted from real permissions and storage: [`MOBILE_PLAY_DATA_SAFETY.md`](MOBILE_PLAY_DATA_SAFETY.md). Paste into Play Console at submit time.
- [x] No analytics / advertising SDKs in the current dependency set; **no surprise** telemetry.

## Technical

- [x] **Release signing** path exists (upload key outside the repo). Repeat for the **final GA AAB** before submit.
- [x] **Version policy** documented below. Workspace candidate: `versionName` `1.0.0` (`package.json`), `versionCode` **2**.
- [x] **Target API level** — `targetSdkVersion` / `compileSdkVersion` **36**. `minSdkVersion` **31** (Android 12+).
- [x] **Open source notices**: run `yarn license:app-data` after dependency changes; commit `app/data/thirdPartyLicenses.json` with the candidate. Regenerated 2026-08-14.

## Optional polish

- [x] **Monochrome adaptive icon** (Android 13+) — see `mipmap-anydpi-v26/ic_launcher*.xml`.
- [ ] **Back gesture** and predictive back behavior on supported devices (smoke on device at GA).

## Version bump recipe

`package.json` `"version"` is the human-readable **versionName** (Gradle reads it; Settings/About show `APP_VERSION` from the same file).

Android **`versionCode`** is a separate integer in [`android/app/build.gradle`](../android/app/build.gradle). Play requires it to be **strictly greater** than every previously uploaded AAB for `org.ezkey.mobile`, in every track.

Before each Play upload:

1. Open Play Console → the Ezkey app → **App bundle explorer**. Note the highest published `versionCode`.
2. If the workspace `versionCode` is **not** greater than that number, bump it in `android/app/build.gradle`.
3. Bump `ezkey_mobile/package.json` `"version"` when the user-visible release string should change (for example `1.0.0` → `1.0.1`).
4. If dependencies changed, run `yarn license:app-data` and commit the JSON snapshot.
5. Build the signed AAB (`./scripts/build-install-release-clean.sh` or `yarn android:bundle:release` with JDK 17).

**This workspace (2026-08-14):** `versionName` `1.0.0`, `versionCode` `2`. That assumes the experimental listing (or the proven local signed install) used `versionCode` `1`. If Play Console already shows a higher code, use `last + 1` instead of `2`. Do not reuse a `versionCode`.

## Client updates after the first official listing

Release builds include a **fail-open** Google Play **flexible** in-app update check (`EzkeyPlayUpdateModule`). It does nothing on debug, sideload, or Play errors. The prompt can only appear after a **later** Play upload is available. This is not a hard minimum-version gate and does not use Auth API `instance-info`.

## Notes

- This app follows a **pull-based** model for pending authentication; do not imply background tracking in the listing unless you add it and disclose it.
- Listing copy must say **Android 12+**. Do not claim older OS support.
