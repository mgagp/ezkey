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

## Technical

- [x] **Release signing** path exists (upload key outside the repo). Repeat for the **final GA AAB** before submit.
- [x] **Version policy** documented below (`versionCode` + `versionName` bump every Play upload; release name `'<code> (<name>)'`). In-app chrome says Public alpha (plus git short SHA on About), not `v1.0.0`.
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

Keep Play uploads readable without inventing a second versioning system:

| Field | Where | Rule |
|-------|--------|------|
| **`versionCode`** | `android/app/build.gradle` | Strictly increasing integer per Play upload (hard requirement). Bump by PR before each upload. |
| **`versionName`** | `ezkey_mobile/package.json` `"version"` | Semver with a pre-release tag while in public alpha: `0.y.z-alpha`. **Bump on every Play upload** (patch for fixes, minor for features). Gradle injects this as Android `versionName`; Settings/About show the same string. |
| **Play release name** | set by `play-upload-internal.sh` | `'<versionCode> (<versionName>)'` (e.g. `4 (0.1.1-alpha)`). Override with `--release-name` only when needed. |

**Why this exists:** vc4 went to the internal track as bare `0.1.0-alpha` while alpha showed hand-named `3 (0.1.0-alpha)` — same `versionName` as vc3, so the Console could not tell the uploads apart. Name releases with both code and name, and bump `versionName` every upload.

### Version bump recipe

Before each Play upload:

1. Open Play Console → the Ezkey app → **App bundle explorer**. Note the highest published `versionCode`.
2. If the workspace `versionCode` is **not** greater than that number, bump it in `android/app/build.gradle`.
3. Bump `ezkey_mobile/package.json` `"version"` for this upload (patch or minor under `0.y.z-alpha`).
4. If dependencies changed, run `yarn license:app-data` and commit the JSON snapshot.
5. Build the signed AAB (`./scripts/build-install-release-clean.sh` or `yarn android:bundle:release` with JDK 17).

**Upload history:** vc4 = `0.1.0-alpha` (internal; same name as vc3 — why the policy exists). **vc5 = `0.1.1-alpha`** (patch; first upload under the policy). Do not reuse a `versionCode`.

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

Optional flags: `--dry-run` (auth + list tracks/versionCodes, delete edit, no commit), `--status draft|completed` (default `draft`), `--release-name` (override the default `'<versionCode> (<versionName>)'`), `--allow-same-version-name` (escape hatch if the AAB `versionName` already appears on a Play release — default is refuse). The script refuses any track other than `internal` and refuses an AAB whose `versionCode` is not strictly greater than the highest `versionCode` already on any track. On Windows Git Bash set `EZKEY_PYTHON` if `python3` is the Microsoft Store stub. (Why the proto-manifest fix: AAB `base/manifest/AndroidManifest.xml` is aapt2 protobuf `XmlNode`, not binary AXML — the first upload dry-run failed until the reader handled that format.)

### Track hygiene

Each track has one active release; a new upload on that track replaces the previous active release. Older bundles remain in Console history and cannot be deleted — that is normal. Keep tracks tidy on purpose: **alpha** (closed testing) may still serve vc3 `3 (0.1.0-alpha)` to its testers while **internal** moves ahead — either promote the current internal build to alpha or pause/stop alpha when it is unused (Marc’s call). Promotion is **not** automated by `play-upload-internal.sh` (internal-only by design).

## In-app third-party licenses snapshot

The Licenses screen reads the committed snapshot
[`app/data/thirdPartyLicenses.json`](../app/data/thirdPartyLicenses.json), generated from
`package.json` `dependencies` only:

```bash
cd ezkey_mobile
yarn install --immutable
yarn license:app-data
```

CI (`ezkey-mobile-unit-tests` → `js-validate` → `yarn license:ci`) runs:

1. `yarn license:check` — direct dependency license allowlist.
2. Snapshot freshness — regenerates the JSON and fails if the committed file differs, **ignoring
   `generatedAt`** so timestamp-only drift does not fail. On failure it prints a concise
   package-level diff (added / removed / version or license changes).

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
