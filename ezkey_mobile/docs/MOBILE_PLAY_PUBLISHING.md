# Ezkey Mobile — Google Play publishing checklist

Short checklist for when you target a **production** Play listing. It is not a substitute for legal review.

Companion copy and form answers:

- Listing text, What's new, screenshot shot list: [`MOBILE_PLAY_STORE_LISTING.md`](MOBILE_PLAY_STORE_LISTING.md)
- Data Safety answers from current app behavior: [`MOBILE_PLAY_DATA_SAFETY.md`](MOBILE_PLAY_DATA_SAFETY.md)
- Signing: [`MOBILE_RELEASE_SIGNING.md`](MOBILE_RELEASE_SIGNING.md)
- Readiness snapshot: [`MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](MOBILE_PLAY_RELEASE_READINESS_AUDIT.md)

## Developer account

Prefer a **Google Play organization** account (DUNS, verified address, public organization name) when a legal entity exists or can be created quickly. A personal account is cheaper up front ($25 one-time) but migrating to an organization later is not trivial on Google's side. Organization accounts also match Ezkey's self-hosted / enterprise-facing posture.

## Release tracks

Use the same app, stacked tracks:

1. **Internal testing** — up to 100 email testers, minutes to propagate, no Play review. Validate the signed AAB and critical flows first.
2. **Closed testing** — email lists or Google Groups; light Play review. Personal accounts created after 2023-11-13: ≥12 testers opted in continuously for 14 days; organization accounts exempt.
3. **Production** — public listing.

Recommended path: **Internal → Closed → Production**. Open testing (public early access) is optional.

Delivery is **AAB** (not APK). Play App Signing holds the app-signing key; you keep the **upload key** — see [`MOBILE_RELEASE_SIGNING.md`](MOBILE_RELEASE_SIGNING.md).

## Store listing

- [x] **App name** — launcher and listing default **Ezkey** (`app.json` `displayName`, `strings.xml` `app_name`). Optional search subtitle "Authenticator" may be used in the short description only.
- [ ] **Screenshots** and **feature graphic** for phone (and tablet if required). Shot list: [`MOBILE_PLAY_STORE_LISTING.md`](MOBILE_PLAY_STORE_LISTING.md).
- [x] **Privacy policy URL** — `https://ezkey.org/privacy.html` (French: `https://ezkey.org/fr/confidentialite.html`).
- [x] **Support contact** — `support@ezkey.org` (same as the privacy page).

## Privacy and data

- [x] **Data safety** answers drafted from real permissions and storage: [`MOBILE_PLAY_DATA_SAFETY.md`](MOBILE_PLAY_DATA_SAFETY.md). Paste into Play Console at submit time.
- [x] No analytics / advertising SDKs in the current dependency set; **no surprise** telemetry.
  The one SDK that would phone home (ML Kit usage metrics, via the QR scanner) has its upload
  backend removed in the app manifest and guarded in CI; see
  [`MOBILE_PLAY_DATA_SAFETY.md`](MOBILE_PLAY_DATA_SAFETY.md) § Third-party SDK telemetry (#1147).

## Technical

- [x] **Release signing** path exists (upload key outside the repo). Repeat for the **final GA AAB** before submit.
- [x] **Version policy** documented below (`versionCode` bumps every Play upload; `versionName` stays on the product cut until that SemVer moves; release name `'<code> (<name>)'`). In-app chrome says Public alpha (plus git short SHA on About), not `v1.0.0`.
- [x] **Target API level** — `targetSdkVersion` / `compileSdkVersion` **36**. `minSdkVersion` **31** (Android 12+).
- [x] **16 KB page-size** — NDK r28+, datastore 1.2.1 force, `yarn android:bundle:release` runs
  `scripts/check-16kb-alignment.sh` (see [`MOBILE_ANDROID_PLATFORM_SUPPORT.md`](MOBILE_ANDROID_PLATFORM_SUPPORT.md)).
  Do not use `useLegacyPackaging`. RN 0.87.1 prebuilt RELRO residuals are an explicit Decision A
  allowlist (#659) until an RN bump.
- [x] **Open source notices**: run `yarn license:app-data` after dependency changes; commit
  `app/data/thirdPartyLicenses.json` with the candidate. See *In-app third-party licenses
  snapshot* below.

## Optional polish

- [x] **Monochrome adaptive icon** (Android 13+) — see `mipmap-anydpi-v26/ic_launcher*.xml`.
- [ ] **Back gesture** and predictive back behavior on supported devices (smoke on device at GA).

## Versioning policy

Product SemVer is **one version for the whole monorepo**
([ADR-0017](../../product-docs/global/architecture-decisions.md#adr-0017-product-versioning-one-semver-for-the-monorepo),
[`docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../../docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md),
[#1211](https://github.com/mgagp/ezkey/issues/1211)). Do not invent a second product line for
mobile; an own cadence later would be a **separate** decision.

| Field | Where | Rule |
|-------|--------|------|
| **`versionName`** | `ezkey_mobile/package.json` `"version"` | Equals the product version `X.Y.Z` (optional matching pre-release suffix when the product tag uses one, e.g. `0.2.0-alpha.1`). Product line starts at `0.2.0` because Play already shipped `0.1.2-alpha` (must not go backwards; ADR-0017 / [#1211](https://github.com/mgagp/ezkey/issues/1211)). Gradle injects this as Android `versionName`. iOS `MARKETING_VERSION` uses the same string. Settings/About show it. It stays put across Play uploads of one cut. |
| **`versionCode`** | `android/app/build.gradle` | Strictly increasing integer per Play upload (hard requirement). Monotonic; independent of SemVer and of the iOS build number. |
| **Play release name** | set by `play-upload-internal.sh` | `'<versionCode> (<versionName>)'` (e.g. `7 (0.2.0)`). Override with `--release-name` only when needed. |

**Trace:** Mobile `versionName` = product `X.Y.Z` with monotonic `versionCode` — store uploads stay
tied to the monorepo product line; Console still needs both code and name to tell uploads apart —
[#1211](https://github.com/mgagp/ezkey/issues/1211).

**Why `versionCode` stays in the release name:** vc4 went to the internal track as bare
`0.1.0-alpha` while alpha showed hand-named `3 (0.1.0-alpha)` — same `versionName` as vc3, so the
Console could not tell the uploads apart. Name releases with both code and name. Sharing a
`versionName` across uploads is expected for one product cut; the code in the release name is what
distinguishes them.

Align from the monorepo parent POM (strips Maven `-SNAPSHOT` for `versionName`):

```bash
./scripts/sync-version.sh
./scripts/check-version-alignment.sh
```

`versionCode` must stay **strictly greater than 6** (Play already shipped vc6). The sync script
raises the workspace floor to **7** when needed; bump further before each Play upload if Console
already has that integer.

### Version bump recipe

Before each Play upload:

1. Open Play Console → the Ezkey app → **App bundle explorer**. Note the highest published `versionCode`.
2. Run `./scripts/sync-version.sh` so `package.json` / `versionName` match the parent POM product SemVer.
3. If the workspace `versionCode` is **not** greater than the Console high-water mark, bump it in `android/app/build.gradle` (must remain > 6).
4. If dependencies changed, run `yarn license:app-data` and commit the JSON snapshot.
5. Build the signed AAB (`./scripts/build-install-release-clean.sh` or `yarn android:bundle:release` with JDK 17).

**Upload history (pre–ADR-0017):** vc4 = `0.1.0-alpha` (internal; same name as vc3 — why the
`versionCode` in the release name exists). vc5 = `0.1.1-alpha`. **vc6 = `0.1.2-alpha`** (#1082 on
RN 0.88.1). Product line `0.2.0` starts at **vc7**. Do not reuse a `versionCode`.

## Upload signed AAB to internal testing (CLI)

Service account (created by Marc, GCP project `ezkey-play`):

| Item | Value |
|------|--------|
| Account | `ezkey-play-publisher@ezkey-play.iam.gserviceaccount.com` |
| Play Console access | `org.ezkey.mobile` only — view app information + **release to testing tracks** (no production) |
| JSON key | Conventionally `C:\Users\marcg\.gradle\ezkey-play-publisher.json` on Rootbeer (outside the repo); **never** commit the key |

Scripts live under `ezkey_mobile/` — run them from that directory. Host prerequisite (not part of the app Gradle build):

```bash
cd ezkey_mobile
pip install -r scripts/requirements-play-upload.txt
```

Build the signed AAB, then upload to the **internal** track as a **draft** by default (review in Play Console before rolling out to testers):

```bash
cd ezkey_mobile
export EZKEY_PLAY_SERVICE_ACCOUNT_JSON="/c/Users/marcg/.gradle/ezkey-play-publisher.json"
./scripts/bundle-release.sh
./scripts/play-upload-internal.sh --dry-run android/app/build/outputs/bundle/release/app-release.aab
./scripts/play-upload-internal.sh android/app/build/outputs/bundle/release/app-release.aab
```

**One edit at a time:** the Play Developer API allows only one open edit per app. On 2026-10-19 a parallel `--dry-run` deleted the edit under a concurrent vc5 upload (`HttpError 400 This Edit has been deleted.`); the retry succeeded alone — never run `play-upload-internal.sh` (dry-run or real) concurrently; dry-run first, then upload only after it has exited.

Optional flags: `--dry-run` (auth + list tracks/versionCodes, delete edit, no commit), `--status draft|completed` (default `draft`), `--release-name` (override the default `'<versionCode> (<versionName>)'`), `--allow-same-version-name` (accepted, no longer required: the same `versionName` warns and continues). The script refuses any track other than `internal` and refuses an AAB whose `versionCode` is not strictly greater than the highest `versionCode` already on any track. On Windows Git Bash set `EZKEY_PYTHON` if `python3` is the Microsoft Store stub. (Why the proto-manifest fix: AAB `base/manifest/AndroidManifest.xml` is aapt2 protobuf `XmlNode`, not binary AXML — the first upload dry-run failed until the reader handled that format.)

### Track hygiene

Each track has one active release; a new upload on that track replaces the previous active release. Older bundles remain in Console history and cannot be deleted — that is normal. Keep tracks tidy on purpose: **alpha** (closed testing) may still serve vc3 `3 (0.1.0-alpha)` to its testers while **internal** moves ahead — either promote the current internal build to alpha or pause/stop alpha when it is unused (Marc’s call). Promotion is **not** automated by `play-upload-internal.sh` (internal-only by design).

## In-app third-party licenses snapshot

The Licenses screen reads the committed snapshot
[`app/data/thirdPartyLicenses.json`](../app/data/thirdPartyLicenses.json). Each row opens the
full license text, bundled in the app (works offline); vendor components (Google ML Kit, Play
services, Play Core) sit in their own section with a link to their terms (#1109). Sources:

| Section | Source | Refresh |
|---------|--------|---------|
| App libraries (JS) | Packages Metro actually puts in a release bundle, per platform (sourcemaps of `react-native bundle --dev false`), not `package.json` alone | `yarn license:app-data` |
| Android libraries + vendor components | [`licenses/android-libraries.json`](../licenses/android-libraries.json), AboutLibraries export of the release runtime classpath; `react-android` and Hermes are added from their npm packages (RN injects their version, so the export omits them) | `yarn license:android` (JDK 17), then `yarn license:app-data` |
| iOS libraries (#1148) | [`ios/Podfile.lock`](../ios/Podfile.lock): React Native (`React-Core`) and Hermes (`hermes-engine`), described from their npm packages. Pods from npm libraries (VisionCamera, Nitro, Keychain…) are already covered by their JS entry. The CocoaPods acknowledgements are **not** used: they cover 10 of 99 pods | `bundle exec pod install` on the Mac, then `yarn license:app-data` |
| C/C++ libraries inside React Native (both platforms) | Folly, glog, {fmt}, double-conversion, fast_float, Boost (+ SocketRocket on iOS), compiled into `react-android` / `ReactNativeDependencies.xcframework`, which ship no license file. Upstream texts committed under [`licenses/texts/`](../licenses/texts/) (fetched at the pinned tag); versions read from `react-native/gradle/libs.versions.toml` (Android) and `ios/Podfile.lock` (iOS) | automatic on an RN bump; re-check the texts on a major version change (`RN_BUNDLED_NATIVE` in the generator) |

License text comes from the package's own `LICENSE` file, else the file of a sibling package from
the same repository (monorepos such as `@react-native/*`), else the standard MIT text with the
`author` from `package.json`.

```bash
cd ezkey_mobile
yarn install --immutable
yarn license:android   # only when native Android deps changed (Gradle, or an RN bump)
yarn license:app-data
```

CI runs:

1. `yarn license:check` (`js-validate`) — direct dependency license allowlist.
2. Snapshot freshness (`js-validate`) — regenerates the JSON and fails if the committed file
   differs, **ignoring `generatedAt`** so timestamp-only drift does not fail. On failure it prints a
   concise package-level diff (added / removed / version or license changes).
3. Android export freshness (`android-jvm-unit-tests`) — re-runs the AboutLibraries export and
   fails if `licenses/android-libraries.json` changed.
4. `Podfile.lock` freshness (`mobile-ios`, macOS) — runs `pod install` and fails if
   `ios/Podfile.lock` changed, so the iOS section cannot lag behind a native dependency change
   made without the Mac.

When the freshness check fails (including on Dependabot PRs): run `yarn install --immutable` then
`yarn license:app-data` from `ezkey_mobile/` and commit `app/data/thirdPartyLicenses.json` on the
same branch. GitHub Actions tests the **PR merge commit** with `main` — if `main` changed
`package.json` / `yarn.lock` after your branch point, merge or rebase onto the current base first,
then regenerate. No bot write token is required; the CI failure message spells out the same fix.

Local equivalent: `yarn license:ci` (or `./scripts/check-third-party-licenses-ci.sh`).

## Client updates after the first official listing

Release builds include a **fail-open** Google Play **flexible** in-app update check (`EzkeyPlayUpdateModule`). It does nothing on debug, sideload, or Play errors. The prompt can only appear after a **later** Play upload is available. This is not a hard minimum-version gate and does not use Auth API `instance-info`.

## Notes

- This app follows a **pull-based** model for pending authentication; do not imply background tracking in the listing unless you add it and disclose it.
- Listing copy must say **Android 12+**. Do not claim older OS support.
