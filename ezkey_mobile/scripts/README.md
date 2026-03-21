# Ezkey mobile — utility scripts

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
