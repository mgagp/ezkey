#!/usr/bin/env bash
# Refresh licenses/android-libraries.json (AboutLibraries export of the Android release
# runtime classpath) for the in-app Open Source Licenses screen (#1109).
#
# Usage (from ezkey_mobile/):
#   ./scripts/export-android-licenses.sh
#   yarn license:android
#
# Then run `yarn license:app-data` and commit both JSON files.
set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# shellcheck source=resolve-android-jdk.sh
source "${MOBILE_ROOT}/scripts/resolve-android-jdk.sh"

cd "${MOBILE_ROOT}/android"
./gradlew :app:exportLibraryDefinitions --console=plain
echo "Updated ${MOBILE_ROOT}/licenses/android-libraries.json"
