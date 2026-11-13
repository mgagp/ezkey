#!/usr/bin/env bash
# Produce a release AAB and fail if the 16 KB page-size gate fails.
# Usage: cd ezkey_mobile && ./scripts/bundle-release.sh
# Equivalent yarn: yarn android:bundle:release
set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AAB="${MOBILE_ROOT}/android/app/build/outputs/bundle/release/app-release.aab"

# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"

echo "Using JAVA_HOME=${JAVA_HOME}"
"$JAVA_HOME/bin/java" -version

# shellcheck source=assert-release-production-clean-env.sh
source "${MOBILE_ROOT}/scripts/assert-release-production-clean-env.sh"

cd "${MOBILE_ROOT}/android"
./gradlew bundleRelease --no-daemon

if [[ ! -f "$AAB" ]]; then
  echo "Release AAB not found at ${AAB}" >&2
  exit 1
fi

echo "==> Running 16 KB alignment gate on ${AAB}"
"${MOBILE_ROOT}/scripts/check-16kb-alignment.sh" "$AAB"
echo "Release AAB ready: ${AAB}"
