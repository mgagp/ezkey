# Ezkey mobile — utility scripts

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

**Agents and maintainers:** use this for a clean debug build on a connected device. It resolves JDK 17/21 (`resolve-android-jdk.sh`), checks `adb` first, uninstalls `org.ezkey.mobile`, runs `gradlew clean installDebug`, and launches the app.

From `ezkey_mobile/`:

```bash
adb devices -l
./scripts/build-install-debug-clean.sh
```

Options:

- `--skip-clean` — install existing `app-debug.apk` only (device reconnected after a long build).
- `--no-uninstall` — keep data; reinstall over same signature.

Yarn alias: `yarn android:install:debug:clean`.

Before build/install, the script now runs a companion preflight:

- `scripts/preflight-android-path-length.sh` - estimates Windows native object
  path lengths for known React Native codegen offenders and warns early when
  MAX_PATH risk is high.

### Windows path-length caveat (native CMake)

Some React Native native modules can exceed Windows object-path limits during
`installDebug` (errors such as `Filename longer than 260 characters` or
`CMAKE_OBJECT_PATH_MAX`).

The script now detects this case and prints a targeted remediation message.
Preferred fix: run from a shorter workspace root on the same drive (for example
`C:\\w\\ezkey-worktree2`) and rerun the script.

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

Sets `JAVA_HOME` to JDK 17/21 (never JDK 25 from PATH). Probes Android Studio JBR (including `Android Studio1`), `C:\Tools\jdk17`, Microsoft JDK 17, and macOS `java_home`. Override with `EZKEY_ANDROID_JAVA_HOME`.

## `dependency-monitor.mjs`

Lightweight dependency monitoring helper for iterative hygiene.

It combines:

- available upgrades (`npm-check-updates`)
- high-severity audit signal (`yarn npm audit --severity high`)
- ecosystem gates for known deferred majors (ESLint 10, Jest 30)

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

## `code-quality-curator.mjs`

Curates findings from Biome, Semgrep, and Detekt/SARIF into one normalized model,
then generates readable reports.

Default expected input snapshots (relative to `ezkey_mobile/`):

- `.monitor/biome-report.json`
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
  --inputs=.monitor/biome-report.json,.monitor/semgrep-report.json,.monitor/detekt.sarif \
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

## `quality-pipeline.mjs` (unified invocation)

Runs the current quality signal sources in one pass, then generates one consolidated prioritized report
through the curator model.

Current pipeline includes:

- Semgrep scan
- Detekt scan
- Curator consolidation (`.monitor/biome-report.json` included automatically if present)

From `ezkey_mobile/`:

```bash
yarn quality:pipeline
```

With explicit report name:

```bash
node scripts/quality-pipeline.mjs --report-name=quality-unified
```

Default consolidated outputs:

- `.monitor/code-quality/quality-unified.normalized.json`
- `.monitor/code-quality/quality-unified.md`
- `.monitor/code-quality/quality-unified.html`

### Lane A/B triage routine (continuous hygiene)

Goal: keep quality work continuous without turning it into heavy backlog process.

- Lane A (short-term actionable): production-impacting correctness, resilience, and security findings.
- Lane B (continuous hygiene): maintainability/style/test-noise findings handled opportunistically.

Recommended cadence per cycle:

1. run `yarn quality:pipeline:report`
2. open `.monitor/code-quality/quality-unified.md`
3. pick up to 3 items for Lane A
4. capture up to 5 items for Lane B (fix now or defer)
5. ship one small focused lot

Copy-paste triage template:

```md
## Quality cycle YYYY-MM-DD

Run:
- command: `yarn quality:pipeline:report`
- report: `.monitor/code-quality/quality-unified.md`

Lane A (act now, max 3)
1. [rule] file:line - why this matters now
2. [rule] file:line - why this matters now
3. [rule] file:line - why this matters now

Lane B (hygiene, max 5)
1. [rule] file:line - fix now | defer
2. [rule] file:line - fix now | defer
3. [rule] file:line - fix now | defer
4. [rule] file:line - fix now | defer
5. [rule] file:line - fix now | defer

Decision summary
- shipped this cycle:
- deferred:
- note for next cycle:
```

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
