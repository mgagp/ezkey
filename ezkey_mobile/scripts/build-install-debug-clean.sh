#!/usr/bin/env bash
# Canonical clean debug build + install on a connected device (Git Bash on Windows).
# Usage (from repo):  cd ezkey_mobile && ./scripts/build-install-debug-clean.sh
# Options:
#   --skip-clean       Reuse existing APK; only uninstall + adb install (fast).
#   --no-uninstall     Skip adb uninstall (same signature reinstall).
#   --build-only       Assemble debug APK only (no adb device required).
#   --metro-port PORT  Metro TCP port for adb reverse / stale-Metro check (default 8081).
#                      Also reads EZKEY_METRO_PORT or RCT_METRO_PORT.
#   --with-metro-reverse
#                      Always re-apply adb reverse for the Metro port (also auto-on when
#                      ezkey.useMetroInDebug=true in android/gradle.properties).
set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG=org.ezkey.mobile
APK="${MOBILE_ROOT}/android/app/build/outputs/apk/debug/app-debug.apk"
SKIP_CLEAN=false
NO_UNINSTALL=false
BUILD_ONLY=false
WITH_METRO_REVERSE=false
METRO_PORT_ARG=""

# shellcheck source=lib/metro-adb.sh
source "${MOBILE_ROOT}/scripts/lib/metro-adb.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-clean)
      SKIP_CLEAN=true
      shift
      ;;
    --no-uninstall)
      NO_UNINSTALL=true
      shift
      ;;
    --build-only)
      BUILD_ONLY=true
      shift
      ;;
    --with-metro-reverse)
      WITH_METRO_REVERSE=true
      shift
      ;;
    --metro-port)
      if [[ $# -lt 2 ]]; then
        echo "Option --metro-port requires a value (e.g. --metro-port 8082)." >&2
        exit 2
      fi
      METRO_PORT_ARG="$2"
      shift 2
      ;;
    --metro-port=*)
      METRO_PORT_ARG="${1#--metro-port=}"
      shift
      ;;
    -h|--help)
      cat <<'EOF'
Canonical clean debug build + install (Git Bash on Windows).

Usage: ./scripts/build-install-debug-clean.sh [options]

  --skip-clean           Reuse existing APK; uninstall + adb install only
  --no-uninstall         Skip adb uninstall
  --build-only           assembleDebug only (no device required)
  --metro-port PORT      Metro TCP port (default 8081; EZKEY_METRO_PORT / RCT_METRO_PORT)
  --with-metro-reverse   Re-apply adb reverse every run (auto when ezkey.useMetroInDebug=true)
  -h, --help             Show this help
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

METRO_PORT="$(ezkey_metro_port "${METRO_PORT_ARG}")"

if ezkey_metro_debug_enabled "${MOBILE_ROOT}/android/gradle.properties"; then
  WITH_METRO_REVERSE=true
fi

# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"

echo "Using JAVA_HOME=${JAVA_HOME}"
"$JAVA_HOME/bin/java" -version

cd "${MOBILE_ROOT}"

"${MOBILE_ROOT}/scripts/preflight-android-path-length.sh"

_print_path_length_remediation() {
  local mode_hint="$1"
  cat <<EOF

Detected a Windows native path-length failure (MAX_PATH / CMake object path).

Recommended remediation:
  1. Use a shorter workspace root on the same drive, then run this script again.
     Example target root: C:\\w\\p  (keep the Windows path to ezkey_mobile/ ≤ 40 chars)
  2. Reinstall dependencies from that short-root workspace.
  3. Re-run: ./scripts/build-install-debug-clean.sh${mode_hint}

This is an environment/path constraint, not an app logic regression.
EOF
}

if [[ "$BUILD_ONLY" == true ]]; then
  echo "Build-only mode: assembling debug APK (no device / install / launch)."
  if [[ "$SKIP_CLEAN" == true && -f "$APK" ]]; then
    echo "Existing APK present (--skip-clean): ${APK}"
    echo "Done (build-only, skipped Gradle)."
    exit 0
  fi
  echo "Clean Gradle assembleDebug..."
  rm -rf "${MOBILE_ROOT}/android/app/build" "${MOBILE_ROOT}/android/build"
  GRADLE_LOG="$(mktemp)"
  if ! (cd "${MOBILE_ROOT}/android" && ./gradlew clean assembleDebug --no-daemon 2>&1 | tee "$GRADLE_LOG"); then
    if grep -qiE "Filename longer than 260 characters|CMAKE_OBJECT_PATH_MAX" "$GRADLE_LOG"; then
      _print_path_length_remediation " --build-only"
    fi
    rm -f "$GRADLE_LOG"
    exit 1
  fi
  rm -f "$GRADLE_LOG"
  echo "APK: ${APK}"
  echo "Done (build-only)."
  exit 0
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device. Connect the phone (USB or wireless debugging) before starting a long Gradle build." >&2
  echo "  adb devices -l" >&2
  echo "  Or build without a device: ./scripts/build-install-debug-clean.sh --build-only" >&2
  exit 1
fi

adb devices -l

# Wireless ADB drops reverse mappings on reconnect — re-apply every install when Metro is in use.
if [[ "$WITH_METRO_REVERSE" == true ]]; then
  ezkey_warn_stale_metro "$METRO_PORT" "$MOBILE_ROOT"
  ezkey_adb_reverse_metro "$METRO_PORT"
else
  # Still warn if a packager is up (common confusion across worktrees) even when
  # the debug APK is standalone (useMetroInDebug=false).
  ezkey_warn_stale_metro "$METRO_PORT" "$MOBILE_ROOT" || true
fi

if [[ "$NO_UNINSTALL" != true ]]; then
  echo "Uninstalling ${PKG} (ignore failure if not installed)..."
  adb uninstall "${PKG}" 2>/dev/null || true
fi

if [[ "$SKIP_CLEAN" == true && -f "$APK" ]]; then
  echo "Installing existing APK (--skip-clean): ${APK}"
  adb install -r "$APK"
else
  echo "Clean Gradle build + installDebug..."
  rm -rf "${MOBILE_ROOT}/android/app/build" "${MOBILE_ROOT}/android/build"
  GRADLE_LOG="$(mktemp)"
  if ! (cd "${MOBILE_ROOT}/android" && ./gradlew clean installDebug --no-daemon 2>&1 | tee "$GRADLE_LOG"); then
    if grep -qiE "Filename longer than 260 characters|CMAKE_OBJECT_PATH_MAX" "$GRADLE_LOG"; then
      _print_path_length_remediation ""
    fi
    rm -f "$GRADLE_LOG"
    exit 1
  fi
  rm -f "$GRADLE_LOG"
fi

# Re-apply reverse after install as well (long Gradle windows often outlive wireless ADB).
if [[ "$WITH_METRO_REVERSE" == true ]]; then
  if adb get-state >/dev/null 2>&1; then
    ezkey_adb_reverse_metro "$METRO_PORT"
  else
    echo "Warning: adb device lost after install; re-connect and run: adb reverse tcp:${METRO_PORT} tcp:${METRO_PORT}" >&2
  fi
fi

echo "Launching ${PKG}..."
adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
echo "Done."
