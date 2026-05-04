#!/usr/bin/env python3
"""
Generate Google Play listing assets from the Ezkey logo SVG.

- App icon: 512x512 PNG or JPEG, suitable for the Play Store listing icon field.
- Feature graphic: 1024x500 PNG or JPEG, suitable for the Play Store listing
  feature graphic field.

Prerequisites:
  pip install -r scripts/requirements-generate-icons.txt

Usage (from ezkey_mobile directory):
  python scripts/generate_play_store_assets.py
"""

from __future__ import annotations

import argparse
import io
import sys
from pathlib import Path

PLAY_ICON_SIZE = 512
FEATURE_GRAPHIC_SIZE = (1024, 500)
DEFAULT_ICON_BACKGROUND = "#d6e6ff"
DEFAULT_FEATURE_TOP = "#f4f8ff"
DEFAULT_FEATURE_BOTTOM = "#dce9ff"


def _parse_hex_color(s: str) -> tuple[int, int, int, int]:
    s = s.strip().lstrip("#")
    if len(s) == 6:
        return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)
    if len(s) == 8:
        return (
            int(s[0:2], 16),
            int(s[2:4], 16),
            int(s[4:6], 16),
            int(s[6:8], 16),
        )
    raise ValueError(f"Invalid color: {s!r} (use #RRGGBB or #RRGGBBAA)")


def _with_alpha(color: tuple[int, int, int, int], alpha: int) -> tuple[int, int, int, int]:
    return (color[0], color[1], color[2], alpha)


def _ensure_rgb(image):
    from PIL import Image

    if image.mode == "RGB":
        return image
    background = Image.new("RGB", image.size, (255, 255, 255))
    background.paste(image, mask=image.getchannel("A"))
    return background


def _gradient_background(width: int, height: int, top, bottom):
    from PIL import Image

    image = Image.new("RGBA", (width, height))
    pixels = image.load()
    for y in range(height):
        ratio = y / max(1, height - 1)
        row = (
            int(round(top[0] + (bottom[0] - top[0]) * ratio)),
            int(round(top[1] + (bottom[1] - top[1]) * ratio)),
            int(round(top[2] + (bottom[2] - top[2]) * ratio)),
            int(round(top[3] + (bottom[3] - top[3]) * ratio)),
        )
        for x in range(width):
            pixels[x, y] = row
    return image


def _resize_logo(image, edge: int):
    from PIL import Image

    return image.resize((edge, edge), Image.Resampling.LANCZOS)


def _compose_icon(fg_hi, size: int, background_rgba, padding_ratio: float):
    from PIL import Image

    padding = max(0, int(round(size * padding_ratio)))
    inner = max(1, size - (padding * 2))
    logo = _resize_logo(fg_hi, inner)
    canvas = Image.new("RGBA", (size, size), background_rgba)
    offset = ((size - logo.width) // 2, (size - logo.height) // 2)
    canvas.paste(logo, offset, logo)
    return canvas


def _compose_feature_graphic(
    fg_hi,
    width: int,
    height: int,
    top_rgba,
    bottom_rgba,
    logo_ratio: float,
    watermark_ratio: float,
):
    from PIL import Image, ImageDraw, ImageFilter

    background = _gradient_background(width, height, top_rgba, bottom_rgba)
    glow_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow_layer)

    glow_bounds = (
        width // 2 - 210,
        height // 2 - 210,
        width // 2 + 210,
        height // 2 + 210,
    )
    glow_draw.ellipse(glow_bounds, fill=(255, 255, 255, 170))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(radius=36))
    background.alpha_composite(glow_layer)

    watermark_edge = max(1, int(round(height * watermark_ratio)))
    watermark = _resize_logo(fg_hi, watermark_edge)
    watermark_alpha = watermark.getchannel("A").point(lambda px: px * 0.12)
    watermark.putalpha(watermark_alpha)
    watermark_offset = (
        width - watermark.width - 76,
        (height - watermark.height) // 2,
    )
    background.paste(watermark, watermark_offset, watermark)

    main_logo_edge = max(1, int(round(height * logo_ratio)))
    main_logo = _resize_logo(fg_hi, main_logo_edge)
    main_offset = (
        (width - main_logo.width) // 2,
        (height - main_logo.height) // 2,
    )
    background.paste(main_logo, main_offset, main_logo)
    return background


def _save_image(image, target: Path, output_format: str, quality: int) -> int:
    target.parent.mkdir(parents=True, exist_ok=True)
    save_kwargs = {}
    if output_format == "JPEG":
        image = _ensure_rgb(image)
        save_kwargs = {"quality": quality, "optimize": True, "progressive": True}
    else:
        save_kwargs = {"optimize": True}
    image.save(target, format=output_format, **save_kwargs)
    return target.stat().st_size


