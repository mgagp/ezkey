#!/usr/bin/env bash
# Install debug APK after uninstalling a release/side-loaded build (different signature).
# Usage: from ezkey_mobile/: ./scripts/install-debug-after-uninstall.sh
set -euo pipefail
MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG=org.ezkey.mobile
cd "${MOBILE_ROOT}"

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device. Connect Pixel and retry." >&2
  exit 1
fi

# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"
echo "Using JAVA_HOME=${JAVA_HOME}"

echo "Uninstalling ${PKG} (ignore failure if not installed)..."
adb uninstall "${PKG}" 2>/dev/null || true
echo "Installing debug via Gradle..."
(cd "${MOBILE_ROOT}/android" && ./gradlew installDebug --no-daemon)
echo "Done. Launch the app manually or: adb shell monkey -p ${PKG} -c android.intent.category.LAUNCHER 1"
