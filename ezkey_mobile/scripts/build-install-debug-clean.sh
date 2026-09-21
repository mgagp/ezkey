#!/usr/bin/env bash
# Canonical clean debug build + install on a connected device (Git Bash on Windows).
# Usage (from repo):  cd ezkey_mobile && ./scripts/build-install-debug-clean.sh
# Options:
#   --skip-clean     Reuse existing APK; only uninstall + adb install (fast).
#   --no-uninstall   Skip adb uninstall (same signature reinstall).
set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG=org.ezkey.mobile
APK="${MOBILE_ROOT}/android/app/build/outputs/apk/debug/app-debug.apk"
SKIP_CLEAN=false
NO_UNINSTALL=false

for arg in "$@"; do
  case "$arg" in
    --skip-clean) SKIP_CLEAN=true ;;
    --no-uninstall) NO_UNINSTALL=true ;;
    -h|--help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"

echo "Using JAVA_HOME=${JAVA_HOME}"
"$JAVA_HOME/bin/java" -version

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device. Connect the phone (USB or wireless debugging) before starting a long Gradle build." >&2
  echo "  adb devices -l" >&2
  exit 1
fi

adb devices -l

cd "${MOBILE_ROOT}"

"${MOBILE_ROOT}/scripts/preflight-android-path-length.sh"

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
      cat <<EOF

Detected a Windows native path-length failure (MAX_PATH / CMake object path).

Recommended remediation:
  1. Use a shorter workspace root on the same drive, then run this script again.
     Example target root: C:\\w\\ezkey-worktree2
  2. Reinstall dependencies from that short-root workspace.
  3. Re-run: ./scripts/build-install-debug-clean.sh

This is an environment/path constraint, not an app logic regression.
EOF
    fi
    rm -f "$GRADLE_LOG"
    exit 1
  fi
  rm -f "$GRADLE_LOG"
fi

echo "Launching ${PKG}..."
adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
echo "Done."
