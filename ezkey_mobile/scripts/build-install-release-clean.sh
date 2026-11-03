#!/usr/bin/env bash
# Clean release build + install on a connected device (Git Bash on Windows).
# Release APK bundles JS and does not require Metro (USE_DEVELOPER_SUPPORT=false).
# Usage: cd ezkey_mobile && ./scripts/build-install-release-clean.sh
set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG=org.ezkey.mobile
APK="${MOBILE_ROOT}/android/app/build/outputs/apk/release/app-release.apk"

# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"

echo "Using JAVA_HOME=${JAVA_HOME}"
"$JAVA_HOME/bin/java" -version

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device. Connect the phone before starting a long Gradle build." >&2
  exit 1
fi

adb devices -l

cd "${MOBILE_ROOT}"

# shellcheck source=assert-release-production-clean-env.sh
source "${MOBILE_ROOT}/scripts/assert-release-production-clean-env.sh"

echo "Uninstalling ${PKG} (ignore failure if not installed)..."
adb uninstall "${PKG}" 2>/dev/null || true

echo "Clean Gradle release build..."
rm -rf "${MOBILE_ROOT}/android/app/build" "${MOBILE_ROOT}/android/build" "${MOBILE_ROOT}/android/app/.cxx"
# Autolinked native modules cache prior NDK/linker flags under node_modules/*/android/.cxx
find "${MOBILE_ROOT}/node_modules" -type d -path '*/android/.cxx' -prune -exec rm -rf {} + 2>/dev/null || true
(cd "${MOBILE_ROOT}/android" && ./gradlew clean assembleRelease --no-daemon)

if [[ ! -f "$APK" ]]; then
  echo "Release APK not found at ${APK}" >&2
  exit 1
fi

echo "==> Running 16 KB alignment gate on ${APK}"
"${MOBILE_ROOT}/scripts/check-16kb-alignment.sh" "$APK"

echo "Installing release APK: ${APK}"
adb install -r "$APK"

echo "Launching ${PKG}..."
# am start on the resolved launcher activity: `monkey` exits -5 without launching on Android 17
# (API 37) images. Falls back to monkey when the activity cannot be resolved.
launcher="$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "${PKG}" 2>/dev/null | tail -1 | tr -d '\r')"
if [[ "$launcher" == "${PKG}/"* ]]; then
  adb shell am start -n "$launcher" >/dev/null 2>&1 || true
else
  adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
fi
echo "Done. Release build installed (offline-capable, no Metro required)."
