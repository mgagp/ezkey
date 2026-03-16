#!/usr/bin/env python3
"""
Prepare EZKey Admin UI brand assets from the repo logo.

- Copies logo.svg to ezkey-admin-ui/public/logo.svg
- Builds favicon.svg from the logo (same 3 main paths, viewBox + 32x32, no empty paths)
- Updates ezkey-admin-ui/index.html to use /favicon.svg

Run from repo root: python scripts/prepare-admin-ui-brand-assets.py
"""

import re
import shutil
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
LOGO_SOURCE = REPO_ROOT / "logo.svg"
ADMIN_UI_PUBLIC = REPO_ROOT / "ezkey-admin-ui" / "public"
INDEX_HTML = REPO_ROOT / "ezkey-admin-ui" / "index.html"


def main() -> None:
    if not LOGO_SOURCE.exists():
        raise SystemExit(f"Logo not found: {LOGO_SOURCE}")

    ADMIN_UI_PUBLIC.mkdir(parents=True, exist_ok=True)

    with open(LOGO_SOURCE, encoding="utf-8") as f:
        lines = f.readlines()

    # 1) Copy full logo to public
    logo_dest = ADMIN_UI_PUBLIC / "logo.svg"
    shutil.copy2(LOGO_SOURCE, logo_dest)
    print(f"Copied logo -> {logo_dest.relative_to(REPO_ROOT)}")

    # 2) Build favicon: keep only xml decl, svg (with viewBox + 32x32), and paths with fill="#3076DF"
    #    (skip empty/tiny paths)
    favicon_lines = []
    favicon_lines.append(lines[0])  # <?xml ...?>

    # Replace svg tag: add viewBox and set width/height to 32
    svg_line = lines[1]
    if 'width="2022"' in svg_line and 'height="2022"' in svg_line:
        svg_line = re.sub(
            r'width="2022"\s+height="2022"',
            'viewBox="0 0 2022 2022" width="32" height="32"',
            svg_line,
        )
    favicon_lines.append(svg_line)

    # Keep only path elements that have substantial content (fill="#3076DF" and non-empty d or long path)
    for i in range(2, len(lines)):
        line = lines[i]
        if line.strip().startswith("<path") and 'fill="#3076DF"' in line and ' d="' in line:
            if len(line) > 200:  # real path content, not a tiny artifact
                favicon_lines.append(line)
        if line.strip() == "</svg>":
            break

    favicon_lines.append("</svg>\n")

    favicon_dest = ADMIN_UI_PUBLIC / "favicon.svg"
    with open(favicon_dest, "w", encoding="utf-8") as f:
        f.writelines(favicon_lines)
    print(f"Wrote favicon -> {favicon_dest.relative_to(REPO_ROOT)} ({len(favicon_lines)} lines)")

    # 2b) Build logo-sidebar.svg: same as favicon but fill #d6e6ff (sidebar-fg) and 40x40
    sidebar_lines = [
        favicon_lines[0],
        favicon_lines[1].replace('width="32" height="32"', 'width="40" height="40"'),
    ]
    for line in favicon_lines[2:-1]:
        sidebar_lines.append(line.replace('fill="#3076DF"', 'fill="#d6e6ff"'))
    sidebar_lines.append("</svg>\n")
    sidebar_dest = ADMIN_UI_PUBLIC / "logo-sidebar.svg"
    with open(sidebar_dest, "w", encoding="utf-8") as f:
        f.writelines(sidebar_lines)
    print(f"Wrote logo-sidebar -> {sidebar_dest.relative_to(REPO_ROOT)}")

    # 3) Point index.html at favicon
    if INDEX_HTML.exists():
        html = INDEX_HTML.read_text(encoding="utf-8")
        if 'href="/vite.svg"' in html:
            html = html.replace('href="/vite.svg"', 'href="/favicon.svg"')
            INDEX_HTML.write_text(html, encoding="utf-8")
            print(f"Updated {INDEX_HTML.relative_to(REPO_ROOT)} -> favicon.svg")
        else:
            print(f"index.html already uses favicon or different icon link; no change.")
    else:
        print(f"Warning: {INDEX_HTML} not found; skipping index update.")

    print("Done.")


if __name__ == "__main__":
    main()
