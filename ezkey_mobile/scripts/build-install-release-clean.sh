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
rm -rf "${MOBILE_ROOT}/android/app/build" "${MOBILE_ROOT}/android/build"
(cd "${MOBILE_ROOT}/android" && ./gradlew clean assembleRelease --no-daemon)

if [[ ! -f "$APK" ]]; then
  echo "Release APK not found at ${APK}" >&2
  exit 1
fi

echo "Installing release APK: ${APK}"
adb install -r "$APK"

echo "Launching ${PKG}..."
adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
echo "Done. Release build installed (offline-capable, no Metro required)."