def _format_bytes(size: int) -> str:
    return f"{size / 1024:.1f} KiB"


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
        description="Generate Google Play icon and feature graphic from the Ezkey logo SVG."
    )
    parser.add_argument(
        "--svg",
        type=Path,
        help="Path to logo.svg (default: <repo root>/logo.svg)",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="Output directory (default: ezkey_mobile/android/play-store-assets)",
    )
    parser.add_argument(
        "--format",
        choices=("png", "jpeg"),
        default="png",
        help="Output image format (default: png)",
    )
    parser.add_argument(
        "--icon-padding",
        type=float,
        default=0.10,
        help="Icon inset padding as a fraction of the edge (default: 0.10)",
    )
    parser.add_argument(
        "--feature-logo-ratio",
        type=float,
        default=0.56,
        help="Feature graphic logo size as a fraction of the 500px height (default: 0.56)",
    )
    parser.add_argument(
        "--feature-watermark-ratio",
        type=float,
        default=0.90,
        help="Feature graphic watermark size as a fraction of the 500px height (default: 0.90)",
    )
    parser.add_argument(
        "--icon-background",
        type=str,
        default=DEFAULT_ICON_BACKGROUND,
        help=f"Icon background color (default: {DEFAULT_ICON_BACKGROUND})",
    )
    parser.add_argument(
        "--feature-top",
        type=str,
        default=DEFAULT_FEATURE_TOP,
        help=f"Feature graphic top gradient color (default: {DEFAULT_FEATURE_TOP})",
    )
    parser.add_argument(
        "--feature-bottom",
        type=str,
        default=DEFAULT_FEATURE_BOTTOM,
        help=f"Feature graphic bottom gradient color (default: {DEFAULT_FEATURE_BOTTOM})",
    )
    parser.add_argument(
        "--raster-size",
        type=int,
        default=1600,
        help="Internal SVG rasterization size before scaling (default: 1600)",
    )
    parser.add_argument(
        "--jpeg-quality",
        type=int,
        default=92,
        help="JPEG quality if --format jpeg is used (default: 92)",
    )
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    ezkey_mobile_dir = script_dir.parent
    repo_root = ezkey_mobile_dir.parent

    svg_path = (args.svg or (repo_root / "logo.svg")).resolve()
    output_dir = (args.output_dir or (ezkey_mobile_dir / "android" / "play-store-assets")).resolve()

    if not svg_path.is_file():
        print(f"ERROR: SVG not found: {svg_path}", file=sys.stderr)
        return 1

    try:
        icon_bg = _parse_hex_color(args.icon_background)
        feature_top = _parse_hex_color(args.feature_top)
        feature_bottom = _parse_hex_color(args.feature_bottom)
    except ValueError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    if not (0.0 <= args.icon_padding < 0.5):
        print("ERROR: --icon-padding must be between 0.0 and 0.5", file=sys.stderr)
        return 1

    if not (0.1 <= args.feature_logo_ratio <= 0.9):
        print("ERROR: --feature-logo-ratio must be between 0.1 and 0.9", file=sys.stderr)
        return 1

    if not (0.1 <= args.feature_watermark_ratio <= 2.0):
        print(
            "ERROR: --feature-watermark-ratio must be between 0.1 and 2.0",
            file=sys.stderr,
        )
        return 1

    if not (1 <= args.jpeg_quality <= 100):
        print("ERROR: --jpeg-quality must be between 1 and 100", file=sys.stderr)
        return 1

    hi = max(PLAY_ICON_SIZE, FEATURE_GRAPHIC_SIZE[0], FEATURE_GRAPHIC_SIZE[1], args.raster_size)
    buf = io.BytesIO()
    try:
        cairosvg.svg2png(
            url=str(svg_path),
            write_to=buf,
            output_width=hi,
            output_height=hi,
        )
    except Exception as exc:
        print(f"ERROR: Failed to rasterize SVG: {exc}", file=sys.stderr)
        return 1

    buf.seek(0)
    fg_hi = Image.open(buf).convert("RGBA")

    output_format = args.format.upper()
    extension = ".jpg" if output_format == "JPEG" else ".png"

    icon = _compose_icon(
        fg_hi,
        PLAY_ICON_SIZE,
        icon_bg,
        args.icon_padding,
    )
    icon_path = output_dir / f"play-icon-512x512{extension}"
    icon_size = _save_image(icon, icon_path, output_format, args.jpeg_quality)

    feature = _compose_feature_graphic(
        fg_hi,
        FEATURE_GRAPHIC_SIZE[0],
        FEATURE_GRAPHIC_SIZE[1],
        feature_top,
        feature_bottom,
        args.feature_logo_ratio,
        args.feature_watermark_ratio,
    )
    feature_path = output_dir / f"feature-graphic-1024x500{extension}"
    feature_size = _save_image(feature, feature_path, output_format, args.jpeg_quality)

    print(f"Wrote {icon_path} ({PLAY_ICON_SIZE}x{PLAY_ICON_SIZE}, {_format_bytes(icon_size)})")
    print(
        f"Wrote {feature_path} "
        f"({FEATURE_GRAPHIC_SIZE[0]}x{FEATURE_GRAPHIC_SIZE[1]}, {_format_bytes(feature_size)})"
    )

    if icon_size > 1024 * 1024:
        print("WARNING: app icon is larger than Google Play's 1 MiB limit.", file=sys.stderr)
        return 2

    if feature_size > 15 * 1024 * 1024:
        print(
            "WARNING: feature graphic is larger than Google Play's 15 MiB limit.",
            file=sys.stderr,
        )
        return 2

    print("Done. Assets are ready for the Google Play Console listing fields.")
    return 0


if __name__ == "__main__":
    sys.exit(main())