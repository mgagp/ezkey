#!/bin/bash
# Asserts that production build output (dist/) contains no demo-mode strings.
# Run after: npm run build (production). Use in CI or locally to verify no demo leakage.
# Exit 0 if clean, 1 if any demo string is found.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="${1:-$ROOT_DIR/dist}"

if [ ! -d "$DIST_DIR" ]; then
  echo "Error: dist directory not found at $DIST_DIR. Run 'npm run build' first." >&2
  exit 1
fi

# Strings that exist only in demo-mode.ts preset data (not in locale JSON). If these appear in
# dist/*.js, demo preset tables were bundled — production should tree-shaken them when VITE_DEMO_MODE
# is forced false (see vite.config.ts). Do not use "Fill demo" / "Garage du coin" here: those
# substrings also appear in i18n files shipped in all builds.
DEMO_STRINGS=(
  'big.bird@garageducoin.ca'
  'Oscar Boulon — iPhone (Garage du coin)'
  'jean.martin@garageducoin.ca'
)

FOUND=0
for s in "${DEMO_STRINGS[@]}"; do
  if grep -rq --include='*.js' "$s" "$DIST_DIR" 2>/dev/null; then
    echo "Error: Production build contains demo-only string: $s" >&2
    FOUND=1
  fi
done

if [ "$FOUND" -eq 1 ]; then
  echo "Run 'npm run build' (production) and ensure vite.config.ts defines VITE_DEMO_MODE=false for production." >&2
  exit 1
fi

echo "OK: No demo strings found in $DIST_DIR"
