# Ezkey mobile — utility scripts

## `build-install-debug-clean.sh` (canonical Android debug install)

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

- `.monitor/biome.json`
- `.monitor/semgrep.json`
- `.monitor/detekt.sarif`
- `.monitor/detekt.json`

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
  --inputs=.monitor/biome.json,.monitor/semgrep.json,.monitor/detekt.sarif \
  --output-dir=.monitor/code-quality \
  --report-name=run-001 \
  --top=40 \
  --format=all
```

Optional strict mode for later CI gating:

```bash
node scripts/code-quality-curator.mjs --fail-on-critical
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
