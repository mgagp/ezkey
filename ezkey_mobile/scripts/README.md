# Ezkey mobile — utility scripts

## Canonical campaign (Git Bash)

Prefer **`../../ezkey-tests/scripts/run-mobile-real-device.sh`** from the repo root (JUnit creates
attempts, Maestro drives the Pixel, Bash writes `logs/mobile-churn/<session>/` with `rca.md` per
iteration). PowerShell helpers below are **interim**. Do **not** use recover+reset as the default
F2a bootstrap — the Bash runner uses a fresh Admin API `POST` enrollment.

On failure, agent read-order: `SESSION.md` / `SESSION-table.md` → `iterations/<n>/rca.md` →
`logcat-filtered.txt` → full `maestro.log` only if still inconclusive.

Stay-awake (screen-on for the run, restore on exit) is in
`scripts/lib/android-stay-awake.sh`. Opt out with `--no-stay-awake` on the campaign runner, or
`EZKEY_ANDROID_STAY_AWAKE=0`. Wi-Fi ADB is not a plug source, so the wrapper also raises
`screen_off_timeout` for the session.

## `get-fresh-enrollment-seed.ps1`

PowerShell helper for autonomous Maestro enrollment runs on a one-shot enrollment backend.

It performs, in order:

- `POST /api/v1/admin/auth/recover` with username + recovery code
- `POST /api/v1/admin/enrollments/reset` with the returned recovery token
- writes fresh runtime values for Maestro (`ENROLLMENT_ID`, `ENROLLMENT_PROOF_TOKEN`,
  `ENROLLMENT_AUTH_URL`, `ENROLLMENT_CHALLENGE`) to:
  - JSON (`maestro/reports/fresh-enrollment-seed.json`)
  - PowerShell env script (`maestro/reports/fresh-enrollment-seed.ps1`)

Optional switch `-RunMaestro` executes `maestro/flows/pilot_enrollment_full_runtime.yaml`
immediately with the fresh values.

From `ezkey_mobile/`:

```powershell
.\scripts\get-fresh-enrollment-seed.ps1 `
  -AdminApiBaseUrl "http://localhost:8082" `
  -Username "admin" `
  -RecoveryCode "1111-2222-3333-4444-5555-6666-7777-8888" `
  -EnrollmentAuthUrl "https://your-auth-url.example" `
  -RunMaestro
```

If needed, force a specific enrollment id:

```powershell
.\scripts\get-fresh-enrollment-seed.ps1 `
  -AdminApiBaseUrl "http://localhost:8082" `
  -Username "admin" `
  -RecoveryCode "1111-2222-3333-4444-5555-6666-7777-8888" `
  -EnrollmentAuthUrl "https://your-auth-url.example" `
  -EnrollmentId 4
```

## `build-install-debug-clean.sh` (canonical Android debug install)

Preferred debug install path for devices (JDK probe + path preflight + clean + install).
See `AGENTS.md` § Android debug build.

From `ezkey_mobile/`:

```bash
adb devices -l
./scripts/build-install-debug-clean.sh
# or: yarn android:install:debug:clean
```

Options:

| Option | Meaning |
|--------|---------|
| `--skip-clean` | Install existing `app-debug.apk` only |
| `--no-uninstall` | Keep app data; reinstall over same signature |
| `--build-only` | `assembleDebug` only — **no adb device required** (`yarn android:assemble:debug:clean`) |
| `--metro-port PORT` | Metro TCP port (default `8081`; also `EZKEY_METRO_PORT` / `RCT_METRO_PORT`) |
| `--with-metro-reverse` | Re-apply `adb reverse tcp:PORT` every run (auto-on when `ezkey.useMetroInDebug=true`) |

Wireless ADB drops `adb reverse` on reconnect — the script re-applies it before and after
install when Metro reverse is enabled. It also warns when a packager already listening on
that port appears to belong to a different worktree (stale Metro).

## `build-install-release-clean.sh` + `assert-release-production-clean-env.sh`

Release install path. Before Gradle, sources `assert-release-production-clean-env.sh`, which
**fails** if `.env` (or `ENVFILE`) still enables F2a bypass, pending-auth flow trace, or the
pending-auth debug panel. App code also hard-gates F2a on native debug build type.

Contract: [`docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`](../docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md).

