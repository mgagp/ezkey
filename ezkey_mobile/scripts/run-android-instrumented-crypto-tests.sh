#!/usr/bin/env bash
# Run EzkeyCryptoModule androidTest instrumentation (MOB-006).
# Requires a connected emulator or device. Uses JDK 17/21 via resolve-android-jdk.sh — never JDK 25.
#
# Usage (from ezkey_mobile/):
#   ./scripts/run-android-instrumented-crypto-tests.sh
#
# Notes:
#   - Emulator-safe: asserts Keystore lifecycle and tier ∈ {NONE,STANDARD,STRONG}.
#   - Does NOT claim StrongBox. Physical evidence: docs/MOBILE_STRONGBOX_MANUAL_CHECKLIST.md
set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST_CLASS="org.ezkey.mobile.crypto.EzkeyCryptoModuleInstrumentedTest"

# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"

echo "Using JAVA_HOME=${JAVA_HOME}"
"$JAVA_HOME/bin/java" -version

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device/emulator. Start an emulator or connect a phone before instrumentation." >&2
  echo "  adb devices -l" >&2
  exit 1
fi

adb devices -l

cd "${MOBILE_ROOT}/android"

# Assemble first so a long compile does not race wireless adb disconnects.
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

if ! adb get-state >/dev/null 2>&1; then
  echo "adb device dropped during assemble. Reconnect USB/wireless debugging, then re-run:" >&2
  echo "  yarn android:test:instrumented:crypto" >&2
  echo "  adb devices -l" >&2
  exit 1
fi

./gradlew :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}"
