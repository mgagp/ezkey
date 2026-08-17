# Mobile release signing & versioning

How to produce a Play Store-ready Android App Bundle (AAB) for Ezkey Mobile.

## 1. Generate the upload keystore (one-time)

The **upload key** is what you use to sign every AAB you upload to the Play Console.
Google Play App Signing then re-signs the artifact with its own managed key before
distribution. If your upload key is ever lost or compromised, you can rotate it
through the Play Console without users needing to reinstall the app.

Generate the keystore once on your workstation, **outside this repository**:

```powershell
# From an empty directory of your choice (NOT the repo)
keytool -genkeypair -v `
  -keystore ezkey-upload.keystore `
  -alias ezkey-upload `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -dname "CN=Ezkey, OU=Mobile, O=Ezkey, L=Pincourt, ST=QC, C=CA"
```

You will be prompted for a **store password** and a **key password**. Use long,
distinct values and store them in a password manager (1Password, Bitwarden, etc.).

> Recommended storage: keep the `.keystore` file in a password-manager attachment
> (or an encrypted offline backup), and keep a separate copy in
> `~/.gradle/ezkey-upload.keystore` on your dev machine for local builds.

## 2. Configure Gradle to find the keystore

Add the credentials to **`~/.gradle/gradle.properties`** (user-scope, never
committed). Replace the values with what you used above:

```properties
EZKEY_UPLOAD_STORE_FILE=C:\\Users\\<you>\\.gradle\\ezkey-upload.keystore
EZKEY_UPLOAD_STORE_PASSWORD=<store password>
EZKEY_UPLOAD_KEY_ALIAS=ezkey-upload
EZKEY_UPLOAD_KEY_PASSWORD=<key password>
```

When all four properties are present **and** the keystore file exists,
`android/app/build.gradle` registers a `release` signing config and uses it for
release builds. When any property is missing, release builds **fall back to the
debug keystore** with a loud warning so that local debug workflows keep working,
but the resulting artifact **cannot** be uploaded to the Play Console.

For CI, set the same names as `ORG_GRADLE_PROJECT_EZKEY_UPLOAD_*` environment
variables (Gradle automatically maps them to project properties).

## 3. Build a signed AAB

```bash
yarn android:bundle:release
```

The bundle lands at:

```
ezkey_mobile/android/app/build/outputs/bundle/release/app-release.aab
```

Verify the signature:

```bash
jarsigner -verify -verbose -certs app-release.aab
```

The output should contain `jar verified` and the certificate CN you set above.

## 4. Test the signed AAB on a device

The `.aab` is the Play Store delivery format; you need
[`bundletool`](https://github.com/google/bundletool) to install it locally:

```bash
bundletool build-apks --bundle=app-release.aab --output=ezkey.apks `
  --ks=<path to keystore> --ks-key-alias=ezkey-upload
bundletool install-apks --apks=ezkey.apks
```

Run the critical end-to-end flow with R8 enabled (QR scan → enroll → pending
auth → respond) on a real device or emulator. If anything crashes only in
release mode, it is almost always a missing ProGuard `-keep` rule; add it to
`android/app/proguard-rules.pro` and rebuild.

## 5. Version bumping

Edit **`ezkey_mobile/package.json`** and bump the semantic version there before each
Play Store upload. Android `versionName` is injected from that file during the build,
so `package.json` is the human-readable source of truth:

```gradle
versionCode 2     // monotonically increasing integer; +1 per Play upload
```

Confirm the next integer against **Play Console → App bundle explorer** before upload. The
workspace default is `2` because the experimental drop used `versionCode` `1`. Then update
**`ezkey_mobile/android/app/build.gradle`** only for that Android-specific counter:

```gradle
versionCode 2
```

Example: if `package.json` contains `"version": "1.0.1"`, Android will package
that same value as `versionName`, and the About screen will surface `1.0.1` from
the same source.

> Play Console rejects an AAB whose `versionCode` is not strictly greater than
> the previously published one in the same track.

A future iteration can automate this from `git describe`; for v1 the manual
process is intentionally explicit and reviewable.

## 6. What never goes in the repo

- `*.keystore` (already in `.gitignore`, except `debug.keystore`).
- `~/.gradle/gradle.properties`.
- Any screenshot or recording revealing real enrollment data.

When in doubt, run `git status` before committing.