After a successful `assembleRelease`, the script also runs
[`check-16kb-alignment.sh`](check-16kb-alignment.sh) on the release APK.

## `bundle-release.sh` + `check-16kb-alignment.sh` (16 KB page-size gate)

`yarn android:bundle:release` → `scripts/bundle-release.sh` → Gradle `bundleRelease` →
`scripts/check-16kb-alignment.sh` on `app-release.aab`.

The check extracts `arm64-v8a` / `x86_64` `.so` files and fails the build if:

- any `PT_LOAD` `p_align` &lt; `0x4000`, or
- any `GNU_RELRO` end (`VirtAddr + MemSiz`) is not 16 KB aligned (except documented residuals —
  RN 0.87.1 Maven prebuilts Decision A / #659, plus Fresco/CameraX/ML Kit AAR prebuilts), or
- for APK inputs, `zipalign -c -P 16 -v 4` fails.

Standalone: `./scripts/check-16kb-alignment.sh path/to/app-release.apk|aab`.

Requires `llvm-readelf` / `readelf` on PATH, or the NDK copy (`llvm-readelf` on Linux,
`llvm-readelf.exe` under `prebuilt/windows-x86_64/bin` on Windows Git Bash).

Policy: [`docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md`](../docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md)
§ *16 KB page-size gate*. Do **not** set `useLegacyPackaging=true` as a workaround.

## `check-third-party-licenses-ci.sh` (license allowlist + snapshot freshness)

`yarn license:ci` → allowlist (`yarn license:check`) then freshness
(`check-third-party-licenses-freshness.mjs`). CI wires this into
`.github/workflows/ezkey-mobile-unit-tests.yml` (`js-validate`).

Regenerate the in-app list after dependency changes (or after merging/rebasing onto a
base that changed `package.json` / `yarn.lock` — CI tests the **merge commit**):

```bash
yarn install --immutable
yarn license:app-data
```

Freshness ignores `generatedAt` only. On failure it prints a concise package-level
diff (added / removed / version or license changes). See
[`docs/MOBILE_PLAY_PUBLISHING.md`](../docs/MOBILE_PLAY_PUBLISHING.md) § *In-app third-party
licenses snapshot*.

## `run-mobile-churn-no-recovery.ps1`

Runs repeated mobile churn iterations without using enrollment recovery/reset:

- creates an auth attempt for an already-verified enrollment via Admin API
- runs the Maestro pending/respond flow on real device
- reads final auth attempt status and writes a CSV summary

Requirements:

- verified enrollment already present on device (for example enrollment 2 / `mobile_tester`)
- valid admin bearer token (`-AdminToken` or `EZKEY_ADMIN_TOKEN`)

Important dual-lane note:

- Demo Device and real phone do not share enrollment state.
- A JSON under `demo-device:/app/data/enrollments/*.json` does not make that enrollment appear on
  the real phone home screen.
- The script now fails fast if `ezkey.e2e.home.enrollment.<id>` is not visible on the phone.

From `ezkey_mobile/`:

```powershell
.\scripts\run-mobile-churn-no-recovery.ps1 `
  -Iterations 5 `
  -EnrollmentId 2 `
  -AdminApiBaseUrl "http://localhost:9080" `
  -AdminToken "ezkey_..."
```

Output summary:

- `maestro/reports/churn-no-recovery/summary.csv`

## `run-mobile-test-campaign.ps1` (3-phase orchestrator)

Implements the mobile test campaign model aligned with Ezkey functional test patterns:

1. **Phase 1** (`Demo Device lane`): obtain Global Admin token by replaying passwordless login
   (`/admin/auth/login` -> `/auth-attempts/pending` -> `/auth-attempts/respond` ->
   `/admin/auth/passwordless-wait`) using Demo Device enrollment material for `mobile_tester`
   (or future `admin.mobile`).
2. **Phase 2** (`Admin API lane`): create/reuse one integration and create one fresh enrollment,
   persist campaign state JSON, optionally bind+verify on phone via Maestro enrollment flow.
3. **Phase 3** (`Real phone lane`): churn loop on phone with Maestro + Admin API auth-attempt checks.

State files:

- `maestro/reports/mobile-test-campaign-state.json`
- `maestro/reports/mobile-test-campaign-summary.csv`

Run all phases:

```powershell
.\scripts\run-mobile-test-campaign.ps1 `
  -Phase all `
  -Username "mobile_tester" `
  -Iterations 5
```

Run one phase:

```powershell
.\scripts\run-mobile-test-campaign.ps1 -Phase phase1 -Username "mobile_tester"
.\scripts\run-mobile-test-campaign.ps1 -Phase phase2 -Username "mobile_tester"
.\scripts\run-mobile-test-campaign.ps1 -Phase phase3 -Username "mobile_tester" -Iterations 5
```

Notes:

- Phase 3 fails fast if no Android device is visible via `adb devices`.
- Phase 3 fails fast if the campaign enrollment tile is not visible on the phone home screen.
- Demo Device is used for token bootstrap only; churn execution remains on real phone.

### Windows path-length caveat (native CMake)

Some React Native native modules can exceed Windows object-path limits during
`installDebug` (errors such as `Filename longer than 260 characters` or
`CMAKE_OBJECT_PATH_MAX`).

`preflight-android-path-length.sh` warns when the Windows path to `ezkey_mobile/`
is longer than **40** characters (override: `EZKEY_ANDROID_PATH_ROOT_MAX`). Short
roots such as `C:\w\p\ezkey_mobile` stay quiet. Longer clones under `C:\Users\...`
are the risky case. The install script also greps the Gradle log for MAX_PATH
failures and prints remediation.

## `preflight-android-path-length.sh`

Standalone path-risk probe for Windows native Android builds.

From `ezkey_mobile/`:

```bash
./scripts/preflight-android-path-length.sh
```

Note: run from Git Bash on Windows for accurate drive-path detection.

Strict mode (fails with exit code 1 when risk is detected):

```bash
./scripts/preflight-android-path-length.sh --strict
```

Yarn alias: `yarn android:preflight:path`.

## `resolve-android-jdk.sh`

Sets `JAVA_HOME` for Android Gradle (never JDK 25 from PATH). **Project standard is JDK 17** (same as CI `actions/setup-java`).

Order: `EZKEY_ANDROID_JAVA_HOME` → macOS `java_home -v 17` → versionless / globbed JDK 17 installs (`C:\Tools\jdk17`, `Program Files\Microsoft\jdk-17*`, Temurin, Linux `/usr/lib/jvm/…`) → Android Studio JBR (17 preferred, else 21). Never picks 21 when a 17 install exists. Do not reintroduce `android/gradle/gradle-daemon-jvm.properties` or Foojay toolchain auto-download — migrating the project to JDK 21 is a separate decision.

## `assert-no-gradle-daemon-jvm.sh`

CI/local guard: fails if `android/gradle/gradle-daemon-jvm.properties` reappears (Android Studio `updateDaemonJvm` / Foojay JDK 21 pin) or if `org.gradle.java.installations.auto-download=true`. Prints removal instructions. `--self-test` proves the negative path (fixture with the forbidden file must fail).

## `run-with-git-bash.mjs`

Yarn entrypoint for `.sh` scripts. On Windows, launches
`C:\Program Files\Git\bin\bash.exe` (override `EZKEY_GIT_BASH`); never the WSL
shim at `System32\bash.exe` (that yields exit 127 for these scripts). On
macOS/Linux, uses `bash` from PATH.

## `dependency-monitor.mjs`

Lightweight dependency monitoring helper for iterative hygiene.

It combines:

- available upgrades (`npm-check-updates`)
- high-severity audit signal (`yarn npm audit --severity high`)
- ecosystem gates for known deferred majors (ESLint 10, Jest 30, TypeScript 7+)

From `ezkey_mobile/`:

```bash
yarn deps:monitor
```

Strict mode (non-zero when actionable upgrades or high-severity audit findings exist):

```bash
yarn deps:monitor:strict
```

History mode (append each run to local NDJSON history):

```bash
yarn deps:monitor:history
```

Default history location: `.monitor/dependency-history.ndjson` (local working tree).

You can override it manually:

```bash
node scripts/dependency-monitor.mjs --history --history-file=.monitor/custom-history.ndjson
```

## `verify-android-sensitive-storage.sh`

Runs a repeatable Android debug-build verification for local secret handling:

- confirms the app sandbox is accessible through `adb run-as`
- inspects `RKStorage` (AsyncStorage) and verifies `enrollmentProofToken` and `integrationPublicKey` are absent from the persisted enrollment JSON
- inspects Android sealed-secret envelope rows stored alongside metadata in AsyncStorage
- checks the legacy `react-native-keychain` datastore only as a migration residue signal on Android
- scans the current `logcat` buffer for obvious proof-token or integration-key leaks

### Run (from `ezkey_mobile`)

```bash
./scripts/verify-android-sensitive-storage.sh
```

Optional package override:

```bash
./scripts/verify-android-sensitive-storage.sh org.ezkey.mobile
```

Requirements:

- connected Android device visible to `adb`
- a debuggable installed app build
- Python (`python3`, `python`, or `py -3`) on the host

This script is intended as a practical investigation aid, not as a formal cryptographic proof.

## `mobile-doctor-curated` (preferred hygiene pass)

Punctual curated pass — keyword **`mobile-doctor-curated`**.

Runs **react-doctor** + **Semgrep** (Ezkey mobile pack) + **Detekt**, then curates into P1/P2/P3.

From `ezkey_mobile/`:

```bash
yarn doctor:curated
./scripts/mobile-doctor-curated.sh
```

Optional flags: `--skip-react-doctor`, `--skip-semgrep`, `--skip-detekt`, `--curate-only`.

Outputs (gitignored under `logs/`):

- `logs/mobile-doctor/mobile-doctor.curated.md`
- `logs/mobile-doctor/mobile-doctor.curated.json`
- `logs/mobile-doctor/raw/`

Config: `config/mobile-doctor/suppressions.json`  
Campaign notes: `product-docs/global/hygiene/mobile-doctor/`  
Agent contract: `AGENTS.md` § Mobile doctor-curated pass

`yarn quality:pipeline` delegates to this script (legacy alias).

**Not in v1:** Biome (rejected for curated pass — ESLint+Prettier remain the lint/format gates).

## `code-quality-curator.mjs` (legacy multi-format reports)

Optional legacy curator for Semgrep/Detekt snapshots under `.monitor/`. Prefer
`yarn doctor:curated` for hygiene campaigns.

Default expected input snapshots (relative to `ezkey_mobile/`):

- `.monitor/semgrep-report.json`
- `.monitor/detekt.sarif`
- `.monitor/detekt-report.json`

Outputs (default):

- `.monitor/code-quality/curated-report.normalized.json`
- `.monitor/code-quality/curated-report.md`
- `.monitor/code-quality/curated-report.html`

From `ezkey_mobile/`:

```bash
yarn quality:curate
```

Markdown only:

```bash
yarn quality:curate:md
```

HTML only:

```bash
yarn quality:curate:html
```

Useful options:

```bash
node scripts/code-quality-curator.mjs \
  --inputs=.monitor/semgrep-report.json,.monitor/detekt.sarif \
  --output-dir=.monitor/code-quality \
  --report-name=run-001 \
  --exclude-path-fragments=__tests__/,app/hooks/__tests__/ \
  --top=40 \
  --format=all
```

Optional strict mode for later CI gating:

```bash
node scripts/code-quality-curator.mjs --fail-on-critical
```

## `semgrep/rules/mobile-security.yml` + `semgrep-scan.mjs`

Starter Semgrep pack for iteration 2 (10 focused rules):

- React Native TS/JS: direct `fetch`, sensitive console logging, AsyncStorage sensitive keys,
  hardcoded bearer token literals, hardcoded non-TLS URLs.
- Android Kotlin: `Random` usage/import, `Base64.DEFAULT`, sensitive `Log.*` content,
  insecure `AES/ECB/PKCS5Padding`.

The scan runner supports:

- local `semgrep` CLI when installed,
- Docker fallback (`semgrep/semgrep`) when CLI is missing.

From `ezkey_mobile/`:

```bash
node scripts/semgrep-scan.mjs
```

Or via package scripts:

```bash
yarn quality:semgrep:scan
yarn quality:semgrep:report
```

Generated artifacts:

- `.monitor/semgrep-report.json`
- `.monitor/code-quality/iteration2-semgrep.normalized.json`
- `.monitor/code-quality/iteration2-semgrep.md`
- `.monitor/code-quality/iteration2-semgrep.html`

Optional suppression support for iteration 3:

- copy `scripts/quality-suppressions.example.json` to a local file such as
  `.monitor/code-quality-suppressions.json`
- run the curator with `--suppression-file=.monitor/code-quality-suppressions.json`
- suppressed findings stay visible in the report, but no longer count as actionable noise

Example:

```bash
node scripts/code-quality-curator.mjs \
  --inputs=.monitor/semgrep-report.json \
  --report-name=iteration3-semgrep \
  --suppression-file=.monitor/code-quality-suppressions.json
```

## `detekt/detekt.yml` + `detekt-scan.mjs`

Detekt adds a Kotlin-specific static analysis angle complementary to Semgrep.

From `ezkey_mobile/`:

```bash
node scripts/detekt-scan.mjs
```

Or via package scripts:

```bash
yarn quality:detekt:scan
yarn quality:detekt:report
```

Generated artifact:

- `.monitor/detekt.sarif`

## `quality-pipeline.mjs` (legacy alias)

Delegates to `mobile-doctor-curated.mjs`. Prefer:

```bash
yarn doctor:curated
```

Campaign HITL notes use `product-docs/global/hygiene/mobile-doctor/TEMPLATE.md` (not the old Lane A/B paste block).

**Pitfall:** One-liners such as `adb shell run-as … strings …/RKStorage | grep …` often exit with **255** and produce no useful output: the app UID sandbox typically does **not** ship `strings`, `sqlite3`, or a full `grep`. Prefer this script (host-side parsing via `adb exec-out`) or the flows in `docs/MOBILE_SECURITY_INVESTIGATION_TECHNIQUES.md`.

## `generate_android_launcher_icons.py`

Rasters the repository root **`logo.svg`** into Android **`mipmap-*`** assets:

- **`ic_launcher_foreground.png`** — logo on a **transparent** square (108dp per density) for **adaptive icons** (API 26+). The pale-blue fill comes from **`values/colors.xml`** + **`mipmap-anydpi-v26/ic_launcher.xml`** so the **entire** masked icon (circle/squircle) is blue, not white at the edges.
- **`ic_launcher.png`** / **`ic_launcher_round.png`** — legacy **composed** icons for API 25 and below.

### Android launcher prerequisites

```bash
pip install -r scripts/requirements-generate-icons.txt
```

Uses **CairoSVG** and **Pillow**. On some Windows setups you may need a working Cairo stack; if `pip install cairosvg` fails, try WSL or install [GTK/Cairo](https://www.cairographics.org/) for Windows.

### Run the launcher generator (from `ezkey_mobile`)

```bash
python scripts/generate_android_launcher_icons.py
```

Defaults:

- SVG: `<repo root>/logo.svg`
- Output: `android/app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`

Optional: `--svg`, `--res`, `--padding` (default `0.08`), `--background` (default `#d6e6ff`, matching `ezkey-admin-ui` `--color-bg` / main pale-blue backdrop).

### After generation

Rebuild and install:

```bash
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The manifest already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`; no manifest change is required.

**iOS:** This script does not update `ios/` assets; use Xcode’s asset catalog or a separate export if you need matching icons on iOS.

## `generate_play_store_assets.py`

Generates Google Play listing assets from the repository root **`logo.svg`**:

- **`play-icon-512x512.png`** — square app icon for the Play Console app icon field
- **`feature-graphic-1024x500.png`** — wide feature graphic for the Play Console presentation image field

By default the script uses the Ezkey pale-blue background and a simple branded feature graphic layout with a centered logo and subtle watermark.

### Play listing prerequisites

```bash
pip install -r scripts/requirements-generate-icons.txt
```

### Run the Play asset generator (from `ezkey_mobile`)

```bash
python scripts/generate_play_store_assets.py
```

Defaults:

- SVG: `<repo root>/logo.svg`
- Output: `android/play-store-assets/`
- Format: `png`

Useful options:

- `--format png|jpeg`
- `--output-dir <path>`
- `--icon-padding 0.10`
- `--feature-logo-ratio 0.56`
- `--feature-watermark-ratio 0.90`
- `--icon-background '#d6e6ff'`
- `--feature-top '#f4f8ff'`
- `--feature-bottom '#dce9ff'`

The script prints each output file path, pixel dimensions, and final file size, and exits with a warning status if a generated file exceeds Google Play's size limits.
