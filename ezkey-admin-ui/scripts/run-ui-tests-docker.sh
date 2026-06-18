#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ADMIN_UI_URL="${EZKEY_ADMIN_UI_URL:-http://host.docker.internal:3090}"
DEMO_DEVICE_URL="${EZKEY_DEMO_DEVICE_URL:-http://host.docker.internal:8083}"
RESULTS_DIR="${EZKEY_BROWSER_TEST_RESULTS_DIR:-$ROOT_DIR/test-results/browser}"
REPORT_DIR="${EZKEY_BROWSER_TEST_REPORT_DIR:-$ROOT_DIR/playwright-report}"
IMAGE_NAME="ezkey-admin-ui-browser-tests"
SKIP_UI_START=0
INNER_ARGS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --skip-ui-start)
      SKIP_UI_START=1
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
    --report-dir)
      REPORT_DIR="$2"
      shift 2
      ;;
    *)
      INNER_ARGS+=("$1")
      shift
      ;;
  esac
done

mkdir -p "$RESULTS_DIR" "$REPORT_DIR"

if [ "$SKIP_UI_START" -eq 0 ]; then
  "$ROOT_DIR/start.sh" -d
fi

docker build -t "$IMAGE_NAME" -f "$ROOT_DIR/docker/ui-browser-tests.Dockerfile" "$ROOT_DIR"

docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -e EZKEY_ADMIN_UI_URL="$ADMIN_UI_URL" \
  -e EZKEY_DEMO_DEVICE_URL="$DEMO_DEVICE_URL" \
  -e EZKEY_BROWSER_TEST_RESULTS_DIR=/work/test-results/browser \
  -e PLAYWRIGHT_SKIP_WEBSERVER=1 \
  -v "$RESULTS_DIR:/work/test-results/browser" \
  -v "$REPORT_DIR:/work/playwright-report" \
  "$IMAGE_NAME" \
  ./scripts/run-ui-tests.sh --skip-webserver "${INNER_ARGS[@]}"
