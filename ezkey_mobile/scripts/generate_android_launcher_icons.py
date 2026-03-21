#!/usr/bin/env python3
"""
Generate Android launcher assets from the Ezkey logo SVG.

- API 26+ (adaptive icons): solid pale-blue background (@color/ic_launcher_background) fills
  the full icon mask; the logo is on a separate foreground layer (transparent PNG). This avoids
  a white ring around the icon on circular launchers.
- API < 26: legacy square PNGs (ic_launcher.png / ic_launcher_round.png) with composed bg+logo.

Prerequisites:
  pip install -r scripts/requirements-generate-icons.txt

Usage (from ezkey_mobile directory):
  python scripts/generate_android_launcher_icons.py

Then rebuild the app (e.g. cd android && ./gradlew assembleDebug) and adb install.
"""

from __future__ import annotations

import argparse
import io
import sys
from pathlib import Path

# Per mipmap folder: legacy launcher size (px) and adaptive-icon layer size (108dp in px).
# Adaptive foreground/background layers are 108dp; see
# https://developer.android.com/develop/ui/views/launch-icon-design-adaptive
DENSITY: dict[str, dict[str, int]] = {
    "mipmap-mdpi": {"legacy": 48, "adaptive": 108},
    "mipmap-hdpi": {"legacy": 72, "adaptive": 162},
    "mipmap-xhdpi": {"legacy": 96, "adaptive": 216},
    "mipmap-xxhdpi": {"legacy": 144, "adaptive": 324},
    "mipmap-xxxhdpi": {"legacy": 192, "adaptive": 432},
}


def _parse_hex_color(s: str) -> tuple[int, int, int, int]:
    s = s.strip().lstrip("#")
    if len(s) == 6:
        r = int(s[0:2], 16)
        g = int(s[2:4], 16)
        b = int(s[4:6], 16)
        return (r, g, b, 255)
    if len(s) == 8:
        r = int(s[0:2], 16)
        g = int(s[2:4], 16)
        b = int(s[4:6], 16)
        a = int(s[6:8], 16)
        return (r, g, b, a)
    raise ValueError(f"Invalid color: {s!r} (use #RRGGBB or #RRGGBBAA)")


def main() -> int:
    try:
        import cairosvg
        from PIL import Image
    except ImportError:
        print(
            "ERROR: cairosvg and Pillow are required.\n"
            "  pip install -r scripts/requirements-generate-icons.txt",
            file=sys.stderr,
        )
        return 1

    parser = argparse.ArgumentParser(
        description="Generate mipmap launcher icons from logo.svg for ezkey_mobile Android."
    )
    parser.add_argument(
        "--svg",
        type=Path,
        help="Path to logo.svg (default: <repo root>/logo.svg)",
    )
    parser.add_argument(
        "--res",
        type=Path,
        help="Android res directory (default: ezkey_mobile/android/app/src/main/res)",
    )
    parser.add_argument(
        "--padding",
        type=float,
        default=0.08,
        help="Legacy icon: inset padding as fraction of edge (default: 0.08)",
    )
    parser.add_argument(
        "--foreground-ratio",
        type=float,
        default=0.62,
        help=(
            "Adaptive foreground: logo scales to this fraction of the 108dp layer edge "
            "(default: 0.62, inside the 66dp safe zone)"
        ),
    )
    parser.add_argument(
        "--background",
        type=str,
        default="#d6e6ff",
        help=(
            "Legacy icon only — composed background (default: #d6e6ff; must match "
            "values/colors.xml ic_launcher_background for API 26+)"
        ),
    )
    parser.add_argument(
        "--raster-size",
        type=int,
        default=512,
        help="Internal rasterization size before scaling (default: 512)",
    )
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    ezkey_mobile_dir = script_dir.parent
    repo_root = ezkey_mobile_dir.parent

    svg_path = (args.svg or (repo_root / "logo.svg")).resolve()
    res_dir = (args.res or (ezkey_mobile_dir / "android/app/src/main/res")).resolve()

    if not svg_path.is_file():
        print(f"ERROR: SVG not found: {svg_path}", file=sys.stderr)
        return 1

    if not res_dir.is_dir():
        print(f"ERROR: res directory not found: {res_dir}", file=sys.stderr)
        return 1

    try:
        bg = _parse_hex_color(args.background)
    except ValueError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1

    hi = max(64, args.raster_size)
    buf = io.BytesIO()
    try:
        cairosvg.svg2png(
            url=str(svg_path),
            write_to=buf,
            output_width=hi,
            output_height=hi,
        )
    except Exception as e:
        print(f"ERROR: Failed to rasterize SVG: {e}", file=sys.stderr)
        return 1

    buf.seek(0)
    fg_hi = Image.open(buf).convert("RGBA")

    for folder, sizes in DENSITY.items():
        out_dir = res_dir / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        legacy_px = sizes["legacy"]
        adaptive_px = sizes["adaptive"]

        # API < 26: flat square bitmap (unchanged behaviour for old devices).
        icon = _compose_legacy_icon(fg_hi, legacy_px, args.padding, bg)
        out_buf = io.BytesIO()
        icon.save(out_buf, format="PNG")
        data = out_buf.getvalue()
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            target = out_dir / name
            target.write_bytes(data)
            print(f"Wrote {target}")

        # API 26+: foreground layer only (transparent); background is @color in XML.
        fg_layer = _compose_adaptive_foreground(
            fg_hi, adaptive_px, args.foreground_ratio
        )
        out_buf = io.BytesIO()
        fg_layer.save(out_buf, format="PNG")
        fg_path = out_dir / "ic_launcher_foreground.png"
        fg_path.write_bytes(out_buf.getvalue())
        print(f"Wrote {fg_path}")

    print(
        "Done. Rebuild the Android app (e.g. cd android && ./gradlew assembleDebug) and adb install."
    )
    return 0


def _compose_legacy_icon(fg_hi, size: int, padding_ratio: float, bg_rgba: tuple[int, int, int, int]):
    from PIL import Image

    pad = max(0, int(round(size * padding_ratio)))
    inner = max(1, size - 2 * pad)
    fg = fg_hi.resize((inner, inner), Image.Resampling.LANCZOS)
    bg = Image.new("RGBA", (size, size), bg_rgba)
    offset = ((size - fg.width) // 2, (size - fg.height) // 2)
    bg.paste(fg, offset, fg)
    return bg


def _compose_adaptive_foreground(fg_hi, canvas_size: int, inner_ratio: float):
    """108dp layer: logo on fully transparent background, centered."""
    from PIL import Image

    inner = max(1, int(round(canvas_size * inner_ratio)))
    fg = fg_hi.resize((inner, inner), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    offset = ((canvas_size - fg.width) // 2, (canvas_size - fg.height) // 2)
    out.paste(fg, offset, fg)
    return out


if __name__ == "__main__":
    sys.exit(main())
