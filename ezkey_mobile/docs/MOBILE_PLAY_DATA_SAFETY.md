# Ezkey Mobile — Play Data Safety answers

Draft answers for the Google Play **Data safety** form, taken from current app behavior
(2026-08-14). Re-verify against [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml)
and `package.json` before each listing update. This is not legal advice.

## Declared permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | Auth API calls for enrollment bind/verify and pending/respond. |
| `CAMERA` | Scan enrollment QR codes only. Images are not stored. |
| `USE_BIOMETRIC` | Optional local "confirm before approvals" on this device. Not sent to any server. |
| `HIDE_OVERLAY_WINDOWS` | Hides other apps' overlays while the Approve / Deny screen is shown (tapjacking defense, #1154). Normal permission, no user prompt. |

Merged from libraries (present in the release APK dump, not declared in our manifest):

| Permission | Likely source |
| --- | --- |
| `USE_FINGERPRINT` | androidx.biometric (legacy alias of `USE_BIOMETRIC`) |
| `ACCESS_NETWORK_STATE` | Play Core / Play services (flexible in-app updates) |

## Data collected / shared (Play categories)

Ezkey Mobile is a companion authenticator for a **self-hosted** Ezkey installation. The Play
listing operator (the Ezkey project) does **not** operate a telemetry, analytics, or advertising
backend for this app.

| Play category | Answer | Notes |
| --- | --- | --- |
| Location | Not collected | — |
| Personal info (name, email, phone) | Not collected by the app publisher | Enrollment labels come from the operator's installation (`instance-info` branding). |
| Financial info | Not collected | — |
| Health | Not collected | — |
| Messages | Not collected | — |
| Photos / videos | Not collected | Camera is used for live QR decode only. |
| Audio | Not collected | — |
| Files and docs | Not collected | — |
| Calendar | Not collected | — |
| Contacts | Not collected | — |
| App activity | Not collected by the publisher | Pending auth is **pull-based** (user opens the app and checks). No background tracking. |
| App info and performance | Not collected | No Crashlytics / analytics SDK. ML Kit usage metrics are blocked on-device (see below). |
| Device or other IDs | Not collected | Device cryptographic keys stay in Android Keystore. No advertising ID. ML Kit's per-installation id is never uploaded (see below). |

### Third-party SDK telemetry (ML Kit)

Play counts data an SDK sends as data the app collects. The Android QR scanner uses Google ML
Kit, whose [data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
lists diagnostics and usage metrics (device model, OS, app version, per-installation id,
latency, error codes) sent through `com.google.android.datatransport`. Scanned images and QR
content never leave the device.

The app manifest removes datatransport's only upload backend (CCT), so queued events are
deleted instead of sent; the QR scanner itself is unaffected. CI fails if any datatransport
backend reappears in the merged release manifest (#1147). Play builds up to versionCode 6 shipped
with the backend enabled (internal and closed testing tracks, one tester at the time).

**iOS:** ML Kit is not linked (the QR code is decoded with AVFoundation). No analytics or telemetry
SDK is installed, and every privacy manifest shipped (the app's `PrivacyInfo.xcprivacy` and each
pod's, including those inside prebuilt xcframeworks) declares no tracking and no collected data.
CI (`mobile-ios`, `scripts/ios-privacy-check.sh`) fails if a known telemetry pod appears in
`ios/Podfile.lock` or a privacy manifest starts declaring tracking or collection.

## Data stored on the device (not "collected" by Ezkey.org)

- Enrollment metadata and sealed local secrets (`enrollmentProofToken`, integration public key)
- Per-enrollment EC P-256 signing keys in Android Keystore (StrongBox when available)
- Language preference, local security preference (standard vs confirm-before-approvals)
- Optional Play update "dismissed versionCode" in AsyncStorage

These stay on the phone unless the user deletes enrollments or uses Danger Zone clear-all.

They are never backed up or transferred. `android:allowBackup="false"` covers Android 11 and
earlier semantics, and `res/xml/data_extraction_rules.xml` excludes every domain from cloud
backup, device-to-device transfer and cross-platform transfer (#1154). Keystore keys cannot leave
the phone, so a copied enrollment list would only show enrollments that cannot sign; on a new
phone the user re-enrolls.

The Approve / Deny screen sets `FLAG_SECURE` while focused (no screenshot, screen recording or
Recents thumbnail of a pending request); the rest of the app stays capturable.

## Data transmitted to the operator's installation

Only Auth API protocol traffic to the **Auth URL encoded in the enrollment QR** (the self-hosted
instance the user enrolled with): bind, verify, pending, respond, and enrolled instance-info
refresh. The Ezkey project does not receive that traffic unless the user enrolled with an Ezkey-operated instance (for example the community preview).

## Security practices (Play checkboxes)

- Data is encrypted in transit (platform TLS).
- Users can request that data be deleted: they delete enrollments or clear all local data in the
  app; server-side enrollment lifecycle is operated by their Ezkey administrator.
- The app is not targeted at children.

## Privacy policy URL

`https://ezkey.org/privacy.html`
