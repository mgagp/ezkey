# Ezkey mobile — utility scripts

## `verify-android-sensitive-storage.sh`

Runs a repeatable Android debug-build verification for local secret handling:

- confirms the app sandbox is accessible through `adb run-as`
- inspects `RKStorage` (AsyncStorage) and verifies `enrollmentProofToken` is absent from the persisted enrollment JSON
- inspects the secure-storage datastore used by `react-native-keychain`
- scans the current `logcat` buffer for obvious proof-token leaks

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

**Pitfall:** One-liners such as `adb shell run-as … strings …/RKStorage | grep …` often exit with **255** and produce no useful output: the app UID sandbox typically does **not** ship `strings`, `sqlite3`, or a full `grep`. Prefer this script (host-side parsing via `adb exec-out`) or the flows in `docs/MOBILE_SECURITY_INVESTIGATION_TECHNIQUES.md`.

## `generate_android_launcher_icons.py`

Rasters the repository root **`logo.svg`** into Android **`mipmap-*`** assets:

- **`ic_launcher_foreground.png`** — logo on a **transparent** square (108dp per density) for **adaptive icons** (API 26+). The pale-blue fill comes from **`values/colors.xml`** + **`mipmap-anydpi-v26/ic_launcher.xml`** so the **entire** masked icon (circle/squircle) is blue, not white at the edges.
- **`ic_launcher.png`** / **`ic_launcher_round.png`** — legacy **composed** icons for API 25 and below.

### Prerequisites

```bash
pip install -r scripts/requirements-generate-icons.txt
```

Uses **CairoSVG** and **Pillow**. On some Windows setups you may need a working Cairo stack; if `pip install cairosvg` fails, try WSL or install [GTK/Cairo](https://www.cairographics.org/) for Windows.

### Run (from `ezkey_mobile`)

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
