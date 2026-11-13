#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ADMIN_UI_URL="${EZKEY_ADMIN_UI_URL:-http://127.0.0.1:4173}"
DEMO_DEVICE_URL="${EZKEY_DEMO_DEVICE_URL:-http://127.0.0.1:8083}"
RESULTS_DIR="${EZKEY_BROWSER_TEST_RESULTS_DIR:-$ROOT_DIR/test-results/browser}"
SKIP_WEBSERVER="${PLAYWRIGHT_SKIP_WEBSERVER:-0}"
PLAYWRIGHT_ARGS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --headed)
      export PLAYWRIGHT_HEADED=1
      shift
      ;;
    --capture-video)
      export PLAYWRIGHT_CAPTURE_VIDEO=1
      shift
      ;;
    --skip-webserver)
      SKIP_WEBSERVER=1
      shift
      ;;
    --ui-url)
      ADMIN_UI_URL="$2"
      shift 2
      ;;
    --demo-device-url)
      DEMO_DEVICE_URL="$2"
      shift 2
      ;;
    --results-dir)
      RESULTS_DIR="$2"
      shift 2
      ;;
    --grep)
      PLAYWRIGHT_ARGS+=("--grep" "$2")
      shift 2
      ;;
    *)
      PLAYWRIGHT_ARGS+=("$1")
      shift
      ;;
  esac
done

mkdir -p "$RESULTS_DIR"

export EZKEY_ADMIN_UI_URL="$ADMIN_UI_URL"
export EZKEY_DEMO_DEVICE_URL="$DEMO_DEVICE_URL"
export PLAYWRIGHT_SKIP_WEBSERVER="$SKIP_WEBSERVER"

cat > "$RESULTS_DIR/run-info.txt" <<EOF
Admin UI URL: $EZKEY_ADMIN_UI_URL
Demo Device URL: $EZKEY_DEMO_DEVICE_URL
Skip web server: $PLAYWRIGHT_SKIP_WEBSERVER
Headed mode: ${PLAYWRIGHT_HEADED:-0}
Capture video: ${PLAYWRIGHT_CAPTURE_VIDEO:-0}
EOF

cd "$ROOT_DIR"

# Playwright npm package does not download browser binaries on `npm install`.
# After a fresh clone or @playwright/test bump, Chromium must be installed once locally.
echo "Ensuring Playwright Chromium is installed..."
npm run test:browser:install

# Bash 3.2 (macOS /bin/bash) treats an empty array as unbound under `set -u`.
npm run test:browser -- ${PLAYWRIGHT_ARGS[@]+"${PLAYWRIGHT_ARGS[@]}"} 2>&1 | tee "$RESULTS_DIR/summary.txt"
