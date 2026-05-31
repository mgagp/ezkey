#!/usr/bin/env bash
# TEMP (#177): Capture EZKEY_DIAG_TEMP + React Native lines during device functional tests.
# Usage: ./scripts/capture-stack-migration-logcat.sh [output.txt]
set -euo pipefail
MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-${MOBILE_ROOT}/maestro/reports/stack-migration-logcat-$(date -u +%Y%m%dT%H%M%SZ).txt}"
mkdir -p "$(dirname "$OUT")"
echo "Writing logcat slice to ${OUT}"
adb logcat -c
echo "--- logcat capture started $(date -u -Iseconds) ---" >"${OUT}"
adb logcat -v time ReactNativeJS:I ReactNative:I EZKEY_DIAG_TEMP:I EzkeyQrPlugin:W *:S >>"${OUT}" &
PID=$!
echo "Capturing (PID ${PID}). Press Ctrl+C to stop."
trap 'kill "${PID}" 2>/dev/null || true' INT TERM
wait "${PID}" || true
